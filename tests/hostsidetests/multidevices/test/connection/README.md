# Run the test through ATS local runner

This is the simplest way to verify the test.

Step 1: Build the test package:

```
m CtsWifiConnectionTests
```

Step 2: Edit the test config file.

Test config file is
`$ANDROID_HOST_OUT/testcases/CtsWifiConnectionTests/x86_64/connection/WifiConnectionTestbed.yaml`.

For running the test with static Wi-Fi networks, use the template below
and replace the `<wifi-ssid>` and `<wifi-password>` with the SSID and password
of the Wi-Fi network accessible to your android device:

```
TestBeds:
- Name: WifiConnectionTestbed
  Controllers:
    AndroidDevice: '*'
  TestParams:
    use_programmable_ap: False
    wifi_ssid: <wifi-ssid>
    wifi_password: <wifi-password>
```

For running the test with a programmable AP, use the template below and replace
the `<AP-IP>` with the IP of the programmable AP:

```
TestBeds:
- Name: WifiConnectionTestbed
  Controllers:
    AndroidDevice: '*'
    # Specify settings for the AP.
    OpenWrtDevice:
    - hostname: <AP-IP>
      skip_init_reboot: True
  TestParams:
    use_programmable_ap: True
```

Step 3: Run the test:

```
$ANDROID_BUILD_TOP/tools/deviceinfra/prebuilts/ats-local-runner \
-c=$ANDROID_HOST_OUT/testcases/CtsWifiConnectionTests/CtsWifiConnectionTests.configv2 \
-a=$ANDROID_HOST_OUT/testcases/CtsWifiConnectionTests
```

# Run the test through CTS-V-HOST

This builds all CTS-V-HOST tools and tests to verify using exactly the same tool
to run the test in CTS-V-HOST. This takes longer build time.

Build:

```
m cts-v-host
```

Edit test config
`${ANDROID_HOST_OUT}/cts-v-host/android-cts-v-host/testcases/CtsWifiConnectionTests/x86_64/connection/WifiConnectionTestbed.yaml`.
Use the same content as running through ATS local runner.

Run the test

```
# Start the cts-v-host-console
${ANDROID_HOST_OUT}/cts-v-host/android-cts-v-host/tools/cts-v-host-tradefed

# From the console prompt, run the module
cts-v-host-console > run everything -m CtsWifiConnectionTests
```

# Reference

* ATS local runner: go/ats-mobly-gerrit-test#verify-the-config-locally
* Run through CTS-V-HOST: https://source.android.com/docs/compatibility/cts/ctsv-multidevice-bt
