package com.privacyhub.presentation.transfer

import androidx.lifecycle.ViewModel

/** Configuration-only recovery; cancelling or submitting clears the memory draft. */
class AddContentDraft : ViewModel() {
    var text = ""
    var open = false
    fun clear() { text = ""; open = false }
}
