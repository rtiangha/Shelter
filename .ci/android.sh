#!/bin/bash -ex

export NDK_CCACHE=$(which ccache)

if [ ! -z "${DROID_KEYSTORE_B64}" ]; then
    export DROID_KEYSTORE_FILE="${GITHUB_WORKSPACE}/ks.jks"
    base64 --decode <<< "${DROID_KEYSTORE_B64}" > "${DROID_KEYSTORE_FILE}"
fi

# Build Shelter 
chmod +x ./gradlew
./gradlew assembleRelease
./gradlew bundleRelease

ccache -s -v

if [ ! -z "${DROID_KEYSTORE_B64}" ]; then
    rm "${DROID_KEYSTORE_FILE}"
fi
