/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.wifi;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.wifi.IListListener;
import android.net.wifi.QosPolicyParams;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for QoS policy requests initiated by applications.
 */
public class ApplicationQosPolicyRequestHandler {
    private static final String TAG = "ApplicationQosPolicyRequestHandler";

    // QosPolicyParams objects contain an integer policyId in the range [1, 255],
    // while the HAL expects a byte policyId in the range [-128, 127].
    private static final int HAL_POLICY_ID_MIN = Byte.MIN_VALUE;
    private static final int HAL_POLICY_ID_MAX = Byte.MAX_VALUE;

    private final ActiveModeWarden mActiveModeWarden;
    private final WifiNative mWifiNative;
    private final Handler mHandler;
    private final ApCallback mApCallback;
    private final ApplicationQosPolicyTrackingTable mPolicyTrackingTable;

    private Map<String, List<QueuedRequest>> mPerIfaceRequestQueue;
    private Map<String, CallbackParams> mPendingCallbacks;

    private static final int REQUEST_TYPE_ADD = 0;
    private static final int REQUEST_TYPE_REMOVE = 1;
    private static final int REQUEST_TYPE_REMOVE_ALL = 2;

    @IntDef(prefix = { "REQUEST_TYPE_" }, value = {
            REQUEST_TYPE_ADD,
            REQUEST_TYPE_REMOVE,
            REQUEST_TYPE_REMOVE_ALL
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface RequestType {}

    private static class QueuedRequest {
        public final @RequestType int requestType;
        public final @Nullable List<QosPolicyParams> policiesToAdd;
        public final @NonNull ApplicationCallback callback;
        public final int requesterUid;

        public boolean processedOnAnyIface;
        public @Nullable List<Integer> initialStatusList;

        QueuedRequest(@RequestType int inRequestType,
                @Nullable List<QosPolicyParams> inPoliciesToAdd,
                @Nullable IListListener inListener, int inRequesterUid) {
            requestType = inRequestType;
            policiesToAdd = inPoliciesToAdd;
            callback = new ApplicationCallback(inListener);
            requesterUid = inRequesterUid;
            processedOnAnyIface = false;
        }
    }

    /**
     * Wrapper around the calling application's IListListener.
     * Ensures that the listener is only called once.
     */
    private static class ApplicationCallback {
        private @Nullable IListListener mListener;

        ApplicationCallback(@Nullable IListListener inListener) {
            mListener = inListener;
        }

        public void sendResult(List<Integer> statusList) {
            if (mListener == null) return;
            try {
                mListener.onResult(statusList);
            } catch (RemoteException e) {
                Log.e(TAG, "Listener received remote exception " + e);
            }

            // Set mListener to null to avoid calling again.
            // The application should only be notified once.
            mListener = null;
        }

        /**
         * Use when all policies should be assigned the same status code.
         * Ex. If all policies are rejected with the same error code.
         */
        public void sendResult(int size, @WifiManager.QosRequestStatus int statusCode) {
            List<Integer> statusList = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                statusList.add(statusCode);
            }
            sendResult(statusList);
        }
    }

    /**
     * Represents a request that has been sent to the HAL and is awaiting the AP callback.
     */
    private static class CallbackParams {
        public final @NonNull List<Byte> policyIds;

        CallbackParams(@NonNull List<Byte> inPolicyIds) {
            Collections.sort(inPolicyIds);
            policyIds = inPolicyIds;
        }

        public boolean matchesResults(List<SupplicantStaIfaceHal.QosPolicyStatus> resultList) {
            List<Byte> resultPolicyIds = new ArrayList<>();
            for (SupplicantStaIfaceHal.QosPolicyStatus status : resultList) {
                resultPolicyIds.add((byte) status.policyId);
            }
            Collections.sort(resultPolicyIds);
            return policyIds.equals(resultPolicyIds);
        }
    }

    private class ApCallback implements SupplicantStaIfaceHal.QosScsResponseCallback {
        @Override
        public void onApResponse(String ifaceName,
                List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList) {
            mHandler.post(() -> {
                logApCallbackMockable(ifaceName, halStatusList);
                CallbackParams expectedParams = mPendingCallbacks.get(ifaceName);
                if (expectedParams == null) return;

                if (!expectedParams.matchesResults(halStatusList)) {
                    // Silently ignore this callback if it does not match the expected parameters.
                    // TODO: Add a timeout to clear the pending callback if it is never received.
                    Log.i(TAG, "Callback was unsolicited. statusList: " + halStatusList);
                    return;
                }

                mPendingCallbacks.remove(ifaceName);
                processNextRequestIfPossible(ifaceName);
            });
        }
    }

    public ApplicationQosPolicyRequestHandler(@NonNull ActiveModeWarden activeModeWarden,
            @NonNull WifiNative wifiNative, @NonNull HandlerThread handlerThread) {
        mActiveModeWarden = activeModeWarden;
        mWifiNative = wifiNative;
        mHandler = new Handler(handlerThread.getLooper());
        mPerIfaceRequestQueue = new HashMap<>();
        mPendingCallbacks = new HashMap<>();
        mApCallback = new ApCallback();
        mPolicyTrackingTable = createPolicyTrackingTableMockable();
        mWifiNative.registerQosScsResponseCallback(mApCallback);
    }

    protected ApplicationQosPolicyTrackingTable createPolicyTrackingTableMockable() {
        return new ApplicationQosPolicyTrackingTable(HAL_POLICY_ID_MIN, HAL_POLICY_ID_MAX);
    }

    @VisibleForTesting
    protected void logApCallbackMockable(String ifaceName,
            List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList) {
        Log.i(TAG, "Received AP callback on " + ifaceName + ", size=" + halStatusList.size());
    }

    /**
     * Request to add a list of new QoS policies.
     *
     * @param policies List of {@link QosPolicyParams} objects representing the policies.
     * @param listener Listener to call when the operation is complete.
     * @param uid UID of the requesting application.
     */
    public void queueAddRequest(@NonNull List<QosPolicyParams> policies,
            @NonNull IListListener listener, int uid) {
        QueuedRequest request = new QueuedRequest(REQUEST_TYPE_ADD, policies, listener, uid);
        queueRequestOnAllIfaces(request);
        processNextRequestOnAllIfacesIfPossible();
    }

    private void queueRequestOnAllIfaces(QueuedRequest request) {
        List<ClientModeManager> clientModeManagers =
                mActiveModeWarden.getInternetConnectivityClientModeManagers();
        if (clientModeManagers.size() == 0) {
            // Reject request if no ClientModeManagers are available.
            request.callback.sendResult(request.policiesToAdd.size(),
                    WifiManager.QOS_REQUEST_STATUS_INSUFFICIENT_RESOURCES);
            return;
        }

        // Pre-process add requests by adding policies to the tracking table
        // and assigning virtual policy IDs.
        if (request.requestType == REQUEST_TYPE_ADD) {
            List<Integer> statusList = mPolicyTrackingTable.addPolicies(
                    request.policiesToAdd, request.requesterUid);
            List<QosPolicyParams> acceptedPolicies =
                    filterPoliciesByStatusList(request.policiesToAdd, statusList);
            if (acceptedPolicies.isEmpty()) {
                // Tracking table rejected all policies in the request. Table may be full,
                // or all policies are already being tracked.
                request.callback.sendResult(statusList);
                return;
            }
            request.initialStatusList = statusList;
        }

        for (ClientModeManager cmm : clientModeManagers) {
            String ifaceName = cmm.getInterfaceName();
            if (!mPerIfaceRequestQueue.containsKey(ifaceName)) {
                mPerIfaceRequestQueue.put(ifaceName, new ArrayList<>());
            }
            mPerIfaceRequestQueue.get(ifaceName).add(request);
        }
    }

    private void processNextRequestOnAllIfacesIfPossible() {
        for (String ifaceName : mPerIfaceRequestQueue.keySet()) {
            processNextRequestIfPossible(ifaceName);
        }
    }

    private void processNextRequestIfPossible(String ifaceName) {
        if (mPendingCallbacks.containsKey(ifaceName)) {
            // Supplicant is still processing a request on this interface.
            return;
        } else if (mPerIfaceRequestQueue.get(ifaceName).isEmpty()) {
            // No requests in this queue.
            return;
        }

        QueuedRequest request = mPerIfaceRequestQueue.get(ifaceName).get(0);
        mPerIfaceRequestQueue.get(ifaceName).remove(0);
        if (request.requestType == REQUEST_TYPE_ADD) {
            processAddRequest(ifaceName, request);
        }
        // TODO: Handle remove and removeAll requests.
    }

    /**
     * Filter out policies that do not have status code
     * {@link WifiManager#QOS_REQUEST_STATUS_TRACKING}.
     */
    private List<QosPolicyParams> filterPoliciesByStatusList(List<QosPolicyParams> policyList,
            List<Integer> statusList) {
        List<QosPolicyParams> filteredPolicies = new ArrayList<>();
        for (int i = 0; i < statusList.size(); i++) {
            if (statusList.get(i) == WifiManager.QOS_REQUEST_STATUS_TRACKING) {
                filteredPolicies.add(policyList.get(i));
            }
        }
        return filteredPolicies;
    }

    private void processAddRequest(String ifaceName, QueuedRequest request) {
        boolean previouslyProcessed = request.processedOnAnyIface;
        request.processedOnAnyIface = true;

        // Filter out policies that were already in the table during pre-processing.
        List<Integer> statusList = new ArrayList(request.initialStatusList);
        List<QosPolicyParams> policyList = filterPoliciesByStatusList(
                request.policiesToAdd, request.initialStatusList);

        // Filter out policies that were removed from the table in processSynchronousHalResponse().
        policyList = mPolicyTrackingTable.filterUntrackedPolicies(policyList, request.requesterUid);

        List<SupplicantStaIfaceHal.QosPolicyStatus> halStatusList =
                mWifiNative.addQosPolicyRequestForScs(ifaceName, policyList);
        if (halStatusList == null) {
            if (!previouslyProcessed) {
                statusList = handleHalPolicyAddError(
                        statusList, request.policiesToAdd, request.requesterUid);
                request.callback.sendResult(statusList);
            }
            processNextRequestIfPossible(ifaceName);
            return;
        }

        // Policies that were sent to the AP expect a response from the callback.
        List<Byte> policiesAwaitingCallback = new ArrayList<>();
        for (SupplicantStaIfaceHal.QosPolicyStatus status : halStatusList) {
            if (status.statusCode == SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_SENT) {
                policiesAwaitingCallback.add((byte) status.policyId);
            }
        }

        // Send the status list to the requesting application.
        // Should only be done the first time that a request is processed.
        if (!previouslyProcessed) {
            statusList = processSynchronousHalResponse(
                    statusList, halStatusList, request.policiesToAdd, request.requesterUid);
            request.callback.sendResult(statusList);
        }

        if (policiesAwaitingCallback.isEmpty()) {
            processNextRequestIfPossible(ifaceName);
        } else {
            mPendingCallbacks.put(ifaceName, new CallbackParams(policiesAwaitingCallback));
        }
    }

    private static @WifiManager.QosRequestStatus int halToWifiManagerSyncStatus(
            @SupplicantStaIfaceHal.QosPolicyScsRequestStatusCode int halStatus) {
        switch (halStatus) {
            case SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_SENT:
                return WifiManager.QOS_REQUEST_STATUS_TRACKING;
            case SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_ALREADY_ACTIVE:
                return WifiManager.QOS_REQUEST_STATUS_ALREADY_ACTIVE;
            case SupplicantStaIfaceHal.QOS_POLICY_SCS_REQUEST_STATUS_INVALID:
                return WifiManager.QOS_REQUEST_STATUS_INVALID_PARAMETERS;
            default:
                return WifiManager.QOS_REQUEST_STATUS_FAILURE_UNKNOWN;
        }
    }

    /**
     * Handle the case where {@link WifiNative#addQosPolicyRequestForScs(String, List)} fails.
     *
     * For any policy that was sent to the HAL, assign the proper error code and
     * remove that policy from the tracking table.
     */
    private List<Integer> handleHalPolicyAddError(List<Integer> statusList,
            List<QosPolicyParams> policyList, int uid) {
        List<Integer> rejectedPolicies = new ArrayList<>();
        for (int i = 0; i < statusList.size(); i++) {
            if (statusList.get(i) != WifiManager.QOS_REQUEST_STATUS_TRACKING) {
                // Policy was assigned an error code by the tracking table
                // and was not sent to the HAL.
                continue;
            }
            statusList.set(i, WifiManager.QOS_REQUEST_STATUS_FAILURE_UNKNOWN);
            rejectedPolicies.add(policyList.get(i).getPolicyId());
        }

        // Remove policies that were sent to the HAL from the tracking table.
        mPolicyTrackingTable.removePolicies(rejectedPolicies, uid);
        return statusList;
    }

    /**
     * Process the status list from {@link WifiNative#addQosPolicyRequestForScs(String, List)}.
     *
     * For each policy that was sent to the HAL, merge the HAL status into the main status list.
     * If any policies were rejected by the HAL, remove them from the policy tracking table.
     */
    @VisibleForTesting
    protected List<Integer> processSynchronousHalResponse(List<Integer> statusList,
            List<SupplicantStaIfaceHal.QosPolicyStatus> halResults,
            List<QosPolicyParams> policyList, int uid) {
        int halIndex = 0;
        List<Integer> rejectedPolicies = new ArrayList<>();
        for (int i = 0; i < statusList.size(); i++) {
            if (statusList.get(i) != WifiManager.QOS_REQUEST_STATUS_TRACKING) {
                // Policy was assigned an error code by the tracking table
                // and was not sent to the HAL.
                continue;
            }
            int statusCode = halToWifiManagerSyncStatus(halResults.get(halIndex).statusCode);
            if (statusCode != WifiManager.QOS_REQUEST_STATUS_TRACKING) {
                rejectedPolicies.add(policyList.get(i).getPolicyId());
            }
            statusList.set(i, statusCode);
            halIndex++;
        }

        if (!rejectedPolicies.isEmpty()) {
            // Remove policies rejected by the HAL from the tracking table.
            mPolicyTrackingTable.removePolicies(rejectedPolicies, uid);
        }
        return statusList;
    }
}
