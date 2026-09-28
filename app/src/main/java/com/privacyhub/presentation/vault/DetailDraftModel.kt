package com.privacyhub.presentation.vault

import androidx.lifecycle.ViewModel

/** Memory-only edits survive rotation, but are never placed in Android saved-state Bundles. */
class DetailDraftModel : ViewModel() {
    var draft: DetailDraft? = null
}

data class DetailDraft(
    val title: String,
    val content: String,
    val category: String,
    val note: String,
    val accessCode: String?
)
