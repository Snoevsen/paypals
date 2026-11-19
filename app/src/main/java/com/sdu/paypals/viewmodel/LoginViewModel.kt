package com.sdu.paypals.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Preserves authentication form mode and non-sensitive inputs across configuration changes,
 * system interruptions, and OS process death via [SavedStateHandle].
 */
class LoginViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    companion object {
        internal const val KEY_SIGN_UP_MODE = "login_sign_up_mode"
        internal const val KEY_FULL_NAME = "login_full_name"
        internal const val KEY_EMAIL = "login_email"
    }

    val isSignUpMode: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_SIGN_UP_MODE, false)
    val fullName: StateFlow<String> = savedStateHandle.getStateFlow(KEY_FULL_NAME, "")
    val email: StateFlow<String> = savedStateHandle.getStateFlow(KEY_EMAIL, "")

    fun setSignUpMode(signUp: Boolean) {
        savedStateHandle[KEY_SIGN_UP_MODE] = signUp
    }

    fun updateFullName(value: String) {
        savedStateHandle[KEY_FULL_NAME] = value
    }

    fun updateEmail(value: String) {
        savedStateHandle[KEY_EMAIL] = value
    }

    fun clearState() {
        savedStateHandle[KEY_SIGN_UP_MODE] = false
        savedStateHandle[KEY_FULL_NAME] = ""
        savedStateHandle[KEY_EMAIL] = ""
    }
}
