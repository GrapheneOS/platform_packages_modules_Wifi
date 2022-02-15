/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.os.Handler;
import android.provider.Browser;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.server.wifi.util.CertificateSubjectInfo;
import com.android.server.wifi.util.NativeUtil;
import com.android.wifi.resources.R;

import java.security.cert.X509Certificate;

/** This class is used to handle insecure EAP networks. */
public class InsecureEapNetworkHandler {
    private static final String TAG = "InsecureEapNetworkHandler";

    @VisibleForTesting
    static final String ACTION_CERT_NOTIF_TAP =
            "com.android.server.wifi.ClientModeImpl.ACTION_CERT_NOTIF_TAP";
    @VisibleForTesting
    static final String ACTION_CERT_NOTIF_ACCEPT =
            "com.android.server.wifi.ClientModeImpl.ACTION_CERT_NOTIF_ACCEPT";
    @VisibleForTesting
    static final String ACTION_CERT_NOTIF_REJECT =
            "com.android.server.wifi.ClientModeImpl.ACTION_CERT_NOTIF_REJECT";
    @VisibleForTesting
    static final String EXTRA_PENDING_CERT_SSID =
            "com.android.server.wifi.ClientModeImpl.EXTRA_PENDING_CERT_SSID";

    private final String mCaCertHelpLink;
    private final WifiContext mContext;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiNative mWifiNative;
    private final FrameworkFacade mFacade;
    private final WifiNotificationManager mNotificationManager;
    private final boolean mIsTrustOnFirstUseSupported;
    private final InsecureEapNetworkHandlerCallbacks mCallbacks;
    private final String mInterfaceName;
    private final Handler mHandler;

    @NonNull
    private WifiConfiguration mCurConfig = null;;
    @Nullable
    private X509Certificate mPendingCaCert = null;
    // This is updated on setting a pending CA cert.
    private CertificateSubjectInfo mPendingCaCertSubjectInfo = null;
    // This is updated on setting a pending CA cert.
    private CertificateSubjectInfo mPendingCaCertIssuerInfo = null;
    @Nullable
    private AlertDialog mTofuAlertDialog = null;
    private boolean mIsCertNotificationReceiverRegistered = false;

    BroadcastReceiver mCertNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String ssid = intent.getStringExtra(EXTRA_PENDING_CERT_SSID);
            // This is an onGoing notification, dismiss it once an action is sent.
            dismissDialogAndNotification();
            Log.d(TAG, "Received CertNotification: ssid=" + ssid + ", action=" + action);
            if (action.equals(ACTION_CERT_NOTIF_TAP)) {
                askForUserApprovalForCaCertificate();
            } else if (action.equals(ACTION_CERT_NOTIF_ACCEPT)) {
                handleAccept(ssid);
            } else if (action.equals(ACTION_CERT_NOTIF_REJECT)) {
                handleReject(ssid);
            }
        }
    };

    public InsecureEapNetworkHandler(@NonNull WifiContext context,
            @NonNull WifiConfigManager wifiConfigManager,
            @NonNull WifiNative wifiNative,
            @NonNull FrameworkFacade facade,
            @NonNull WifiNotificationManager notificationManager,
            boolean isTrustOnFirstUseSupported,
            @NonNull InsecureEapNetworkHandlerCallbacks callbacks,
            @NonNull String interfaceName,
            @NonNull Handler handler) {
        mContext = context;
        mWifiConfigManager = wifiConfigManager;
        mWifiNative = wifiNative;
        mFacade = facade;
        mNotificationManager = notificationManager;
        mIsTrustOnFirstUseSupported = isTrustOnFirstUseSupported;
        mCallbacks = callbacks;
        mInterfaceName = interfaceName;
        mHandler = handler;

        mCaCertHelpLink = mContext.getString(R.string.config_wifiCertInstallationHelpLink);
    }

    /**
     * Prepare data for a new connection.
     *
     * Prepare data if this is an Enterprise configuration, which
     * uses Server Cert, without a valid Root CA certificate or user approval.
     *
     * @param config the running wifi configuration.
     */
    public void prepareConnection(@NonNull WifiConfiguration config) {
        if (null == config) return;

        if (!config.isEnterprise()) return;
        WifiEnterpriseConfig entConfig = config.enterpriseConfig;
        if (!entConfig.isEapMethodServerCertUsed()) return;
        if (entConfig.hasCaCertificate()) return;

        // For TOFU supported devices, return if TOFU is not enabled.
        if (mIsTrustOnFirstUseSupported && !entConfig.isTrustOnFirstUseEnabled()) {
            return;
        }
        // For TOFU non-supported devices, return if this is approved before.
        if (!mIsTrustOnFirstUseSupported && entConfig.isUserApproveNoCaCert()) {
            return;
        }

        registerCertificateNotificationReceiver();
        mCurConfig = config;
        // Remove cached PMK in the framework and supplicant to avoid
        // skipping the EAP flow.
        clearNativeData();
        Log.d(TAG, "Remove native cached data and networks for TOFU.");
    }

    /** Clear data on disconnecting a connection. */
    public void clearConnection() {
        unregisterCertificateNotificationReceiver();
        dismissDialogAndNotification();
        clearInternalData();
    }

    /**
     * Store the received Root CA certifiate for later use.
     *
     * @param ssid the target network SSID.
     * @param cert the Root CA certificate from the server.
     * @return true if the cert is cached; otherwise, false.
     */
    public boolean setPendingCaCertificate(@NonNull String ssid, @NonNull X509Certificate cert) {
        if (TextUtils.isEmpty(ssid)) return false;
        if (null == mCurConfig) return false;
        if (!ssid.equals(mCurConfig.SSID)) return false;
        if (null == cert) return false;

        mPendingCaCertSubjectInfo = CertificateSubjectInfo.parse(
                cert.getSubjectDN().getName());
        if (null == mPendingCaCertSubjectInfo) {
            Log.e(TAG, "CA cert has no valid subject.");
            return false;
        }
        mPendingCaCertIssuerInfo = CertificateSubjectInfo.parse(
                cert.getIssuerDN().getName());
        if (null == mPendingCaCertIssuerInfo) {
            Log.e(TAG, "CA cert has no valid issuer.");
            return false;
        }
        mPendingCaCert = cert;
        Log.d(TAG, "Pending Root CA certificate: " + mPendingCaCert.toString());
        return true;
    }

    /**
     * Ask for the user approval if necessary.
     *
     * For T and an EAP network without a CA certificate.
     * - if TOFU is not enabled or no pending CA cert, disconnect it.
     * - if TOFU is enabled and CA cert is pending
     *     - gate the connecitvity event here
     *     - if this request is from a user, launch a dialog to get the user approval.
     *     - if this request is from auto-connect, launch a notification.
     * For preT release, the confirmation flow is similar. Instead of installing CA
     * cert from the server, just mark this network is approved by the user.
     *
     * @param isUserSelected indicates that this connection is triggered by a user.
     * @return true if the user approval is needed; otherwise, false.
     */
    public boolean startUserApprovalIfNecessary(boolean isUserSelected) {
        if (null == mCurConfig) return false;
        if (!mCurConfig.isEnterprise()) return false;
        WifiEnterpriseConfig entConfig = mCurConfig.enterpriseConfig;
        if (!entConfig.isEapMethodServerCertUsed()) return false;
        if (entConfig.hasCaCertificate()) return false;

        // If Trust On First Use is supported, Root CA cert is mandatory
        // for an Enterprise network which needs Root CA cert.
        if (!mIsTrustOnFirstUseSupported) {
            if (mCurConfig.enterpriseConfig.isUserApproveNoCaCert()) {
                return false;
            }
        } else if (null == mPendingCaCert) {
            Log.d(TAG, "No valid CA cert for TLS-based connection.");
            handleError(mCurConfig.SSID);
            return true;
        }

        Log.d(TAG, "startUserApprovalIfNecessaryForInsecureEapNetwork: mIsUserSelected="
                + isUserSelected);

        if (isUserSelected) {
            askForUserApprovalForCaCertificate();
        } else {
            notifyUserForCaCertificate();
        }
        return true;
    }

    private void registerCertificateNotificationReceiver() {
        if (mIsCertNotificationReceiverRegistered) return;

        IntentFilter filter = new IntentFilter();
        if (mIsTrustOnFirstUseSupported) {
            filter.addAction(ACTION_CERT_NOTIF_TAP);
        }
        if (!mIsTrustOnFirstUseSupported) {
            filter.addAction(ACTION_CERT_NOTIF_ACCEPT);
            filter.addAction(ACTION_CERT_NOTIF_REJECT);
        }
        mContext.registerReceiver(mCertNotificationReceiver, filter, null, mHandler);
        mIsCertNotificationReceiverRegistered = true;
    }

    private void unregisterCertificateNotificationReceiver() {
        if (!mIsCertNotificationReceiverRegistered) return;

        mContext.unregisterReceiver(mCertNotificationReceiver);
        mIsCertNotificationReceiverRegistered = false;
    }

    @VisibleForTesting
    void handleAccept(@NonNull String ssid) {
        if (!isConnectionValid(ssid)) return;

        if (!mIsTrustOnFirstUseSupported) {
            mWifiConfigManager.setUserApproveNoCaCert(mCurConfig.networkId, true);
        } else {
            if (null == mPendingCaCert) {
                handleError(ssid);
                return;
            }
            if (!mWifiConfigManager.updateCaCertificate(mCurConfig.networkId, mPendingCaCert)) {
                // Since Root CA certificate is installed, reset these flags.
                mWifiConfigManager.setUserApproveNoCaCert(mCurConfig.networkId, false);
                mWifiConfigManager.enableTrustOnFirstUse(mCurConfig.networkId, false);
            } else {
                // The user approved this network,
                // keep the connection regardless of the result.
                Log.e(TAG, "Cannot update CA cert to network " + mCurConfig.getProfileKey());
            }
        }
        mWifiConfigManager.allowAutojoin(mCurConfig.networkId, true);
        dismissDialogAndNotification();
        clearInternalData();

        if (null != mCallbacks) mCallbacks.onAccept(ssid);
    }

    @VisibleForTesting
    void handleReject(@NonNull String ssid) {
        if (!isConnectionValid(ssid)) return;

        mWifiConfigManager.allowAutojoin(mCurConfig.networkId, false);
        dismissDialogAndNotification();
        clearInternalData();
        clearNativeData();

        if (null != mCallbacks) mCallbacks.onReject(ssid);
    }

    private void handleError(@Nullable String ssid) {
        dismissDialogAndNotification();
        clearInternalData();
        clearNativeData();

        if (null != mCallbacks) mCallbacks.onError(ssid);
    }

    private void askForUserApprovalForCaCertificate() {
        if (mCurConfig == null || TextUtils.isEmpty(mCurConfig.SSID)) return;
        if (mIsTrustOnFirstUseSupported) {
            if (null == mPendingCaCert) {
                Log.e(TAG, "Cannot launch a dialog for TOFU without "
                        + "a valid pending CA certificate.");
                return;
            }
        }
        dismissDialogAndNotification();

        String title = mIsTrustOnFirstUseSupported
                ? mContext.getString(R.string.wifi_ca_cert_dialog_title)
                : mContext.getString(R.string.wifi_ca_cert_dialog_preT_title);
        String continueBtnText = mIsTrustOnFirstUseSupported
                ? mContext.getString(R.string.wifi_ca_cert_dialog_continue_text)
                : mContext.getString(R.string.wifi_ca_cert_dialog_preT_continue_text);
        String abortBtnText = mIsTrustOnFirstUseSupported
                ? mContext.getString(R.string.wifi_ca_cert_dialog_abort_text)
                : mContext.getString(R.string.wifi_ca_cert_dialog_preT_abort_text);

        CharSequence content;
        if (mIsTrustOnFirstUseSupported) {
            String signature = NativeUtil.hexStringFromByteArray(
                    mPendingCaCert.getSignature());
            StringBuilder contentBuilder = new StringBuilder()
                    .append(mContext.getString(R.string.wifi_ca_cert_dialog_message_hint))
                    .append(mContext.getString(
                            R.string.wifi_ca_cert_dialog_message_server_name_text,
                            mPendingCaCertSubjectInfo.commonName))
                    .append(mContext.getString(
                            R.string.wifi_ca_cert_dialog_message_issuer_name_text,
                            mPendingCaCertIssuerInfo.commonName));
            if (!TextUtils.isEmpty(mPendingCaCertSubjectInfo.organization)) {
                contentBuilder.append(mContext.getString(
                        R.string.wifi_ca_cert_dialog_message_organization_text,
                        mPendingCaCertSubjectInfo.organization));
            }
            if (!TextUtils.isEmpty(mPendingCaCertSubjectInfo.email)) {
                contentBuilder.append(mContext.getString(
                        R.string.wifi_ca_cert_dialog_message_contact_text,
                        mPendingCaCertSubjectInfo.email));
            }
            contentBuilder
                    .append(mContext.getString(
                            R.string.wifi_ca_cert_dialog_message_signature_name_text,
                            signature.substring(0, 16)));
            content = contentBuilder.toString();
        } else {
            String hint = mContext.getString(
                    R.string.wifi_ca_cert_dialog_preT_message_hint, mCurConfig.SSID);
            String linkText = mContext.getString(
                    R.string.wifi_ca_cert_dialog_preT_message_link);
            // System service content is not a valid activity context, it cannot
            // launch another activity via Intent.
            // Override URLSpan onClick to add FLAG_ACTIVITY_NEW_TASK flag.
            SpannableString link = new SpannableString(linkText);
            link.setSpan(new URLSpan(mCaCertHelpLink) {
                @Override
                public void onClick(@NonNull View widget) {
                    // When a user clicks the link, the dialog is still on the top of all
                    // activities, i.e. the user cannot read the help page normally.
                    // Dismiss the dialog and launch a notifiation to let the user
                    // make a decision later.
                    if (null != mCurConfig) {
                        notifyUserForCaCertificate();
                    }

                    // Open the help page regardless a notifiation could be launched.
                    Context c = widget.getContext();
                    Intent openLinkIntent = new Intent(Intent.ACTION_VIEW)
                            .setData(Uri.parse(mCaCertHelpLink))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(Browser.EXTRA_APPLICATION_ID, c.getPackageName());
                    c.startActivity(openLinkIntent);
                }
            }, 0, linkText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            content = TextUtils.concat(hint, " ", link);
        }

        mTofuAlertDialog = mFacade.makeAlertDialogBuilder(mContext)
                .setPositiveButton(continueBtnText, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handleAccept(mCurConfig.SSID);
                    }
                })
                .setNegativeButton(abortBtnText, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handleReject(mCurConfig.SSID);
                    }
                })
                .setTitle(title)
                .setMessage(content)
                .create();

        mTofuAlertDialog.setCanceledOnTouchOutside(false);
        mTofuAlertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        mTofuAlertDialog.getWindow().addSystemFlags(
                WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS);
        mTofuAlertDialog.show();
        ((TextView) mTofuAlertDialog.findViewById(android.R.id.message))
                .setMovementMethod(LinkMovementMethod.getInstance());
    }

    private PendingIntent genCaCertNotifIntent(
            @NonNull String action, @NonNull String ssid) {
        Intent intent = new Intent(action)
                .setPackage(mContext.getServiceWifiPackageName())
                .putExtra(EXTRA_PENDING_CERT_SSID, ssid);
        return mFacade.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void notifyUserForCaCertificate() {
        if (mCurConfig == null) return;
        if (mIsTrustOnFirstUseSupported) {
            if (null == mPendingCaCert) return;
        }
        dismissDialogAndNotification();

        PendingIntent tapPendingIntent;
        if (mIsTrustOnFirstUseSupported) {
            tapPendingIntent = genCaCertNotifIntent(ACTION_CERT_NOTIF_TAP, mCurConfig.SSID);
        } else {
            Intent openLinkIntent = new Intent(Intent.ACTION_VIEW)
                    .setData(Uri.parse(mCaCertHelpLink))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            tapPendingIntent = mFacade.getActivity(mContext, 0, openLinkIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        String title = mIsTrustOnFirstUseSupported
                ? mContext.getString(R.string.wifi_ca_cert_notification_title)
                : mContext.getString(R.string.wifi_ca_cert_notification_preT_title);
        String content = mIsTrustOnFirstUseSupported
                ? mContext.getString(R.string.wifi_ca_cert_notification_message, mCurConfig.SSID)
                : mContext.getString(R.string.wifi_ca_cert_notification_preT_message,
                        mCurConfig.SSID);
        Notification.Builder builder = mFacade.makeNotificationBuilder(mContext,
                WifiService.NOTIFICATION_NETWORK_ALERTS)
                .setSmallIcon(Icon.createWithResource(mContext.getWifiOverlayApkPkgName(),
                            com.android.wifi.resources.R.drawable.stat_notify_wifi_in_range))
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(content))
                .setContentIntent(tapPendingIntent)
                .setOngoing(true)
                .setColor(mContext.getResources().getColor(
                            android.R.color.system_notification_accent_color));
        // On a device which does not support Trust On First Use,
        // a user can accept or reject this network via the notification.
        if (!mIsTrustOnFirstUseSupported) {
            Notification.Action acceptAction = new Notification.Action.Builder(
                    null /* icon */,
                    mContext.getString(R.string.wifi_ca_cert_dialog_preT_continue_text),
                    genCaCertNotifIntent(ACTION_CERT_NOTIF_ACCEPT, mCurConfig.SSID)).build();
            Notification.Action rejectAction = new Notification.Action.Builder(
                    null /* icon */,
                    mContext.getString(R.string.wifi_ca_cert_dialog_preT_abort_text),
                    genCaCertNotifIntent(ACTION_CERT_NOTIF_REJECT, mCurConfig.SSID)).build();
            builder.addAction(rejectAction).addAction(acceptAction);
        }
        mNotificationManager.notify(SystemMessage.NOTE_SERVER_CA_CERTIFICATE, builder.build());
    }

    private void dismissDialogAndNotification() {
        mNotificationManager.cancel(SystemMessage.NOTE_SERVER_CA_CERTIFICATE);
        if (mTofuAlertDialog != null) {
            mTofuAlertDialog.dismiss();
            mTofuAlertDialog = null;
        }
    }

    private void clearInternalData() {
        mPendingCaCert = null;
        mPendingCaCertSubjectInfo = null;
        mPendingCaCertIssuerInfo = null;
        mCurConfig = null;
    }

    private void clearNativeData() {
        // PMK should be cleared or it would skip EAP flow next time.
        if (null != mCurConfig) {
            mWifiNative.removeNetworkCachedData(mCurConfig.networkId);
        }
        // remove network so that supplicant's PMKSA cache is cleared
        mWifiNative.removeAllNetworks(mInterfaceName);
    }

    // There might be two possible conditions that there is no
    // valid information to handle this response:
    // 1. A new network request is fired just before getting the response.
    //    As a result, this response is invalid and should be ignored.
    // 2. There is something wrong, and it stops at an abnormal state.
    //    For this case, we should go back DisconnectedState to
    //    recover the state machine.
    // Unfortunatually, we cannot identify the condition without valid information.
    // If condition #1 occurs, and we found that the target SSID is changed,
    // it should transit to L3Connected soon normally, just ignore this message.
    // If condition #2 occurs, clear existing data and notify the client mode
    // via onError callback.
    private boolean isConnectionValid(@Nullable String ssid) {
        if (TextUtils.isEmpty(ssid) || null == mCurConfig) {
            handleError(null);
            return false;
        }

        if (!ssid.equals(mCurConfig.SSID)) {
            Log.w(TAG, "Target SSID " + mCurConfig.SSID
                    + " is different from TOFU returned SSID" + ssid);
            return false;
        }
        return true;
    }

    /** The callbacks object to notify the consumer. */
    public static class InsecureEapNetworkHandlerCallbacks {
        /**
         * When a certificate is accepted, this callback is called.
         *
         * @param ssid SSID of the network.
         */
        public void onAccept(@NonNull String ssid) {}
        /**
         * When a certificate is rejected, this callback is called.
         *
         * @param ssid SSID of the network.
         */
        public void onReject(@NonNull String ssid) {}
        /**
         * When there are no valid data to handle this insecure EAP network,
         * this callback is called.
         *
         * @param ssid SSID of the network, it might be null.
         */
        public void onError(@Nullable String ssid) {}
    }
}
