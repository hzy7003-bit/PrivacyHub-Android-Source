# Third-party notices

PrivacyHub includes the following runtime dependencies and native components bundled inside SQLCipher. This source-tree notice records component-level attributions and scope; it does not change PrivacyHub's own licensing terms. The in-app **Settings → 许可与开源组件 → 第三方开源许可** screen exposes license text entries for SQLCipher Community Edition, Guava, and other AndroidX/Kotlin components. It does not render this document or display the native OpenSSL/AOSP attributions below. The resolved dependency and attribution audit is recorded in [ThirdPartyDependencyInventory.md](docs/ThirdPartyDependencyInventory.md); this summary is not a complete legal SBOM.

| Component | Version | License / attribution |
|---|---:|---|
| SQLCipher for Android Community Edition (`net.zetetic:sqlcipher-android`) | 4.5.6 | Zetetic BSD-style license reproduced below and in `app/src/main/res/raw/sqlcipher_community_license.txt`. Upstream source: [sqlcipher/sqlcipher-android](https://github.com/sqlcipher/sqlcipher-android/tree/v4.5.6). |
| OpenSSL libcrypto, statically bundled inside SQLCipher's native libraries | 3.0.12 | Apache License 2.0; [exact tagged license](https://github.com/openssl/openssl/blob/openssl-3.0.12/LICENSE.txt). The bundled full Apache 2.0 license supplies the license copy; see the explicit attribution below. |
| Guava Android (`com.google.guava:guava`) | 33.7.1-android | Apache License 2.0; full text is bundled in `app/src/main/res/raw/apache_2_license.txt` and shown in the in-app license screen. Used for the Public Suffix List domain policy. |
| AndroidX Room (`room-runtime`, `room-ktx`) | 2.6.1 | Apache License 2.0. |
| AndroidX SQLite | 2.4.0 | Apache License 2.0. |
| AndroidX Biometric | 1.1.0 | Apache License 2.0. |
| AndroidX Fragment KTX | 1.8.5 | Apache License 2.0. |
| Kotlin standard library and compatibility modules | `kotlin-stdlib` / `kotlin-stdlib-common` 2.0.20; `kotlin-stdlib-jdk7` / `kotlin-stdlib-jdk8` 1.8.22 | Apache License 2.0. |

The current release runtime graph contains 60 resolved coordinates: the selected-version POM/license evidence recorded in the inventory identifies Apache License 2.0 for 59 and BSD-3-Clause for SQLCipher. This includes Guava's transitive support artifacts (including `failureaccess`) and AndroidX/Kotlin compatibility components. The Android platform/SDK is not redistributed as an application library here. Test-only dependencies are not packaged in the app.

The Room compiler (`ksp`) is build-time only; Room Testing, AndroidX Test, and Kotlin Test are test-only and are not packaged in the app. Their transitive graphs must be considered separately: Room compiler declares Apache 2.0 and a BSD license and includes a `NOTICE.txt`; the test graphs also contain JUnit under EPL 1.0 and Hamcrest under a BSD license. These build/test dependencies do not automatically become app redistribution obligations.

The SQLCipher Android Community Edition source repository states that it is distributed as the Community edition through Maven Central. The app does not use a commercial SQLCipher build. The bundled SQLCipher component incorporates the SQLite database engine and Android bindings; upstream identifies SQLite and its Android bindings as public domain. Native Android helper sources retain Apache 2.0 attribution. SQLCipher's own downstream redistribution terms are reproduced in full below.

## OpenSSL and native Android helper attribution

The cached SQLCipher 4.5.6 AAR's four `libsqlcipher.so` files each contain the version string `OpenSSL 3.0.12 24 Oct 2023`. The [tagged SQLCipher native makefile](https://github.com/sqlcipher/sqlcipher-android/blob/v4.5.6/sqlcipher/src/main/jni/sqlcipher/Android.mk) statically links `libcrypto.a`; the source tag does not pin that archive's exact version, so the version is identified from the published binary.

Attribution from the [OpenSSL 3.0.12 tagged README](https://github.com/openssl/openssl/blob/openssl-3.0.12/README.md):

```text
Copyright (c) 1998-2023 The OpenSSL Project
Copyright (c) 1995-1998 Eric A. Young, Tim J. Hudson
All rights reserved.
```

The [tagged SQLCipher `JNIHelp.cpp`](https://github.com/sqlcipher/sqlcipher-android/blob/v4.5.6/sqlcipher/src/main/jni/sqlcipher/JNIHelp.cpp) also carries `Copyright (C) 2006 The Android Open Source Project` under Apache License 2.0. The full Apache 2.0 text is bundled in the app, while these component-specific OpenSSL/AOSP attributions are recorded in this source-tree notice and are not displayed by the current in-app license screen. A copy of Apache 2.0 is required for redistribution; relevant upstream NOTICE contents must also be preserved where supplied. This audit did not establish the complete copyright/NOTICE contents for every embedded source file or reconstruct the vendor's native build.

## SQLCipher for Android Community Edition — Zetetic BSD-style license

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

The exact Apache 2.0 and SQLCipher license texts are packaged in the Android app, so users can access them offline.
