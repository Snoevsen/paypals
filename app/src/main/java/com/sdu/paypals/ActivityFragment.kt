package com.sdu.paypals

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.sdu.paypals.adapter.ActivityAdapter
import com.sdu.paypals.databinding.FragmentActivityBinding
import com.sdu.paypals.model.ActivityCategory
import com.sdu.paypals.model.UserActivityItem
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.util.bindSavedText
import com.sdu.paypals.util.collectOnStarted
import com.sdu.paypals.util.openGroupChat
import com.sdu.paypals.viewmodel.ActivityFilterViewModel
import java.util.Locale

class ActivityFragment : Fragment() {

    private var _binding: FragmentActivityBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ActivityFilterViewModel by viewModels()
    private lateinit var activityAdapter: ActivityAdapter
    private var removeFeedListener: (() -> Unit)? = null

    private var allItems: List<UserActivityItem> = emptyList()

    /**
     * Strategy Pattern: Each [FilterMode] encapsulates its summary title and matching predicate.
     */
    enum class FilterMode(
        val summaryTitle: String,
        val predicate: (UserActivityItem) -> Boolean
    ) {
        ALL_ACTIVITIES("All Activities", { true }),
        EXPENSES_PAID_BY_ME("Expenses (What You Paid)", { it.category == ActivityCategory.EXPENSE_PAID_BY_ME }),
        PAID_TO_ME("Paid to You (Received)", { it.category == ActivityCategory.PAID_TO_ME }),
        REMINDERS("Reminders", { it.category == ActivityCategory.REMINDER })
    }

    /**
     * Strategy Pattern: Each [SortMode] encapsulates its button label and sort comparator.
     */
    enum class SortMode(
        val label: String,
        val comparator: Comparator<UserActivityItem>
    ) {
        NEWEST_FIRST("Newest ↓", compareByDescending { it.timestamp }),
        OLDEST_FIRST("Oldest ↑", compareBy { it.timestamp }),
        AMOUNT_DESC("Amount ↓", compareByDescending { it.amount });

        fun next(): SortMode = entries[(ordinal + 1) % entries.size]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupControls()
        observeSavedFilterState()

        val uid = PayPalsRepository.currentUserId()
        if (uid != null) {
            removeFeedListener = PayPalsRepository.observeUserActivityFeed(uid) { items ->
                if (_binding == null) return@observeUserActivityFeed
                allItems = items
                applyFilterSearchAndSort()
            }
        }
    }

    private fun setupRecyclerView() {
        activityAdapter = ActivityAdapter { item ->
            if (item.groupId.isNotBlank()) {
                openGroupChat(item.groupId)
            }
        }
        binding.rvActivities.layoutManager = LinearLayoutManager(requireContext())
        binding.rvActivities.adapter = activityAdapter
    }

    private fun setupControls() {
        binding.etSearchActivity.bindSavedText(
            viewLifecycleOwner,
            viewModel.searchQuery,
            viewModel::updateSearchQuery
        )

        // Standard: All Activities
        binding.btnFilterAll.setOnClickListener { viewModel.setFilterMode(FilterMode.ALL_ACTIVITIES) }

        // Expenses button toggles between user-paid expenses and received payments
        binding.btnFilterExpenses.setOnClickListener {
            val nextMode = if (viewModel.currentFilterMode() == FilterMode.EXPENSES_PAID_BY_ME) {
                FilterMode.PAID_TO_ME
            } else {
                FilterMode.EXPENSES_PAID_BY_ME
            }
            viewModel.setFilterMode(nextMode)
        }

        // Reminders button
        binding.btnFilterReminders.setOnClickListener { viewModel.setFilterMode(FilterMode.REMINDERS) }

        // Chronological / Amount Sort toggle
        binding.btnSortOrder.setOnClickListener { viewModel.cycleSortMode() }

        // 10 up to 25 cards toggle
        binding.btnCardLimit.setOnClickListener { viewModel.toggleCardLimit() }
    }

    private fun observeSavedFilterState() {
        viewLifecycleOwner.collectOnStarted(viewModel.searchQuery) { applyFilterSearchAndSort() }
        viewLifecycleOwner.collectOnStarted(viewModel.filterModeName) {
            updateFilterButtonsVisualState()
            applyFilterSearchAndSort()
        }
        viewLifecycleOwner.collectOnStarted(viewModel.sortModeName) {
            binding.btnSortOrder.text = viewModel.currentSortMode().label
            applyFilterSearchAndSort()
        }
        viewLifecycleOwner.collectOnStarted(viewModel.maxCardsLimit) { limit ->
            binding.btnCardLimit.text = "Max: $limit"
            applyFilterSearchAndSort()
        }
    }

    private fun updateFilterButtonsVisualState() {
        val currentFilterMode = viewModel.currentFilterMode()
        val primaryContainer = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorPrimaryContainer
        )
        val transparent = android.graphics.Color.TRANSPARENT

        fun styleButton(btn: MaterialButton, active: Boolean) {
            btn.backgroundTintList = ColorStateList.valueOf(if (active) primaryContainer else transparent)
        }

        styleButton(binding.btnFilterAll, currentFilterMode == FilterMode.ALL_ACTIVITIES)
        styleButton(binding.btnFilterExpenses, currentFilterMode == FilterMode.EXPENSES_PAID_BY_ME)
        styleButton(binding.btnFilterReminders, currentFilterMode == FilterMode.REMINDERS)

        binding.btnFilterExpenses.text = when (currentFilterMode) {
            FilterMode.EXPENSES_PAID_BY_ME -> "Expenses (You Paid)"
            FilterMode.PAID_TO_ME -> "Paid to You"
            else -> "Expenses"
        }
    }

    private fun applyFilterSearchAndSort() {
        if (_binding == null) return

        val filterMode = viewModel.currentFilterMode()
        val sortMode = viewModel.currentSortMode()
        val limit = viewModel.maxCardsLimit.value
        val filtered = filterAndSortActivities(
            items = allItems,
            filterMode = filterMode,
            query = viewModel.searchQuery.value,
            sortMode = sortMode,
            limit = limit
        )

        binding.tvActiveFilterSummary.text =
            "${filterMode.summaryTitle} (${filtered.size} shown, max $limit)"

        activityAdapter.submitList(filtered)
        binding.layoutEmptyActivity.visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    companion object {
        fun filterAndSortActivities(
            items: List<UserActivityItem>,
            filterMode: FilterMode,
            query: String,
            sortMode: SortMode,
            limit: Int
        ): List<UserActivityItem> {
            val categoryFiltered = items.filter(filterMode.predicate)

            val cleanQuery = query.trim().lowercase(Locale.ROOT)
            val searchFiltered = if (cleanQuery.isEmpty()) {
                categoryFiltered
            } else {
                categoryFiltered.filter { item ->
                    val formattedTime = ActivityAdapter.formatTimestampForDisplay(item.timestamp)
                        .lowercase(Locale.ROOT)
                    val formattedAmount = PayPalsRepository.formatAmount(item.amount)
                        .lowercase(Locale.ROOT)
                    val plainAmount = String.format(Locale.US, "%.2f", item.amount)

                    item.payerName.lowercase(Locale.ROOT).contains(cleanQuery) ||
                        item.description.lowercase(Locale.ROOT).contains(cleanQuery) ||
                        item.groupName.lowercase(Locale.ROOT).contains(cleanQuery) ||
                        formattedTime.contains(cleanQuery) ||
                        formattedAmount.contains(cleanQuery) ||
                        plainAmount.contains(cleanQuery)
                }
            }

            return searchFiltered
                .sortedWith(sortMode.comparator)
                .take(limit.coerceIn(10, 25))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        removeFeedListener?.invoke()
        removeFeedListener = null
        _binding = null
    }
}
