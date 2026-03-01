set -ex

rm -rf /tmp/betowiq || true
mkdir -p /tmp/betowiq/arm64
mkdir -p /tmp/betowiq/x86_64

# This is a python PIP env setup command on gLinux.
gpkg setup

function build_snippets_and_wheel() {
    local arch=$1

    # Build the snippets.
    mkdir -p betowiq/snippets || true
    make wifi_mobly_snippet
    cp $OUT/testcases/wifi_mobly_snippet/${arch}/wifi_mobly_snippet.apk packages/modules/Wifi/tests/hostsidetests/multidevices/betowiq/betowiq/snippets/
    make wifi_aware_snippet_new
    cp $OUT/testcases/wifi_aware_snippet_new/${arch}/wifi_aware_snippet_new.apk packages/modules/Wifi/tests/hostsidetests/multidevices/betowiq/betowiq/snippets/

    # Build the wheel.
    # Use a hardcoded path here since I failed to make cogd command work
    # inside a shell script
    cd packages/modules/Wifi/tests/hostsidetests/multidevices/betowiq
    rm -rf dist || true
    rm -rf betowiq.egg-info || true
    python3 -m build
    cp dist/betowiq-*-py3-none-any.whl /tmp/betowiq/${arch}/

    # Clean up.
    rm -rf betowiq/snippets
    rm -rf dist || true
    rm -rf betowiq.egg-info || true
    cd ../../../../../../../
}

source build/envsetup.sh
lunch caiman-next-userdebug
build_snippets_and_wheel arm64

source build/envsetup.sh
lunch cf_x86_64_phone-next-userdebug
build_snippets_and_wheel x86_64
