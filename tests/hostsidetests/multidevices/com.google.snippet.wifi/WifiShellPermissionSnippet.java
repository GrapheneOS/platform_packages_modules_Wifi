/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.snippet.wifi;

import android.app.UiAutomation;
import android.os.RemoteException;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.Callable;

/**
 * Superclass to be extended by all WiFi Mobly snippets.
 *
 * <p>This class collects functionality commonly used in snippets.
 */
public class WifiShellPermissionSnippet {
    public WifiShellPermissionSnippet() { }

    /**
     * Adopts the specified shell permissions.
     *
     * @param permissions The permissions to grant (if empty all permissions will be granted).
     */
    public void adoptShellPermission(String... permissions) throws RemoteException {
        // Reuse an UiAutomation instance if there is an existing one; otherwise, get one.
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        if (permissions.length == 0) {
            uia.adoptShellPermissionIdentity();
        } else {
            uia.adoptShellPermissionIdentity(permissions);
        }
    }

    public void dropShellPermission() {
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.dropShellPermissionIdentity();
    }

    /**
     * Executes a method with a non-void return type with permission escalation.
     *
     * <p>Adopts the specified shell permissions, executes the {@link Callable} passed to it, then
     * drops the permissions and returns the invocation result.
     *
     * <p>Sample usage: {@code boolean ret = executeWithShellPermission(
     * () -> { return doSomething();});}
     *
     * @param callable the {@link Callable} to execute
     * @param permissions the permissions to grant (if empty all permissions will be granted)
     * @return the callable execution result
     */
    public <T> T executeWithShellPermission(Callable<T> callable, String... permissions) {
        try {
            adoptShellPermission(permissions);
            return callable.call();
        } catch (Exception e) {
            throw new RuntimeException("executeWithShellPermission failed", e);
        } finally {
            dropShellPermission();
        }
    }
    /**
     * Executes a method with a void return type with permission escalation.
     *
     * <p>Adopts the specified shell permissions, executes the {@link Runnable} passed to it, then
     * drops the permissions.
     *
     * <p>Sample usage: {@code executeWithShellPermission(() -> { doSomething(); });}
     *
     * @param runnable the {@link Runnable} to execute
     * @param permissions the permissions to grant (if empty all permissions will be granted)
     */
    public void executeWithShellPermission(Runnable runnable, String... permissions) {
        try {
            adoptShellPermission(permissions);
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException("executeWithShellPermission failed", e);
        } finally {
            dropShellPermission();
        }
    }
}
