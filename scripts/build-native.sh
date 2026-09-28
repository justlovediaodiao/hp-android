#!/usr/bin/env bash
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK directory}"
ndk="${ANDROID_NDK_HOME:-$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)}"
: "${ndk:?Install Android NDK first}"
hev_source="$repo/build/native/hev-socks5-tunnel"
hev_tag="2.17.1"

if [[ ! -d "$hev_source/.git" ]]; then
    mkdir -p "$(dirname "$hev_source")"
    git clone --depth 1 --branch "$hev_tag" --recurse-submodules \
        https://github.com/heiher/hev-socks5-tunnel "$hev_source"
fi
if [[ "$(git -C "$hev_source" rev-parse HEAD)" != "$(git -C "$hev_source" rev-parse "refs/tags/$hev_tag^{commit}")" ]]; then
    echo "hev-socks5-tunnel must be at tag $hev_tag" >&2
    exit 1
fi
git -C "$hev_source" submodule update --init --recursive

(cd "$repo/proxy-go" && gomobile bind -target=android/arm64 -androidapi 26 \
    -javapkg com.hp.proxy \
    -ldflags="-linkmode=external -extldflags=-Wl,-z,max-page-size=16384" \
    -o "$repo/build/native/hp-proxy.aar" ./mobile)

hev_build="$(mktemp -d "${TMPDIR:-/tmp}/hp-hev-build.XXXXXX")"
trap 'rm -rf "$hev_build"' EXIT
cp -R "$hev_source/." "$hev_build/"
rm -rf "$hev_build/obj"
(cd "$hev_build" && git apply --check "$repo/patches/hev-persistent-mapdns.patch")
(cd "$hev_build" && git apply "$repo/patches/hev-persistent-mapdns.patch")

"$ndk/ndk-build" -C "$hev_build" NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT="$hev_build/Android.mk" \
    APP_PLATFORM=android-26 APP_ABI="arm64-v8a" APP_STL=none \
    APP_CFLAGS="-DPKGNAME=com/hp/vpn/vpn -DCLSNAME=HevNative" \
    APP_MODULES=hev-socks5-tunnel \
    NDK_LIBS_OUT="$repo/build/native/jniLibs"
