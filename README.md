# PrivacyHub — Source Available

PrivacyHub 0.10.5-beta (`versionCode` 133) is published here as a reviewed source snapshot based on the official private release tag `v0.10.5-beta` at `20a8bb55d1f40f460a59252dad15bbd40312095b`.

Required Notice: Copyright 2026 hzy7003-bit

This repository is **Source Available**, not Open Source / FOSS, under the PolyForm Noncommercial License 1.0.0. It is provided so users and security researchers can inspect the implementation and review the privacy and security boundaries. See [LICENSE](LICENSE), [COMMERCIAL_LICENSE.md](COMMERCIAL_LICENSE.md), and [SOURCE_DISCLOSURE.md](SOURCE_DISCLOSURE.md).

## Privacy and security at a glance

These statements describe the source snapshot and the identified official release. Android versions, device vendors, ROMs, browsers, and third-party apps can affect runtime behavior.

- The app manifest has no `INTERNET`, `ACCESS_NETWORK_STATE`, or `QUERY_ALL_PACKAGES` permission. The app dependency graph declares no ads or analytics SDK, and this snapshot contains no cloud-sync implementation.
- Vault data is stored locally with Room and SQLCipher. Database-key material is protected through Android Keystore.
- `allowBackup=false` is set, and the declared legacy backup, cloud-backup, and device-transfer rules exclude app data.
- Native-app Autofill requests are bound to the target package and installed signer identity. Web, browser, and WebView Autofill requests fail closed in this release.
- Optional Pro accessibility automation requires the user-enabled service and an active task. It is limited by supported browser package, supported netdisk host, and a specifically identified access-code input; it does not submit the page.
- External share intake accepts only the supported text actions and text MIME types, and rejects a single input above 128 Ki characters before saving.
- Database work runs away from screen rendering, with lifecycle checks for asynchronous results. Cross-store category changes use journaled recovery for interrupted updates.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party components and included license materials. See [BUILDING.md](BUILDING.md) for independent Debug build instructions.

## License, names, and feedback

Noncommercial use is governed by the PolyForm Noncommercial License 1.0.0 in [LICENSE](LICENSE). Commercial use requires separate written authorization. Project names and branding are not licensed by the source license; see [TRADEMARKS.md](TRADEMARKS.md).

External code contributions and pull requests are not currently accepted. Issues, security reports, audit feedback, and documentation reports are welcome; see [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

## Official Android release

The official APK is distributed through the separate [PrivacyHub-Android-Preview v0.10.5-beta release](https://github.com/hzy7003-bit/PrivacyHub-Android-Preview/releases/tag/v0.10.5-beta). A locally built or modified APK is not an official release; this snapshot does not provide the official release signing material or make a reproducible-build claim.

- Official artifact: `PrivacyHub-v0.10.5-beta-release.apk` (the Preview release distributes the same APK bytes as `PrivacyHub-0.10.5-beta.apk`)
- Package / version: `com.privacyhub`, `0.10.5-beta` (`versionCode` 133)
- Minimum / target SDK: 26 / 35
- `debuggable`: false; `allowBackup`: false
- Size: 23,726,447 bytes
- SHA-256: `62dd94f8b6d6dda0406ad5e440706f18c66e0c6dfaa4a34f033a1db916b4842c`
- Official signer certificate SHA-256: `FAFCDDCE1E680A685C9C0B222D996C99ACE9E1EC3F755BD238F6ED1C5D2D1709`
