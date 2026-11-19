package com.sdu.paypals.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Preserves unsaved full-name and email edits across configuration changes and process death.
 * Passwords are intentionally excluded from [SavedStateHandle] serialization for security.
 */
class ProfileViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    companion object {
        internal const val KEY_POPULATED = "profile_populated"
        internal const val KEY_FULL_NAME = "profile_draft_full_name"
        internal const val KEY_EMAIL = "profile_draft_email"
    }

    val draftFullName: StateFlow<String> = savedStateHandle.getStateFlow(KEY_FULL_NAME, "")
    val draftEmail: StateFlow<String> = savedStateHandle.getStateFlow(KEY_EMAIL, "")

    fun populateFromUserIfNeeded(displayName: String, email: String): Boolean {
        if (savedStateHandle.get<Boolean>(KEY_POPULATED) == true) return false
        savedStateHandle[KEY_POPULATED] = true
        savedStateHandle[KEY_FULL_NAME] = displayName
        savedStateHandle[KEY_EMAIL] = email
        return true
    }

    fun updateDraftFullName(value: String) {
        savedStateHandle[KEY_FULL_NAME] = value
    }

    fun updateDraftEmail(value: String) {
        savedStateHandle[KEY_EMAIL] = value
    }
}
