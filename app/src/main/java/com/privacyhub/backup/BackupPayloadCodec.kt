package com.privacyhub.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

object BackupPayloadCodec {
    private const val FORMAT_VERSION = 5
    private const val LEGACY_FORMAT_VERSION = 1
    private const val MAX_ITEM_COUNT = 50_000
    private const val MAX_COLLECTION_SIZE = 1_000
    internal const val MAX_TEXT_BYTES = 1_048_576

    internal fun isCompatibleText(value: String): Boolean {
        if (value.length > MAX_TEXT_BYTES) return false
        return value.encodeToByteArray().size <= MAX_TEXT_BYTES
    }

    internal fun requireCompatibleText(value: String) {
        if (!isCompatibleText(value)) {
            throw BackupFormatException("文本超过 1 MiB 备份上限，未保存。")
        }
    }

    fun encode(payload: BackupPayload): ByteArray {
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(FORMAT_VERSION)
                output.writeLong(payload.createdAtMillis)
                output.writeCount(payload.items.size, MAX_ITEM_COUNT)
                payload.items.forEach { item -> output.writeItem(item) }
                output.writeSettings(payload.settings)
                output.writeCount(payload.autofillCredentials.size, MAX_ITEM_COUNT)
                payload.autofillCredentials.forEach { credential -> output.writeAutofillCredential(credential) }
            }
            bytes.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): BackupPayload {
        try {
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val version = input.readInt()
                if (version !in LEGACY_FORMAT_VERSION..FORMAT_VERSION) {
                    throw BackupFormatException("不支持的备份数据版本。")
                }
                val createdAtMillis = input.readLong()
                val items = List(input.readCount(MAX_ITEM_COUNT)) { input.readItem(version) }
                val settings = input.readSettings()
                val credentials = if (version >= 2) {
                    List(input.readCount(MAX_ITEM_COUNT)) { input.readAutofillCredential(version) }
                } else {
                    emptyList()
                }
                if (input.read() != -1) {
                    throw BackupFormatException("备份数据包含无法识别的内容。")
                }
                BackupPayload(createdAtMillis, items, settings, credentials)
            }
        } catch (error: BackupFormatException) {
            throw error
        } catch (_: EOFException) {
            throw BackupFormatException("备份数据不完整。")
        } catch (_: IllegalArgumentException) {
            throw BackupFormatException("备份数据格式无效。")
        }
    }

    private fun DataOutputStream.writeItem(item: VaultBackupItem) {
        writeText(item.backupId)
        writeText(item.type)
        writeText(item.title)
        writeText(item.content)
        writeOptionalText(item.normalizedLink)
        writeOptionalText(item.originalLink)
        writeOptionalText(item.accessCode)
        writeText(item.sourceApp)
        writeText(item.entryPoint)
        writeOptionalText(item.category)
        writeOptionalText(item.note)
        writeBoolean(item.favorite)
        writeLong(item.createdAtMillis)
        writeLong(item.updatedAtMillis)
        writeBoolean(item.lastOpenedAtMillis != null)
        item.lastOpenedAtMillis?.let(::writeLong)
        writeText(item.target)
    }

    private fun DataInputStream.readItem(version: Int): VaultBackupItem {
        return VaultBackupItem(
            backupId = readText(),
            type = readText(),
            title = readText(),
            content = readText(),
            normalizedLink = readOptionalText(),
            originalLink = readOptionalText(),
            accessCode = if (version >= 3) readOptionalText() else null,
            sourceApp = readText(),
            entryPoint = readText(),
            category = readOptionalText(),
            note = readOptionalText(),
            favorite = readBoolean(),
            createdAtMillis = readLong(),
            updatedAtMillis = readLong(),
            lastOpenedAtMillis = if (readBoolean()) readLong() else null,
            target = readText()
        )
    }

    private fun DataOutputStream.writeAutofillCredential(credential: AutofillBackupCredential) {
        writeText(credential.backupId)
        writeText(credential.label)
        writeText(credential.username)
        writeText(credential.password)
        writeOptionalText(credential.packageName)
        writeOptionalText(credential.webDomain)
        writeLong(credential.createdAtMillis)
        writeLong(credential.updatedAtMillis)
        writeOptionalText(credential.packageSigningCertificateSha256)
    }

    private fun DataInputStream.readAutofillCredential(version: Int): AutofillBackupCredential {
        return AutofillBackupCredential(
            backupId = readText(),
            label = readText(),
            username = readText(),
            password = readText(),
            packageName = readOptionalText(),
            webDomain = if (version >= 4) readOptionalText() else null,
            createdAtMillis = readLong(),
            updatedAtMillis = readLong(),
            packageSigningCertificateSha256 = if (version >= 5) readOptionalText() else null
        )
    }

    private fun DataOutputStream.writeSettings(settings: PortableBackupSettings) {
        writeStringList(settings.customCategories)
        writeCount(settings.categoryAliases.size, MAX_COLLECTION_SIZE)
        settings.categoryAliases.toSortedMap().forEach { (source, destination) ->
            writeText(source)
            writeText(destination)
        }
        writeStringList(settings.homeTags)
    }

    private fun DataInputStream.readSettings(): PortableBackupSettings {
        val customCategories = readStringList()
        val aliases = buildMap {
            repeat(readCount(MAX_COLLECTION_SIZE)) {
                put(readText(), readText())
            }
        }
        return PortableBackupSettings(
            customCategories = customCategories,
            categoryAliases = aliases,
            homeTags = readStringList()
        )
    }

    private fun DataOutputStream.writeStringList(values: List<String>) {
        writeCount(values.size, MAX_COLLECTION_SIZE)
        values.forEach { value -> writeText(value) }
    }

    private fun DataInputStream.readStringList(): List<String> {
        return List(readCount(MAX_COLLECTION_SIZE)) { readText() }
    }

    private fun DataOutputStream.writeOptionalText(value: String?) {
        if (value == null) {
            writeInt(-1)
        } else {
            writeText(value)
        }
    }

    private fun DataInputStream.readOptionalText(): String? {
        val length = readInt()
        if (length == -1) return null
        return readText(length)
    }

    private fun DataOutputStream.writeText(value: String) {
        val encoded = value.encodeToByteArray()
        if (encoded.size > MAX_TEXT_BYTES) {
            throw BackupFormatException("单条备份内容过大。")
        }
        writeInt(encoded.size)
        write(encoded)
    }

    private fun DataInputStream.readText(): String = readText(readInt())

    private fun DataInputStream.readText(length: Int): String {
        if (length !in 0..MAX_TEXT_BYTES) {
            throw BackupFormatException("备份文本长度无效。")
        }
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.decodeToString()
    }

    private fun DataOutputStream.writeCount(value: Int, max: Int) {
        if (value !in 0..max) {
            throw BackupFormatException("备份集合数量无效。")
        }
        writeInt(value)
    }

    private fun DataInputStream.readCount(max: Int): Int {
        val count = readInt()
        if (count !in 0..max) {
            throw BackupFormatException("备份集合数量无效。")
        }
        return count
    }
}
