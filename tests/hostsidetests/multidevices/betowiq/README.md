# Better Together Wi-Fi Quality Test Suite

## What Is BeToWiQ

Better Together Wi-Fi Quality (BeToWiQ) is a new test suite built by Android to
test the Wi-Fi quality that isn't covered by the existing Android tests.

This test suite is designed to focus on user aware Wi-Fi behaviors.

## What Is Tested

TODO

## Prerequisites

*   **Environment**: We recommend an RF shielding box or room to run the test.
*   **Host machine**: A Linux desktop computer for running the test scripts.
*   **Android devices**: Two Android devices running rooted `user` images.
    *   Make sure to enable
        [developer options](https://developer.android.com/studio/debug/dev-options)
        and USB debugging on both devices.
    *   Keep the distance between the closest edges at least 20cm. If the
        devices are placed flat, ensure at least 20cm between the adjacent
        edges. Alternatively, you can place devices vertically in a back-to-back
        configuration and maintain a 20cm gap between them.
    *   Connect both devices to the host machine via USB and authorize the
        connections

### Host First-Time Setup

Make sure the desktop has installed the following required softwares:

*   [Android Debug Bridge (adb)](https://developer.android.com/tools/adb).
*   Python version 3.11 or later.

### Phone Setup

Set up each Android device so it can be used for automated testing:

*   Enable
    [developer options](https://developer.android.com/studio/debug/dev-options).
*   Turn on USB debugging.
*   Connect both Android devices to the host machine via USB and authorize the
    connections.
*   Run adb commands on the host machine and make sure your device appears in
    the output list:

    ```
    adb devices -l
    List of devices attached
      17011FDEE0002N         device usb:1-1 product:raven model:Pixel_6_Pro
      49121FDAP001NQ         device usb:2-8 product:caiman model:Pixel_9_Pro
    ```

    Remember the device serials in the command output, which are
    `17011FDEE0002N` and `49121FDAP001NQ` in this example.

## Test Steps

Follow these steps to prepare and execute tests and review test results.

### Prepare the test

Prepare the following materials to be used for the tests.

#### Get the test codes, tools, and configure build

1.  Obtain the following test files from your Google contact and save them in a
    local directory:

    *   `betowiq-x.y.z-py3-none-any.whl`, where `x.y.z` stands for the release
        version. In the following steps, we use `1.0.0` as an example of release
        version.

2.  Create a new local Python virtual environment as follows.

    ```
    python3 -m venv venv
    source venv/bin/activate
    ```

    If successful, `(venv)` is shown at the beginning of your command prompt.

3.  Install the test wheel file, substituting in the correct `.whl` file.

    ```
    python3 -m pip install betowiq-1.0.0-py3-none-any.whl
    ```

#### Configure Testbed

1.  Enable
    [developer options](https://developer.android.com/studio/debug/dev-options)
    and USB debugging on both Android devices.
2.  Keep the devices at least 20 cm apart during the test execution.
3.  Connect both Android devices to the host machine via USB and authorize the
    connections. Run adb commands on the host machine and make sure your device
    appears in the output list:

    ```
    adb devices -l
    List of devices attached
      17011FDEE0002N         device usb:1-1 product:raven model:Pixel_6_Pro
      49121FDAP001NQ         device usb:2-8 product:caiman model:Pixel_9_Pro
    ```

### Run the test

Run the following command, substituting in the two serial numbers obtained from
the `adb devices` command:

```
mobly_runner betowiq_test_suite -i -s <serial1>,<serial2>
```

Note that no space is allowed between two device serial numbers in the previous
command.

#### Run Specific Test Cases / Classes

You can run selected test cases / classes by adding --tests flag. Using the
following examples as guidelines:

*   `--tests WifiAwareDatapathTest` runs all test cases within
    `WifiAwareDatapathTest`.
*   `--tests WifiAwareDatapathTest.test_ib_unsolicited_passive_open_specific`
    runs only the `test_ib_unsolicited_passive_open_specific` test case.
*   `--tests WifiAwareDatapathTest WifiAwareDiscoveryTest ...` runs multiple
    tests in the order specified.

### View Results and Debug

NOTE: You must upload results to Google's result store if you need to discuss
test results with Google.

Uploading the test results brings following benefits:

*   Easily analyze the test results with a visualized viewer.
*   Easily share test results and debugging artifacts via a single URL link,
    without needing to upload several separate files for others to check. This
    link can be shared both with Google and with folks in your company.

Follow the
[Results uploader document](https://github.com/android/mobly-android-partner-tools?tab=readme-ov-file#results-uploader)
to upload the test results.
