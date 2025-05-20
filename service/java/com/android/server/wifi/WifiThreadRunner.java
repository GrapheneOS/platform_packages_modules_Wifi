/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.server.wifi.RunnerHandler.KEY_SIGNATURE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Keep;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.wifi.util.GeneralUtil.Mutable;

import java.util.function.Supplier;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Runs code on one of the Wifi service threads from another thread (For ex: incoming AIDL call from
 * a binder thread), in order to prevent race conditions.
 * Note: This is a utility class and each wifi service may have separate instances of this class on
 * their corresponding main thread for servicing incoming AIDL calls.
 */
@ThreadSafe
@Keep
public class WifiThreadRunner {
    private static final String TAG = "WifiThreadRunner";

    /** Max wait time for posting blocking runnables */
    private static final int RUN_WITH_SCISSORS_TIMEOUT_MILLIS = 4000;

    /** For test only */
    private boolean mTimeoutsAreErrors = false;
    private volatile Thread mDispatchThread = null;

    private final Handler mHandler;

    public boolean mVerboseLoggingEnabled = false;

    public WifiThreadRunner(Handler handler) {
        mHandler = handler;
    }

    public Handler getHandler() {
        return mHandler;
    }

    /**
     * Synchronously runs code on the main Wifi thread and return a value.
     * <b>Blocks</b> the calling thread until the callable completes execution on the main Wifi
     * thread.
     *
     * BEWARE OF DEADLOCKS!!!
     *
     * @param <T> the return type
     * @param supplier the lambda that should be run on the main Wifi thread
     *                 e.g. wifiThreadRunner.call(() -> mWifiApConfigStore.getApConfiguration())
     *                 or wifiThreadRunner.call(mWifiApConfigStore::getApConfiguration)
     * @param valueToReturnOnTimeout If the lambda provided could not be run within the timeout (
     *                 {@link #RUN_WITH_SCISSORS_TIMEOUT_MILLIS}), will return this provided value
     *                 instead.
     * @return value retrieved from Wifi thread, or |valueToReturnOnTimeout| if the call failed.
     *         Beware of NullPointerExceptions when expecting a primitive (e.g. int, long) return
     *         type, it may still return null and throw a NullPointerException when auto-unboxing!
     *         Recommend capturing the return value in an Integer or Long instead and explicitly
     *         handling nulls.
     */
    @Nullable
    public <T> T call(@NonNull Supplier<T> supplier, T valueToReturnOnTimeout, String taskName) {
        Mutable<T> result = new Mutable<>();
        boolean runWithScissorsSuccess = runWithScissors(mHandler,
                () -> result.value = supplier.get(),
                RUN_WITH_SCISSORS_TIMEOUT_MILLIS, false, taskName);
        if (runWithScissorsSuccess) {
            return result.value;
        } else {
            if (mVerboseLoggingEnabled) {
                Throwable callerThreadThrowable = new Throwable("Caller thread Stack trace:");
                Throwable wifiThreadThrowable = new Throwable("Wifi thread Stack trace:");
                wifiThreadThrowable.setStackTrace(mHandler.getLooper().getThread().getStackTrace());
                Log.e(TAG, "WifiThreadRunner.call() timed out!", callerThreadThrowable);
                Log.e(TAG, "WifiThreadRunner.call() timed out!", wifiThreadThrowable);
            }
            if (mTimeoutsAreErrors) {
                throw new RuntimeException("WifiThreadRunner.call() timed out!");
            }
            return valueToReturnOnTimeout;
        }
    }

    /**
     * TODO(b/342976570): remove when we are sure no more usage
     */
    public <T> T call(@NonNull Supplier<T> supplier, T valueToReturnOnTimeout) {
        return call(supplier, valueToReturnOnTimeout, null);
    }

    /**
     * Runs a Runnable on the main Wifi thread and <b>blocks</b> the calling thread until the
     * Runnable completes execution on the main Wifi thread.
     *
     * BEWARE OF DEADLOCKS!!!
     *
     * @return true if the runnable executed successfully, false otherwise
     */
    public boolean run(@NonNull Runnable runnable, String taskName) {
        boolean runWithScissorsSuccess =
                runWithScissors(mHandler, runnable, RUN_WITH_SCISSORS_TIMEOUT_MILLIS, false,
                        taskName);
        if (runWithScissorsSuccess) {
            return true;
        } else {
            Throwable callerThreadThrowable = new Throwable("Caller thread Stack trace:");
            Log.e(TAG, "WifiThreadRunner.run() timed out!", callerThreadThrowable);
            if (mTimeoutsAreErrors) {
                throw new RuntimeException("WifiThreadRunner.run() timed out!");
            }
            return false;
        }
    }

    /**
     * TODO(b/342976570): remove when we are sure no more usage
     */
    public boolean run(@NonNull Runnable runnable) {
        return run(runnable, null);
    }

    /**
     * Runs a Runnable on the main Wifi thread on the next iteration and <b>blocks</b> the calling
     * thread until the Runnable completes execution on the main Wifi thread.
     *
     * BEWARE OF DEADLOCKS!!!
     *
     * @return true if the runnable executed successfully, false otherwise
     */
    public boolean runAtFront(@NonNull Runnable runnable, String taskName) {
        boolean runWithScissorsSuccess =
                runWithScissors(mHandler, runnable, RUN_WITH_SCISSORS_TIMEOUT_MILLIS, true,
                        taskName);
        if (runWithScissorsSuccess) {
            return true;
        } else {
            Throwable callerThreadThrowable = new Throwable("Caller thread Stack trace:");
            Throwable wifiThreadThrowable = new Throwable("Wifi thread Stack trace:");
            wifiThreadThrowable.setStackTrace(mHandler.getLooper().getThread().getStackTrace());
            Log.e(TAG, "WifiThreadRunner.run() timed out!", callerThreadThrowable);
            Log.e(TAG, "WifiThreadRunner.run() timed out!", wifiThreadThrowable);
            if (mTimeoutsAreErrors) {
                throw new RuntimeException("WifiThreadRunner.run() timed out!");
            }
            return false;
        }
    }
    /**
     * Sets whether or not a RuntimeError should be thrown when a timeout occurs.
     *
     * For test purposes only!
     */
    @VisibleForTesting
    public void setTimeoutsAreErrors(boolean timeoutsAreErrors) {
        Log.d(TAG, "setTimeoutsAreErrors " + timeoutsAreErrors);
        mTimeoutsAreErrors = timeoutsAreErrors;
    }

    /**
     * Prepares to run on a different thread.
     *
     * Useful when TestLooper#startAutoDispatch is used to test code that uses #call or #run,
     * because in this case the messages are dispatched by a thread that is not actually the
     * thread associated with the looper. Should be called before each call
     * to TestLooper#startAutoDispatch, without intervening calls to other TestLooper dispatch
     * methods.
     *
     * For test purposes only!
     */
    @VisibleForTesting
    public void prepareForAutoDispatch() {
        mHandler.postAtFrontOfQueue(() -> {
            mDispatchThread = Thread.currentThread();
        });
    }

    /**
     * Asynchronously runs a Runnable on the main Wifi thread. With specified task name for metrics
     * logging
     * @return true if the runnable was successfully posted <b>(not executed)</b> to the main Wifi
     * thread, false otherwise
     * @param runnable The Runnable that will be executed.
     * @param taskName The task name for performance logging
     */
    public boolean post(@NonNull Runnable runnable, @Nullable String taskName) {
        Message m = Message.obtain(mHandler, runnable);
        m.getData().putString(KEY_SIGNATURE, taskName);
        return mHandler.sendMessage(m);
    }

    /**
     * TODO(b/342976570): remove when we are sure no more usage
     */
    public boolean post(@NonNull Runnable runnable) {
        return post(runnable, null);
    }

    /**
     * Asynchronously runs a Runnable on the main Wifi thread with delay.
     *
     * @param runnable    The Runnable that will be executed.
     * @param delayMillis The delay (in milliseconds) until the Runnable
     *                    will be executed.
     * @param taskName    The task name for performance logging
     * @param token An instance which can be used to cancel {@code runnable} via
     *         {@link #removeCallbacks(Object)}.
     * @return true if the runnable was successfully posted <b>(not executed)</b> to the main Wifi
     * thread, false otherwise
     */
    public boolean postDelayed(@NonNull Runnable runnable, long delayMillis, String taskName,
            Object token) {
        Message m = Message.obtain(mHandler, runnable);
        m.getData().putString(KEY_SIGNATURE, taskName);
        m.obj = token;
        return mHandler.sendMessageDelayed(m, delayMillis);
    }

    /**
     * Remove any pending posts of Runnable r that are in the message queue.
     *
     * @param r The Runnable that will be removed.
     */
    public final void removeCallbacks(@NonNull Runnable r) {
        mHandler.removeCallbacks(r);
    }

    /**
     * Check if there are any pending posts of messages with callback r in the message queue.
     *
     * @param r The Runnable that will be used to query.
     * @return true if exists, otherwise false.
     */
    public final boolean hasCallbacks(@NonNull Runnable r) {
        return mHandler.hasCallbacks(r);
    }


    /**
     * Remove any pending posts of callbacks whose <var>obj</var> is <var>token</var>.
     *
     * @param token The object that will be used to query.
     */
    public final void removeCallbacks(@NonNull Object token) {
        mHandler.removeCallbacksAndMessages(token);
    }

    /**
     * Package private
     * @return Scissors timeout threshold
     */
    static long getScissorsTimeoutThreshold() {
        return RUN_WITH_SCISSORS_TIMEOUT_MILLIS;
    }

    // Note: @hide methods copied from android.os.Handler
    /**
     * Runs the specified task synchronously.
     * <p>
     * If the current thread is the same as the handler thread, then the runnable
     * runs immediately without being enqueued.  Otherwise, posts the runnable
     * to the handler and waits for it to complete before returning.
     * </p><p>
     * This method is dangerous!  Improper use can result in deadlocks.
     * Never call this method while any locks are held or use it in a
     * possibly re-entrant manner.
     * </p><p>
     * This method is occasionally useful in situations where a background thread
     * must synchronously await completion of a task that must run on the
     * handler's thread.  However, this problem is often a symptom of bad design.
     * Consider improving the design (if possible) before resorting to this method.
     * </p><p>
     * One example of where you might want to use this method is when you just
     * set up a Handler thread and need to perform some initialization steps on
     * it before continuing execution.
     * </p><p>
     * If timeout occurs then this method returns <code>false</code> but the runnable
     * will remain posted on the handler and may already be in progress or
     * complete at a later time.
     * </p><p>
     * When using this method, be sure to use {@link Looper#quitSafely} when
     * quitting the looper.  Otherwise {@link #runWithScissors} may hang indefinitely.
     * (TODO: We should fix this by making MessageQueue aware of blocking runnables.)
     * </p>
     *
     * @param r       The Runnable that will be executed synchronously.
     * @param timeout The timeout in milliseconds, or 0 to wait indefinitely.
     * @param atFront Message needs to be posted at the front of the queue or not.
     * @return Returns true if the Runnable was successfully executed.
     * Returns false on failure, usually because the
     * looper processing the message queue is exiting.
     * @hide This method is prone to abuse and should probably not be in the API.
     * If we ever do make it part of the API, we might want to rename it to something
     * less funny like runUnsafe().
     */
    private boolean runWithScissors(@NonNull Handler handler, @NonNull Runnable r,
            long timeout, boolean atFront, String taskName) {
        if (r == null) {
            throw new IllegalArgumentException("runnable must not be null");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }

        if (Looper.myLooper() == handler.getLooper()) {
            r.run();
            return true;
        }

        if (Thread.currentThread() == mDispatchThread) {
            r.run();
            return true;
        }

        BlockingRunnable br = new BlockingRunnable(r);
        return br.postAndWait(handler, timeout, atFront, taskName);
    }

    private static final class BlockingRunnable implements Runnable {
        private final Runnable mTask;
        private boolean mDone;

        BlockingRunnable(Runnable task) {
            mTask = task;
        }

        @Override
        public void run() {
            try {
                mTask.run();
            } finally {
                synchronized (this) {
                    mDone = true;
                    notifyAll();
                }
            }
        }

        public boolean postAndWait(Handler handler, long timeout, boolean atFront,
                String taskName) {
            Message m = Message.obtain(handler, this);
            m.getData().putString(KEY_SIGNATURE, taskName);
            if (atFront) {
                if (!handler.sendMessageAtFrontOfQueue(m)) {
                    return false;
                }
            } else {
                if (!handler.sendMessage(m)) {
                    return false;
                }
            }

            synchronized (this) {
                if (timeout > 0) {
                    final long expirationTime = SystemClock.uptimeMillis() + timeout;
                    while (!mDone) {
                        long delay = expirationTime - SystemClock.uptimeMillis();
                        if (delay <= 0) {
                            return false; // timeout
                        }
                        try {
                            wait(delay);
                        } catch (InterruptedException ex) {
                        }
                    }
                } else {
                    while (!mDone) {
                        try {
                            wait();
                        } catch (InterruptedException ex) {
                        }
                    }
                }
            }
            return true;
        }
    }
}
