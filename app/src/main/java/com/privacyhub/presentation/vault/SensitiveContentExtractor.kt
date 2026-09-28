package com.privacyhub.presentation.vault

internal object SensitiveContentExtractor {
    fun identityCards(text: String): List<String> {
        return Regex("""(?<![0-9A-Za-z])\d{17}[\dXx](?![0-9A-Za-z])""")
            .findAll(text)
            .map { it.value.uppercase() }
            .distinct()
            .toList()
    }

    fun bankCards(text: String): List<String> {
        val identityCards = identityCards(text).toSet()
        return Regex("""(?:\d[ -]?){16,19}""")
            .findAll(text)
            .map { it.value.filter(Char::isDigit) }
            .filter { it.length in 16..19 }
            .filterNot(identityCards::contains)
            .distinct()
            .toList()
    }

    fun verificationCodes(text: String): List<String> {
        return Regex("""(?i)(?:验证码|校验码|动态码|code)\s*(?:是|为)?\s*[:：]?\s*(\d{4,8})(?!\d)""")
            .findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .distinct()
            .toList()
    }

    fun password(text: String): String? {
        val pattern = Regex("""(?i)(?:密码|口令|password|passcode)[:：\s]*([^\s，,；;。]{4,64})""")
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
    }
}
