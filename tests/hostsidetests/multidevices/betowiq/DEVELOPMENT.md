# Development

This document is for internal development.

## Build the wheel files

1. Run `gcert` if you have not run it already.

2. Make sure you have local python environment and have `build` module installed. This build script utilizes `python3 -m build` command to build
the test suite into wheel files.

3. In the root directory of Android Gerrit repo, run the following command:

```
bash packages/modules/Wifi/tests/hostsidetests/multidevices/betowiq/build.sh
```

4. Find the pre-built wheel files under `/tmp/betowiq`.