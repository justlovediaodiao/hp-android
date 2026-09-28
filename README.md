# HP VPN

HP VPN is an Android client for [https-proxy](https://github.com/justlovediaodiao/https-proxy). It uses [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) to provide a device VPN with global and per-app proxy routing.

## Configuration

Create a profile in the app, or copy a JSON object like this and tap **Import from clipboard**:

```json
{"server":"example.com:443","password":"secret"}
```

`server` is a host and port. The optional `cert` field accepts a PEM certificate with escaped newlines. If omitted, the app uses Android's trusted certificates.

## Build


```sh
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
export PATH="$(go env GOPATH)/bin:$PATH"

cd proxy-go
go install golang.org/x/mobile/cmd/gomobile golang.org/x/mobile/cmd/gobind
cd ..

./scripts/build-native.sh
./gradlew assembleDebug
```

The installable debug APK is `build/app/outputs/apk/debug/app-debug.apk`. Run `./gradlew assembleRelease` for an unsigned release APK, then sign it with your release key.

Keep `gradle/wrapper/gradle-wrapper.jar` in Git: `./gradlew` needs it to download and run the Gradle version specified in `gradle-wrapper.properties`.

## GitHub releases

The workflow in `.github/workflows/release.yml` builds the native libraries and a signed release APK. Configure these repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `SIGNING_KEY` | Base64-encoded release keystore (JKS or PKCS12) |
| `ALIAS` | Signing key alias |
| `KEY_STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Signing key password |

Use the same signing key for every release so users can install updates. Push a tag such as `v0.1.0` to build and publish `hp-vpn.apk` as a GitHub Release asset. You can also run **Build and Release APK** manually from the Actions tab; manual runs upload the signed APK as a workflow artifact without creating a release. The app version is currently set in `app/build.gradle.kts`; update `versionCode` and `versionName` before tagging a new release.
