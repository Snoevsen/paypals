package com.sdu.paypals.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.sdu.paypals.model.GroupIcon
import kotlinx.coroutines.flow.StateFlow

/**
 * Preserves new group creation form inputs and selected icon across configuration changes
 * and background OS process death via [SavedStateHandle].
 */
class CreateGroupViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    companion object {
        internal const val KEY_GROUP_NAME = "create_group_name"
        internal const val KEY_GROUP_DESC = "create_group_desc"
        internal const val KEY_ICON_KEY = "create_group_icon_key"
    }

    val groupName: StateFlow<String> = savedStateHandle.getStateFlow(KEY_GROUP_NAME, "")
    val groupDescription: StateFlow<String> = savedStateHandle.getStateFlow(KEY_GROUP_DESC, "")
    val selectedIconKey: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_ICON_KEY, GroupIcon.GROUPS.key)

    fun updateGroupName(name: String) {
        savedStateHandle[KEY_GROUP_NAME] = name
    }

    fun updateGroupDescription(description: String) {
        savedStateHandle[KEY_GROUP_DESC] = description
    }

    fun updateSelectedIconKey(iconKey: String) {
        savedStateHandle[KEY_ICON_KEY] = iconKey
    }

    fun clearDraft() {
        savedStateHandle[KEY_GROUP_NAME] = ""
        savedStateHandle[KEY_GROUP_DESC] = ""
        savedStateHandle[KEY_ICON_KEY] = GroupIcon.GROUPS.key
    }
}
