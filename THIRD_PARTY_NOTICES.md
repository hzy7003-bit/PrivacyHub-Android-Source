# Third-party notices

This notice covers third-party components shipped in the PrivacyHub 0.10.5-beta application and the third-party binary included in this source snapshot. It is a scoped attribution summary, not an exhaustive software bill of materials or a legal opinion. Each component remains under its own license; the PrivacyHub license does not replace those terms.

## Application runtime

| Component | Version / source | License and included notice material |
|---|---|---|
| SQLCipher for Android Community Edition (`net.zetetic:sqlcipher-android`) | 4.5.6; [upstream release](https://github.com/sqlcipher/sqlcipher-android/tree/v4.5.6) | Zetetic BSD-style license. The app includes the copyright, conditions, disclaimer, and no-endorsement restriction in `app/src/main/res/raw/sqlcipher_community_license.txt` and makes that text available in its offline license screen. SQLite components are public domain. |
| OpenSSL `libcrypto`, bundled in SQLCipher native libraries | OpenSSL 3.0.12 version string is present in the released native libraries for the four packaged ABIs; [upstream release](https://github.com/openssl/openssl/tree/openssl-3.0.12) | Apache License 2.0. The app packages the full Apache license text in `app/src/main/res/raw/apache_2_license.txt`. The exact private release source/resources and official APK contain no separately named OpenSSL attribution resource; this source-repository notice records the bundled component and upstream license without claiming that a separate OpenSSL notice is displayed in the APK. No separate `NOTICE` file was present in the exact upstream OpenSSL 3.0.12 tree inspected for this snapshot review. |
| Guava Android (`com.google.guava:guava`) and `failureaccess` | 33.7.1-android and its resolved runtime dependency | Apache License 2.0; the full text is included in `app/src/main/res/raw/apache_2_license.txt` and accessible offline in the app. |
| AndroidX, Kotlin runtime, and other Apache-licensed runtime components | Versions pinned/resolved by the release build | Apache License 2.0; the full text is included in `app/src/main/res/raw/apache_2_license.txt` and accessible through the app's offline third-party license screen. |

### SQLCipher for Android Community Edition — license text

The following text is reproduced from the exact release's bundled `app/src/main/res/raw/sqlcipher_community_license.txt`:

```text
Copyright (c) 2008-2023, ZETETIC LLC
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the ZETETIC LLC nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY ZETETIC LLC ''AS IS'' AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL ZETETIC LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

The reviewed release APK has no `INTERNET` permission. It does not include the Gradle compiler/test toolchain as application libraries. Its packaged application runtime graph is separate from the build-only and test-only artifacts described below.

## Source-snapshot bundled build bootstrap

The source snapshot includes Gradle Wrapper 8.7 (`gradle/wrapper/gradle-wrapper.jar`) and the standard Wrapper scripts. The Wrapper JAR contains `META-INF/LICENSE` under Apache License 2.0; the wrapper scripts retain their license headers. Gradle's [official Wrapper release notes](https://docs.gradle.org/8.5/release-notes.html#_wrapper_improvements) state that the Wrapper JAR is self-attributing and does not need a separate `LICENSE` file. The app's bundled Apache license text is also present in this snapshot. The Gradle distribution and other Gradle/Maven dependencies are fetched by the Wrapper for local builds and are not bundled in the snapshot.

## Build-fetched and test-only components

These artifacts are not included in the app APK or this source snapshot; Gradle resolves them when a developer builds or tests the project. Their licenses apply if the artifacts or their sources are separately redistributed.

- TensorFlow Lite metadata `org.tensorflow:tensorflow-lite-metadata:0.1.0-rc2` is build-fetched only. Its exact artifact license/source mapping remains unestablished; do not redistribute that artifact or source from this repository without a separate review.
- Room compiler `androidx.room:room-compiler:2.6.1` is a KSP build-time compiler only. If the compiler artifact/source is redistributed, preserve its supplied NOTICE and applicable license material after resolving the NOTICE's embedded-component mapping.
- kXML2 `net.sf.kxml:kxml2:2.3.0` is build-fetched only. The exact 2.3.0 source identifies MIT terms for `org.kxml2` (Copyright (c) 2002, 2003 Stefan Haustein); its POM separately scopes a Public Domain declaration to classes directly in `org.xmlpull.v1`. No kXML code is bundled in the app or snapshot.
- JUnit, Hamcrest, Room Testing, AndroidX Test, Kotlin Test, AGP, KSP, Kotlin compiler/plugin, and other build/test tools are not bundled in the app APK or source snapshot. Their exact artifact notices and licenses apply if those artifacts are separately redistributed; this file does not mechanically reproduce the whole build-tool ecosystem.

For build instructions, see [BUILDING.md](BUILDING.md). For the official application download and hashes, see [README.md](README.md).
