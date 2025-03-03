#  Copyright (C) 2024 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

from mobly.controllers import android_device


def set_screen_on_and_unlock(ad: android_device.AndroidDevice):
    """Sets the screen to stay on and unlocks the device.

    Args:
        ad: AndroidDevice instance.
    """
    ad.adb.shell("svc power stayon true")
    ad.adb.shell("input keyevent KEYCODE_WAKEUP")
    ad.adb.shell("wm dismiss-keyguard")


def enable_wifi_verbose_logging(ad: android_device.AndroidDevice):
    """Sets the Wi-Fi verbose logging developer option to Enable."""
    ad.adb.shell('cmd wifi set-verbose-logging enabled')
