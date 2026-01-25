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

#include <android/binder_auto_utils.h>
#include <android/binder_ibinder_jni.h>
#include <android/binder_manager.h>
#include <jni.h>

namespace android {

// nativeWaitForService
extern "C" JNIEXPORT jobject JNICALL
    Java_com_android_server_wifi_mainline_1supplicant_ServiceManagerWrapper_nativeWaitForService__Ljava_lang_String_2(
        JNIEnv* env, jobject /* clazz */, jstring serviceNameJni) {
    // AServiceManager_isDeclared and AServiceManager_waitForService were added in Android 31.
    // Because this method will only be called on 35+, we can suppress the availability warning.
    #pragma clang diagnostic push
    #pragma clang diagnostic ignored "-Wunguarded-availability"
    const char* serviceName = env->GetStringUTFChars(serviceNameJni, nullptr);
    AIBinder* binder = AServiceManager_waitForService(serviceName);
    // Release the C-style string obtained from the Java string to prevent memory leaks.
    env->ReleaseStringUTFChars(serviceNameJni, serviceName);
    jobject javaBinder = AIBinder_toJavaBinder(env, binder);
    // Release the native binder reference. The Java side already took a strong reference.
    if (binder != nullptr) {
        AIBinder_decStrong(binder);
    }
    return javaBinder;
    #pragma clang diagnostic pop
}

}; // namespace android
