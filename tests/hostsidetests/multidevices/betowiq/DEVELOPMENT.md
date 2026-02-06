# Development

This document is for internal development.

## How to build the wheel files

1.  Load build configurations in the Android repo:

```
source build/envsetup.sh
lunch <target>
```

1.  Build the `wifi_mobly_snippet` snippet apk and copy it to
    `betowiq/betowiq/snippets`. Enter the root directory and run the following
    command:

```
make wifi_mobly_snippet
cp $OUT/testcases/wifi_mobly_snippet/arm64/wifi_mobly_snippet.apk packages/modules/Wifi/tests/hostsidetests/multidevices/betowiq/betowiq/snippets/
```

1.  Build the `wifi_aware_snippet_new` snippet apk and copy it to
    `betowiq/betowiq/snippets`. Enter the root directory and run the following
    command:

```
make wifi_aware_snippet_new
cp $OUT/testcases/wifi_aware_snippet_new/arm64/wifi_aware_snippet_new.apk packages/modules/Wifi/tests/hostsidetests/multidevices/betowiq/betowiq/snippets/
```

1.  Enter `betowiq` project root directory and run the following command, then
    you can find the `.whl` file under the `dist` directory:

```
python3 -m build
```
