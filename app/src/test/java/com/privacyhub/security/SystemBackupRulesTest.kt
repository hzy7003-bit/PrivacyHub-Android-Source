package com.privacyhub.security

import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class SystemBackupRulesTest {
    @Test
    fun manifestDisablesAutomaticBackupAndReferencesBothRuleSetsWithoutNetworkPermissions() {
        val manifest = parseResource("/AndroidManifest.xml")
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val application = manifest.getElementsByTagName("application").item(0)
        assertNotNull(application)
        assertEquals("false", application.attributes.getNamedItemNS(androidNamespace, "allowBackup").nodeValue)
        assertEquals("@xml/data_extraction_rules", application.attributes.getNamedItemNS(androidNamespace, "dataExtractionRules").nodeValue)
        assertEquals("@xml/backup_rules", application.attributes.getNamedItemNS(androidNamespace, "fullBackupContent").nodeValue)
        assertEquals(0, manifest.getElementsByTagName("uses-permission").let { nodes ->
            (0 until nodes.length).count {
                nodes.item(it).attributes.getNamedItemNS(androidNamespace, "name")?.nodeValue in
                    setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
            }
        })
    }

    @Test
    fun modernCloudAndDeviceTransferRulesExcludeEverySensitiveStorageDomain() {
        val document = parseResource("/data_extraction_rules.xml")
        val expectedDomains = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref"
        )
        listOf("cloud-backup", "device-transfer").forEach { sectionName ->
            val section = document.getElementsByTagName(sectionName).item(0)
            assertNotNull(section)
            assertEquals(0, section.childNodes.let { nodes ->
                (0 until nodes.length).count { nodes.item(it).nodeName == "include" }
            }, "$sectionName must never opt data back in")
            val exclusions = section.childNodes.let { nodes ->
                (0 until nodes.length).mapNotNull { index ->
                    nodes.item(index).takeIf { it.nodeName == "exclude" }
                }
            }
            assertEquals(expectedDomains, exclusions.map {
                assertEquals(".", it.attributes.getNamedItem("path").nodeValue)
                it.attributes.getNamedItem("domain").nodeValue
            }.toSet(), sectionName)
        }
    }

    @Test
    fun legacyFullBackupRulesExcludeEveryAvailableStorageDomain() {
        val document = parseResource("/backup_rules.xml")
        val domains = setOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref"
        )
        val exclusions = document.getElementsByTagName("exclude")
        assertEquals(domains, (0 until exclusions.length).map { index ->
            val exclusion = exclusions.item(index)
            assertEquals(".", exclusion.attributes.getNamedItem("path").nodeValue)
            exclusion.attributes.getNamedItem("domain").nodeValue
        }.toSet())
        assertFalse(document.getElementsByTagName("include").length > 0)
    }

    private fun parseResource(path: String) = resource(path).use { input ->
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }.newDocumentBuilder().parse(input)
    }

    private fun resource(path: String): InputStream =
        requireNotNull(javaClass.getResourceAsStream(path)) { "Missing test resource: $path" }
}
