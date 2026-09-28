# PrivacyHub — Source Available

PrivacyHub 0.10.4-beta (`versionCode` 132) is published here as a reviewed source snapshot of the private release tag `v0.10.4-beta`.

Required Notice: Copyright 2026 hzy7003-bit

The source is made available so users and security researchers can independently inspect PrivacyHub's privacy and security claims. This repository is Source Available under the PolyForm Noncommercial License 1.0.0. See [LICENSE](LICENSE), [COMMERCIAL_LICENSE.md](COMMERCIAL_LICENSE.md), and [SOURCE_DISCLOSURE.md](SOURCE_DISCLOSURE.md).

## Privacy and security at a glance

The statements below describe this `v0.10.4-beta` snapshot; they are not a promise that every Android device, ROM, browser, or third-party app behaves identically.

- The app manifest declares no `INTERNET`, `ACCESS_NETWORK_STATE`, or `QUERY_ALL_PACKAGES` permission. The project has no ads SDK, analytics SDK, or cloud-sync implementation.
- Vault data is stored locally using Room with SQLCipher; database-key material is protected through Android Keystore.
- Android backup is disabled (`allowBackup=false`), and the declared legacy, cloud-backup, and device-transfer rules exclude app data.
- Native-app Autofill requests are bound to the target package and its installed signer identity. Browser, Web, and WebView Autofill requests fail closed in this release.
- The project uses Kotlin and Android Native View/XML. Build and test instructions are in [BUILDING.md](BUILDING.md).

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the components and license material distributed with the app and source snapshot.

## License and contributions

Noncommercial use is governed by the PolyForm Noncommercial License 1.0.0 in [LICENSE](LICENSE). Commercial use requires separate written authorization; this repository does not itself grant that authorization. Project names and branding are not granted by the source license; see [TRADEMARKS.md](TRADEMARKS.md).

External code contributions are not currently accepted. Issues, bug reports, security reports, audit feedback, and documentation issue reports are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## Official Android release

Source-built APKs are not official PrivacyHub releases. The official APK is distributed separately from the [PrivacyHub-Android-Preview v0.10.4-beta release](https://github.com/hzy7003-bit/PrivacyHub-Android-Preview/releases/tag/v0.10.4-beta) and is signed by the project's official release signer.

- File: `PrivacyHub-0.10.4-beta.apk`
- Package / version: `com.privacyhub`, `0.10.4-beta` (`versionCode` 132)
- Minimum / target SDK: 26 / 35
- `debuggable`: false; `allowBackup`: false
- SHA-256: `6474D4F2D67E33EFB639BBCC579D81D875532DB9A5B9CFB11A2B0D73B9217FC4`
- Official signer certificate SHA-256: `FAFCDDCE1E680A685C9C0B222D996C99ACE9E1EC3F755BD238F6ED1C5D2D1709`

Only an APK distributed by the project and signed by that official release identity is an official PrivacyHub release. A locally built or modified APK is not an official release, even when built from this snapshot.
