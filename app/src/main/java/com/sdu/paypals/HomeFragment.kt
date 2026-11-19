package com.sdu.paypals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.sdu.paypals.adapter.ActivityAdapter
import com.sdu.paypals.adapter.GroupListAdapter
import com.sdu.paypals.databinding.FragmentHomeBinding
import com.sdu.paypals.model.UserBalanceSummary
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.util.openGroupChat

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeGroupAdapter: GroupListAdapter
    private lateinit var homeActivityAdapter: ActivityAdapter

    private var groupsListener: ListenerRegistration? = null
    private var removeBalanceListener: (() -> Unit)? = null
    private var removeActivityListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateGreetingInAppBar()
        setupAdapters()
        setupListeners()
        observeDashboardData()

        PayPalsRepository.syncCurrentUserProfile {
            if (_binding != null && isAdded) {
                updateGreetingInAppBar()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateGreetingInAppBar()
    }

    private fun updateGreetingInAppBar() {
        val profileName = PayPalsRepository.currentUserName()
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Hi, $profileName!"
    }

    private fun setupAdapters() {
        homeGroupAdapter = GroupListAdapter(
            currentUserId = { PayPalsRepository.currentUserId() },
            onGroupClick = { group -> openGroupChat(group.id) }
        )
        binding.rvHomeGroups.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHomeGroups.adapter = homeGroupAdapter

        homeActivityAdapter = ActivityAdapter { item ->
            if (item.groupId.isNotBlank()) {
                openGroupChat(item.groupId)
            }
        }
        binding.rvHomeActivities.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHomeActivities.adapter = homeActivityAdapter
    }

    private fun setupListeners() {
        // "+ New group" redirects into GroupsFragment and activates the FAB create-group action
        binding.tvHomeNewGroup.setOnClickListener {
            val args = Bundle().apply { putBoolean("openCreateGroup", true) }
            val mainAct = activity as? MainActivity
            if (mainAct != null) {
                mainAct.navigateToBottomBarDestination(R.id.groupsFragment, args)
            } else {
                findNavController().navigate(R.id.groupsFragment, args)
            }
        }

        // "See all" redirects into ActivityFragment
        binding.tvHomeSeeAllActivities.setOnClickListener {
            val mainAct = activity as? MainActivity
            if (mainAct != null) {
                mainAct.navigateToBottomBarDestination(R.id.activityFragment)
            } else {
                findNavController().navigate(R.id.activityFragment)
            }
        }
    }

    private fun observeDashboardData() {
        val uid = PayPalsRepository.currentUserId() ?: return

        // 1. Total Owed & Owes Card
        removeBalanceListener?.invoke()
        removeBalanceListener = PayPalsRepository.observeTotalUserBalance(uid) { balance ->
            if (_binding == null) return@observeTotalUserBalance
            bindBalanceCard(balance)
        }

        // 2. Top 3 Groups (sorted by lastActivityAt desc, same as GroupsFragment)
        groupsListener?.remove()
        groupsListener = PayPalsRepository.observeUserGroups(
            userId = uid,
            onUpdate = { groups ->
                if (_binding == null) return@observeUserGroups
                val topThree = groups.take(3)
                homeGroupAdapter.submitList(topThree)
                binding.tvHomeEmptyGroups.visibility =
                    if (topThree.isEmpty()) View.VISIBLE else View.GONE
            },
            onError = { _ -> }
        )

        // 3. Latest 5 Activities
        removeActivityListener?.invoke()
        removeActivityListener = PayPalsRepository.observeUserActivityFeed(uid) { items ->
            if (_binding == null) return@observeUserActivityFeed
            val latestFive = items.sortedByDescending { it.timestamp }.take(5)
            homeActivityAdapter.submitList(latestFive)
            binding.tvHomeEmptyActivities.visibility =
                if (latestFive.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun bindBalanceCard(balance: UserBalanceSummary) {
        binding.tvHomeTotalOwedToMe.text = PayPalsRepository.formatAmount(balance.totalOwedToMe)
        binding.tvHomeTotalIOwe.text = PayPalsRepository.formatAmount(balance.totalIOwe)
        binding.tvHomeBalanceSummary.text =
            balance.formatSummaryText(inGroup = false, PayPalsRepository::formatAmount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        groupsListener?.remove()
        groupsListener = null
        removeBalanceListener?.invoke()
        removeBalanceListener = null
        removeActivityListener?.invoke()
        removeActivityListener = null
        _binding = null
    }
}
