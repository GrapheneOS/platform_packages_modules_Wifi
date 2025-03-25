/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.WorkSource;
import android.util.Log;

import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.util.WifiPermissionsUtil;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WifiMulticastLockManager tracks holders of multicast locks and
 * triggers enabling and disabling of filtering.
 */
public class WifiMulticastLockManager {
    private static final String TAG = "WifiMulticastLockManager";
    private static final int IMPORTANCE_THRESHOLD =
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED;
    private final List<Multicaster> mMulticasters = new ArrayList<>();
    private final Map<Integer, Integer> mNumLocksPerActiveOwner = new HashMap<>();
    private final Map<Integer, Integer> mNumLocksPerInactiveOwner = new HashMap<>();
    private int mMulticastEnabled = 0;
    private int mMulticastDisabled = 0;
    private boolean mIsFilterDisableSessionActive = false;
    private long mFilterDisableSessionStartTime;
    private final Handler mHandler;
    private final Object mLock = new Object();
    private boolean mVerboseLoggingEnabled = false;
    private final BatteryStatsManager mBatteryStats;
    private final ActiveModeWarden mActiveModeWarden;
    private final Clock mClock;
    private final WifiMetrics mWifiMetrics;
    private final WifiPermissionsUtil mWifiPermissionsUtil;

    /** Delegate for handling state change events for multicast filtering. */
    public interface FilterController {
        /** Called when multicast filtering should be enabled */
        void startFilteringMulticastPackets();

        /** Called when multicast filtering should be disabled */
        void stopFilteringMulticastPackets();
    }

    public WifiMulticastLockManager(
            ActiveModeWarden activeModeWarden,
            BatteryStatsManager batteryStats,
            Looper looper,
            Context context,
            Clock clock,
            WifiMetrics wifiMetrics,
            WifiPermissionsUtil wifiPermissionsUtil) {
        mBatteryStats = batteryStats;
        mActiveModeWarden = activeModeWarden;
        mHandler = new Handler(looper);
        mClock = clock;
        mWifiMetrics = wifiMetrics;
        mWifiPermissionsUtil = wifiPermissionsUtil;

        mActiveModeWarden.registerPrimaryClientModeManagerChangedCallback(
                new PrimaryClientModeManagerChangedCallback());

        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        activityManager.addOnUidImportanceListener(new ActivityManager.OnUidImportanceListener() {
            @Override
            public void onUidImportance(final int uid, final int importance) {
                handleImportanceChanged(uid, importance);
            }
        }, IMPORTANCE_THRESHOLD);
    }

    private class Multicaster implements IBinder.DeathRecipient {
        String mTag;
        int mUid;
        IBinder mBinder;
        String mAttributionTag;
        String mPackageName;
        long mAcquireTime;

        Multicaster(int uid, IBinder binder, String tag, String attributionTag,
                String packageName) {
            mTag = tag;
            mUid = uid;
            mBinder = binder;
            mAttributionTag = attributionTag;
            mPackageName = packageName;
            mAcquireTime = mClock.getElapsedSinceBootMillis();
            try {
                mBinder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                binderDied();
            }
        }

        @Override
        public void binderDied() {
            mHandler.post(() -> {
                Log.e(TAG, "Multicaster binderDied");
                synchronized (mLock) {
                    int i = mMulticasters.indexOf(this);
                    if (i != -1) {
                        removeMulticasterLocked(i, mUid, mTag);
                    }
                }
            });
        }

        void unlinkDeathRecipient() {
            mBinder.unlinkToDeath(this, 0);
        }

        public int getUid() {
            return mUid;
        }

        public String getTag() {
            return mTag;
        }

        public IBinder getBinder() {
            return mBinder;
        }

        public String getAttributionTag() {
            return mAttributionTag;
        }

        public String getPackageName() {
            return mPackageName;
        }

        public long getAcquireTime() {
            return mAcquireTime;
        }

        public String toString() {
            return "Multicaster{" + mTag + " uid=" + mUid  + "}";
        }
    }

    private boolean uidIsLockOwner(int uid) {
        return mNumLocksPerActiveOwner.containsKey(uid)
                || mNumLocksPerInactiveOwner.containsKey(uid);
    }

    private void transitionUidToActive(int uid) {
        if (mNumLocksPerInactiveOwner.containsKey(uid)) {
            mNumLocksPerActiveOwner.put(uid, mNumLocksPerInactiveOwner.get(uid));
            mNumLocksPerInactiveOwner.remove(uid);
        }
    }

    private void transitionUidToInactive(int uid) {
        if (mNumLocksPerActiveOwner.containsKey(uid)) {
            mNumLocksPerInactiveOwner.put(uid, mNumLocksPerActiveOwner.get(uid));
            mNumLocksPerActiveOwner.remove(uid);
        }
    }

    private void handleImportanceChanged(int uid, int importance) {
        mHandler.post(() -> {
            synchronized (mLock) {
                if (!uidIsLockOwner(uid)) {
                    return;
                }

                boolean uidIsNowActive = importance < IMPORTANCE_THRESHOLD;
                boolean prevIsMulticastEnabled = isMulticastEnabled();
                Log.i(TAG, "Handling importance changed for uid=" + uid
                        + ", isNowActive=" + uidIsNowActive + ", importance=" + importance);
                if (uidIsNowActive) {
                    transitionUidToActive(uid);
                } else {
                    transitionUidToInactive(uid);
                }

                boolean currentIsMulticastEnabled = isMulticastEnabled();
                if (prevIsMulticastEnabled != currentIsMulticastEnabled) {
                    if (currentIsMulticastEnabled) {
                        // Filtering should be stopped if multicast is enabled
                        stopFilteringMulticastPackets();
                    } else {
                        startFilteringMulticastPackets();
                    }
                }
            }
        });
    }

    protected void dump(PrintWriter pw) {
        pw.println("mMulticastEnabled " + mMulticastEnabled);
        pw.println("mMulticastDisabled " + mMulticastDisabled);
        synchronized (mLock) {
            pw.println("Active lock owners: " + mNumLocksPerActiveOwner);
            pw.println("Inactive lock owners: " + mNumLocksPerInactiveOwner);
            pw.println("Multicast Locks held:");
            for (Multicaster l : mMulticasters) {
                pw.print("    ");
                pw.println(l);
            }
        }
    }

    protected void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    /** Start filtering multicast packets if no locks are actively held */
    public void startFilteringMulticastPackets() {
        synchronized (mLock) {
            if (!isMulticastEnabled()) {
                if (mIsFilterDisableSessionActive) {
                    // Log the end of the filtering disabled session,
                    // since we're about to re-enable multicast packet filtering
                    mWifiMetrics.addMulticastLockManagerActiveSession(
                            mClock.getElapsedSinceBootMillis() - mFilterDisableSessionStartTime);
                }
                mActiveModeWarden.getPrimaryClientModeManager()
                        .getMcastLockManagerFilterController()
                        .startFilteringMulticastPackets();
                mIsFilterDisableSessionActive = false;
            }
        }
    }

    private void stopFilteringMulticastPackets() {
        synchronized (mLock) {
            if (!mIsFilterDisableSessionActive) {
                // Mark the beginning of a filtering disabled session,
                // since we're about to disable multicast packet filtering
                mFilterDisableSessionStartTime = mClock.getElapsedSinceBootMillis();
            }
            mActiveModeWarden.getPrimaryClientModeManager()
                    .getMcastLockManagerFilterController()
                    .stopFilteringMulticastPackets();
            mIsFilterDisableSessionActive = true;
        }
    }

    /**
     * Acquire a multicast lock.
     * @param uid uid of the calling application
     * @param binder a binder used to ensure caller is still alive
     * @param lockTag caller-provided tag to identify this lock
     * @param attributionTag attribution tag of the calling application
     * @param packageName package name of the calling application
     */
    public void acquireLock(int uid, IBinder binder, String lockTag, String attributionTag,
            String packageName) {
        synchronized (mLock) {
            mMulticastEnabled++;

            // Assume that the application is active if it is requesting a lock
            if (mNumLocksPerInactiveOwner.containsKey(uid)) {
                transitionUidToActive(uid);
            }
            int numLocksHeldByUid = mNumLocksPerActiveOwner.getOrDefault(uid, 0);
            mNumLocksPerActiveOwner.put(uid, numLocksHeldByUid + 1);
            mMulticasters.add(new Multicaster(uid, binder, lockTag, attributionTag, packageName));

            // Note that we could call stopFilteringMulticastPackets only when
            // our new size == 1 (first call), but this function won't
            // be called often and by making the stopPacket call each
            // time we're less fragile and self-healing.
            stopFilteringMulticastPackets();
        }

        final long ident = Binder.clearCallingIdentity();
        mBatteryStats.reportWifiMulticastEnabled(new WorkSource(uid));
        WifiStatsLog.write_non_chained(
                WifiStatsLog.WIFI_MULTICAST_LOCK_STATE_CHANGED, uid, null,
                WifiStatsLog.WIFI_MULTICAST_LOCK_STATE_CHANGED__STATE__ON, lockTag);
        Binder.restoreCallingIdentity(ident);
    }

    /** Releases a multicast lock */
    public void releaseLock(int uid, IBinder binder, String lockTag) {
        synchronized (mLock) {
            mMulticastDisabled++;
            int size = mMulticasters.size();
            for (int i = size - 1; i >= 0; i--) {
                Multicaster m = mMulticasters.get(i);
                if ((m != null) && (m.getUid() == uid) && (m.getTag().equals(lockTag))
                        && (m.getBinder() == binder)) {
                    removeMulticasterLocked(i, uid, lockTag);
                    break;
                }
            }
        }
    }

    private void decrementNumLocksForUid(int uid, Map<Integer, Integer> map) {
        int numLocksHeldByUid = map.get(uid) - 1;
        if (numLocksHeldByUid == 0) {
            map.remove(uid);
        } else {
            map.put(uid, numLocksHeldByUid);
        }
    }

    private void removeMulticasterLocked(int i, int uid, String tag) {
        Multicaster removed = mMulticasters.remove(i);
        if (removed != null) {
            removed.unlinkDeathRecipient();
            mWifiMetrics.addMulticastLockManagerAcqSession(
                    uid, removed.getAttributionTag(),
                    mWifiPermissionsUtil.getWifiCallerType(uid, removed.getPackageName()),
                    mClock.getElapsedSinceBootMillis() - removed.getAcquireTime());
        }

        if (mNumLocksPerActiveOwner.containsKey(uid)) {
            decrementNumLocksForUid(uid, mNumLocksPerActiveOwner);
        } else if (mNumLocksPerInactiveOwner.containsKey(uid)) {
            decrementNumLocksForUid(uid, mNumLocksPerInactiveOwner);
        }

        if (!isMulticastEnabled()) {
            startFilteringMulticastPackets();
        }

        final long ident = Binder.clearCallingIdentity();
        mBatteryStats.reportWifiMulticastDisabled(new WorkSource(uid));
        WifiStatsLog.write_non_chained(
                WifiStatsLog.WIFI_MULTICAST_LOCK_STATE_CHANGED, uid, null,
                WifiStatsLog.WIFI_MULTICAST_LOCK_STATE_CHANGED__STATE__OFF, tag);
        Binder.restoreCallingIdentity(ident);
    }

    /** Returns whether multicast should be allowed (filtering disabled). */
    public boolean isMulticastEnabled() {
        synchronized (mLock) {
            // Multicast is enabled if any active lock owners exist
            return !mNumLocksPerActiveOwner.isEmpty();
        }
    }

    private class PrimaryClientModeManagerChangedCallback
            implements ActiveModeWarden.PrimaryClientModeManagerChangedCallback {

        @Override
        public void onChange(
                @Nullable ConcreteClientModeManager prevPrimaryClientModeManager,
                @Nullable ConcreteClientModeManager newPrimaryClientModeManager) {
            if (prevPrimaryClientModeManager != null) {
                // no longer primary => start filtering out multicast packets
                prevPrimaryClientModeManager.getMcastLockManagerFilterController()
                        .startFilteringMulticastPackets();
            }
            if (newPrimaryClientModeManager != null
                    && isMulticastEnabled()) { // this call is synchronized
                // new primary and multicast enabled => stop filtering out multicast packets
                newPrimaryClientModeManager.getMcastLockManagerFilterController()
                        .stopFilteringMulticastPackets();
            }
        }
    }
}
