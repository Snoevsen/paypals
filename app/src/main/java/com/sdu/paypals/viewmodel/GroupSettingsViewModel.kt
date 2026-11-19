package com.sdu.paypals.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.sdu.paypals.model.GroupChat
import com.sdu.paypals.model.GroupIcon
import kotlinx.coroutines.flow.StateFlow

/**
 * Preserves unsaved group metadata edits, icon selection, and member email drafts
 * across configuration changes and process death via [SavedStateHandle].
 */
class GroupSettingsViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    companion object {
        internal const val KEY_POPULATED_GROUP_ID = "settings_populated_group_id"
        internal const val KEY_DRAFT_NAME = "settings_draft_name"
        internal const val KEY_DRAFT_DESC = "settings_draft_desc"
        internal const val KEY_DRAFT_ICON_KEY = "settings_draft_icon_key"
        internal const val KEY_DRAFT_MEMBER_EMAIL = "settings_draft_member_email"
    }

    val populatedGroupId: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_POPULATED_GROUP_ID, "")
    val draftName: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_DRAFT_NAME, "")
    val draftDescription: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_DRAFT_DESC, "")
    val draftIconKey: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_DRAFT_ICON_KEY, GroupIcon.GROUPS.key)
    val draftMemberEmail: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_DRAFT_MEMBER_EMAIL, "")

    fun populateFromGroupIfNeeded(group: GroupChat): Boolean {
        if (populatedGroupId.value == group.id) return false
        savedStateHandle[KEY_POPULATED_GROUP_ID] = group.id
        savedStateHandle[KEY_DRAFT_NAME] = group.name
        savedStateHandle[KEY_DRAFT_DESC] = group.description
        savedStateHandle[KEY_DRAFT_ICON_KEY] = group.iconKey
        return true
    }

    fun updateDraftName(name: String) {
        savedStateHandle[KEY_DRAFT_NAME] = name
    }

    fun updateDraftDescription(description: String) {
        savedStateHandle[KEY_DRAFT_DESC] = description
    }

    fun updateDraftIconKey(iconKey: String) {
        savedStateHandle[KEY_DRAFT_ICON_KEY] = iconKey
    }

    fun updateDraftMemberEmail(email: String) {
        savedStateHandle[KEY_DRAFT_MEMBER_EMAIL] = email
    }

    fun clearMemberEmailDraft() {
        savedStateHandle[KEY_DRAFT_MEMBER_EMAIL] = ""
    }
}
