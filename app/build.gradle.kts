import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Properties
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localOrGradleProperty(name: String): String? =
    providers.gradleProperty(name).orNull ?: localProperties.getProperty(name)

val releaseStoreFile = localOrGradleProperty("privacyHub.release.storeFile")
val releaseStorePassword = localOrGradleProperty("privacyHub.release.storePassword")
val releaseKeyAlias = localOrGradleProperty("privacyHub.release.keyAlias")
val releaseKeyPassword = localOrGradleProperty("privacyHub.release.keyPassword")
val releaseSigningProperties = linkedMapOf(
    "privacyHub.release.storeFile" to releaseStoreFile,
    "privacyHub.release.storePassword" to releaseStorePassword,
    "privacyHub.release.keyAlias" to releaseKeyAlias,
    "privacyHub.release.keyPassword" to releaseKeyPassword
)
val hasCompleteReleaseSigning = releaseSigningProperties.values.all { !it.isNullOrBlank() }

private val EXPECTED_RELEASE_CERTIFICATE_SHA256 =
    "FAFCDDCE1E680A685C9C0B222D996C99ACE9E1EC3F755BD238F6ED1C5D2D1709"

private data class ReleaseSigningConfiguration(
    val storeFile: String?,
    val storePassword: String?,
    val keyAlias: String?,
    val keyPassword: String?
)

private val releaseSigningConfiguration = ReleaseSigningConfiguration(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
)

private fun normalizeFingerprint(value: String): String =
    value.filterNot { it == ':' || it.isWhitespace() }.uppercase()

private fun certificateFingerprint(certificate: X509Certificate): String =
    MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString("") { "%02X".format(it) }

private fun missingReleaseSigningProperties(configuration: ReleaseSigningConfiguration): List<String> =
    linkedMapOf(
        "privacyHub.release.storeFile" to configuration.storeFile,
        "privacyHub.release.storePassword" to configuration.storePassword,
        "privacyHub.release.keyAlias" to configuration.keyAlias,
        "privacyHub.release.keyPassword" to configuration.keyPassword
    ).filterValues { it.isNullOrBlank() }.keys.toList()

private fun loadReleaseKeyStore(
    configuration: ReleaseSigningConfiguration,
    project: org.gradle.api.Project
): KeyStore {
    val storeFile = configuration.storeFile
        ?.let(project::file)
        ?: throw GradleException("缺少正式 Release 签名 keystore 配置，拒绝生成发布产物")
    if (!storeFile.isFile || !storeFile.canRead()) {
        throw GradleException("正式 Release 签名 keystore 文件不存在或不可读，拒绝生成发布产物")
    }

    val password = configuration.storePassword
        ?: throw GradleException("缺少正式 Release 签名 keystore 密码，拒绝生成发布产物")
    val keyStoreTypes = listOf(KeyStore.getDefaultType(), "JKS", "PKCS12").distinct()
    for (type in keyStoreTypes) {
        try {
            return KeyStore.getInstance(type).also { keyStore ->
                storeFile.inputStream().use { input ->
                    keyStore.load(input, password.toCharArray())
                }
            }
        } catch (_: Exception) {
            // Try the other standard keystore type without exposing passwords or paths.
        }
    }
    throw GradleException("无法打开正式 Release 签名 keystore，拒绝生成发布产物")
}

private fun validateReleaseSigningConfiguration(
    configuration: ReleaseSigningConfiguration,
    project: org.gradle.api.Project
): X509Certificate {
    val missing = missingReleaseSigningProperties(configuration)
    if (missing.size == releaseSigningProperties.size) {
        throw GradleException(
            "缺少正式 Release 签名配置，拒绝生成发布产物。请配置四项 privacyHub.release.* 属性"
        )
    }
    if (missing.isNotEmpty()) {
        throw GradleException(
            "正式 Release 签名配置不完整，缺少：${missing.joinToString(", ")}。拒绝生成发布产物"
        )
    }

    val keyStore = loadReleaseKeyStore(configuration, project)
    val alias = configuration.keyAlias!!
    if (!keyStore.containsAlias(alias) || !keyStore.isKeyEntry(alias)) {
        throw GradleException("正式 Release 签名 alias 不存在或不是密钥条目，拒绝生成发布产物")
    }
    try {
        keyStore.getKey(alias, configuration.keyPassword!!.toCharArray())
            ?: throw GradleException("正式 Release 签名密钥不可用，拒绝生成发布产物")
    } catch (_: Exception) {
        throw GradleException("正式 Release 签名密钥密码无效，拒绝生成发布产物")
    }

    return keyStore.getCertificate(alias) as? X509Certificate
        ?: throw GradleException("正式 Release 签名证书不可用，拒绝生成发布产物")
}

private fun validateReleaseSigningIdentity(
    configuration: ReleaseSigningConfiguration,
    project: org.gradle.api.Project,
    expectedFingerprint: String = EXPECTED_RELEASE_CERTIFICATE_SHA256
) {
    val certificate = validateReleaseSigningConfiguration(configuration, project)
    if (normalizeFingerprint(certificateFingerprint(certificate)) != normalizeFingerprint(expectedFingerprint)) {
        throw GradleException("正式 Release 签名证书身份不匹配，拒绝生成发布产物")
    }
}

android {
    namespace = "com.privacyhub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.privacyhub"
        minSdk = 26
        targetSdk = 35
        versionCode = localOrGradleProperty("privacyHub.versionCode")?.toIntOrNull() ?: 134
        versionName = localOrGradleProperty("privacyHub.versionName") ?: "0.10.6-beta"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("androidTest").assets.srcDir(file("schemas"))
        getByName("test").resources.srcDir(file("src/main/res/xml"))
        getByName("test").resources.srcDir(file("src/main/res/raw"))
        getByName("test").resources.srcDir(file("src/main/AndroidManifest.xml"))
        getByName("test").resources.srcDir(rootProject.file("THIRD_PARTY_NOTICES.md"))
    }

    applicationVariants.all {
        outputs.all {
            val variantOutput = this as BaseVariantOutputImpl
            variantOutput.outputFileName = "PrivacyHub-v$versionName-${buildType.name}.apk"
        }
    }
}

val verifyReleaseSigningConfiguration = tasks.register("verifyReleaseSigningConfiguration") {
    group = "verification"
    description = "Validates the complete formal Release keystore configuration without packaging."
    doLast {
        validateReleaseSigningConfiguration(releaseSigningConfiguration, project)
    }
}

val verifyReleaseSigningIdentity = tasks.register("verifyReleaseSigningIdentity") {
    group = "verification"
    description = "Validates the formal Release certificate identity without packaging."
    dependsOn(verifyReleaseSigningConfiguration)
    doLast {
        validateReleaseSigningIdentity(releaseSigningConfiguration, project)
    }
}

val testReleaseSigningGate = tasks.register("testReleaseSigningGate") {
    group = "verification"
    description = "Runs isolated regression cases for the Release signing gate."
    doLast {
        val fixtureDirectory = layout.buildDirectory
            .dir("tmp/release-signing-gate-tests")
            .get()
            .asFile
        project.delete(fixtureDirectory)
        fixtureDirectory.mkdirs()
        val password = "gate-test-password"
        val expectedAlias = "release-test"
        val debugAlias = "debug-test"
        val expectedKeystore = File(fixtureDirectory, "release-test.jks")
        val debugKeystore = File(fixtureDirectory, "debug-test.jks")
        val keytool = listOf(
            File(System.getProperty("java.home"), "bin/keytool"),
            File(System.getProperty("java.home"), "bin/keytool.exe")
        ).firstOrNull(File::isFile)
            ?: throw GradleException("找不到 JDK keytool，无法运行 Release 签名门禁回归测试")

        fun generateFixture(file: File, alias: String) {
            val process = ProcessBuilder(
                keytool.absolutePath,
                "-genkeypair",
                "-noprompt",
                "-keystore", file.absolutePath,
                "-storetype", "JKS",
                "-storepass", password,
                "-keypass", password,
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "2",
                "-dname", "CN=PrivacyHub Release Signing Gate Test"
            ).redirectErrorStream(true).start()
            process.inputStream.use { it.readBytes() }
            if (process.waitFor() != 0) {
                throw GradleException("无法生成 Release 签名门禁临时测试 keystore")
            }
        }

        fun expectFailure(caseName: String, action: () -> Unit) {
            val failed = try {
                action()
                false
            } catch (_: Exception) {
                true
            }
            if (!failed) {
                throw GradleException("Release 签名门禁回归测试失败：$caseName 应当拒绝")
            }
        }

        try {
            generateFixture(expectedKeystore, expectedAlias)
            generateFixture(debugKeystore, debugAlias)
            val expectedConfiguration = ReleaseSigningConfiguration(
                expectedKeystore.absolutePath,
                password,
                expectedAlias,
                password
            )
            val expectedCertificate = validateReleaseSigningConfiguration(expectedConfiguration, project)
            val expectedFingerprint = certificateFingerprint(expectedCertificate)

            expectFailure("all properties missing") {
                validateReleaseSigningIdentity(ReleaseSigningConfiguration(null, null, null, null), project, expectedFingerprint)
            }
            expectFailure("partial properties") {
                validateReleaseSigningIdentity(expectedConfiguration.copy(keyPassword = null), project, expectedFingerprint)
            }
            expectFailure("missing keystore") {
                validateReleaseSigningIdentity(expectedConfiguration.copy(storeFile = File(fixtureDirectory, "missing.jks").absolutePath), project, expectedFingerprint)
            }
            expectFailure("bad alias") {
                validateReleaseSigningIdentity(expectedConfiguration.copy(keyAlias = "missing-alias"), project, expectedFingerprint)
            }
            expectFailure("bad key password") {
                validateReleaseSigningIdentity(expectedConfiguration.copy(keyPassword = "wrong-password"), project, expectedFingerprint)
            }
            val debugConfiguration = ReleaseSigningConfiguration(
                debugKeystore.absolutePath,
                password,
                debugAlias,
                password
            )
            expectFailure("debug or wrong certificate identity") {
                validateReleaseSigningIdentity(debugConfiguration, project, expectedFingerprint)
            }
            validateReleaseSigningIdentity(expectedConfiguration, project, expectedFingerprint)
        } finally {
            project.delete(fixtureDirectory)
        }
    }
}

afterEvaluate {
    setOf(
        "assembleRelease",
        "bundleRelease",
        "packageRelease",
        "packageReleaseBundle",
        "packageReleaseUniversalApk",
        "signRelease",
        "signReleaseBundle"
    ).forEach { finalPackagingTaskName ->
        tasks.matching { it.name == finalPackagingTaskName }.configureEach {
            dependsOn(verifyReleaseSigningIdentity)
        }
    }
}

ksp {
    arg("room.schemaLocation", file("schemas").path)
}

dependencies {
    val roomVersion = "2.6.1"

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.sqlite:sqlite:2.4.0")
    implementation("net.zetetic:sqlcipher-android:4.5.6")
    implementation("com.google.guava:guava:33.7.1-android")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")

    testImplementation(kotlin("test"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
}

afterEvaluate {
    tasks.withType<Test>().configureEach {
        val variantName = name
            .removePrefix("test")
            .removeSuffix("UnitTest")
            .replaceFirstChar { it.lowercase() }
        if (variantName.isNotBlank()) {
            val variantTaskName = variantName.replaceFirstChar { it.uppercase() }
            val safeRuntimeDir = File(
                System.getProperty("java.io.tmpdir"),
                "privacyhub-test-runtime/${project.name}/$variantName"
            )
            val safeMainKotlinClasses = File(safeRuntimeDir, "main-kotlin")
            val safeMainJavaClasses = File(safeRuntimeDir, "main-java")
            val safeTestKotlinClasses = File(safeRuntimeDir, "test-kotlin")
            val safeTestJavaClasses = File(safeRuntimeDir, "test-java")

            doFirst {
                delete(safeRuntimeDir)
                copy {
                    from(layout.buildDirectory.dir("tmp/kotlin-classes/$variantName"))
                    into(safeMainKotlinClasses)
                }
                copy {
                    from(
                        layout.buildDirectory.dir(
                            "intermediates/javac/$variantName/compile${variantTaskName}JavaWithJavac/classes"
                        )
                    )
                    into(safeMainJavaClasses)
                }
                copy {
                    from(layout.buildDirectory.dir("tmp/kotlin-classes/${variantName}UnitTest"))
                    into(safeTestKotlinClasses)
                }
                copy {
                    from(
                        layout.buildDirectory.dir(
                            "intermediates/javac/${variantName}UnitTest/compile${variantTaskName}UnitTestJavaWithJavac/classes"
                        )
                    )
                    into(safeTestJavaClasses)
                }
            }

            classpath = files(
                safeTestKotlinClasses,
                safeTestJavaClasses,
                safeMainKotlinClasses,
                safeMainJavaClasses
            ).plus(classpath)
        }
    }
}
