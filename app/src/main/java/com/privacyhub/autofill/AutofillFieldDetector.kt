package com.privacyhub.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.autofill.AutofillId

internal data class DetectedAutofillFields(
    val username: AutofillId?,
    val password: AutofillId?
)

internal fun findLoginFields(structure: AssistStructure): DetectedAutofillFields {
    var username: AutofillId? = null
    var password: AutofillId? = null

    fun visit(node: AssistStructure.ViewNode) {
        val hints = node.autofillHints.orEmpty().map(String::lowercase)
        val inputType = node.inputType
        val isPassword = hints.any { it.contains("password") } ||
            inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        val isUsername = hints.any { hint ->
            hint.contains("username") || hint.contains("email") || hint.contains("phone")
        }
        if (isPassword && password == null) password = node.autofillId
        if (isUsername && username == null) username = node.autofillId
        repeat(node.childCount) { visit(node.getChildAt(it)) }
    }

    repeat(structure.windowNodeCount) { index -> visit(structure.getWindowNodeAt(index).rootViewNode) }
    return DetectedAutofillFields(username, password)
}
