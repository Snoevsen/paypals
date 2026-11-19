package com.sdu.paypals.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.sdu.paypals.ActivityFragment
import kotlinx.coroutines.flow.StateFlow

/**
 * Preserves search query, category filter strategy, sort strategy, and card display limit
 * across configuration changes and OS process death via [SavedStateHandle].
 */
class ActivityFilterViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    companion object {
        internal const val KEY_SEARCH_QUERY = "activity_search_query"
        internal const val KEY_FILTER_MODE = "activity_filter_mode"
        internal const val KEY_SORT_MODE = "activity_sort_mode"
        internal const val KEY_MAX_LIMIT = "activity_max_limit"
    }

    val searchQuery: StateFlow<String> = savedStateHandle.getStateFlow(KEY_SEARCH_QUERY, "")
    val filterModeName: StateFlow<String> = savedStateHandle.getStateFlow(
        KEY_FILTER_MODE,
        ActivityFragment.FilterMode.ALL_ACTIVITIES.name
    )
    val sortModeName: StateFlow<String> = savedStateHandle.getStateFlow(
        KEY_SORT_MODE,
        ActivityFragment.SortMode.NEWEST_FIRST.name
    )
    val maxCardsLimit: StateFlow<Int> = savedStateHandle.getStateFlow(KEY_MAX_LIMIT, 10)

    fun currentFilterMode(): ActivityFragment.FilterMode =
        runCatching { ActivityFragment.FilterMode.valueOf(filterModeName.value) }
            .getOrDefault(ActivityFragment.FilterMode.ALL_ACTIVITIES)

    fun currentSortMode(): ActivityFragment.SortMode =
        runCatching { ActivityFragment.SortMode.valueOf(sortModeName.value) }
            .getOrDefault(ActivityFragment.SortMode.NEWEST_FIRST)

    fun updateSearchQuery(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    fun setFilterMode(mode: ActivityFragment.FilterMode) {
        savedStateHandle[KEY_FILTER_MODE] = mode.name
    }

    fun cycleSortMode() {
        savedStateHandle[KEY_SORT_MODE] = currentSortMode().next().name
    }

    fun toggleCardLimit() {
        savedStateHandle[KEY_MAX_LIMIT] = if (maxCardsLimit.value == 10) 25 else 10
    }
}
