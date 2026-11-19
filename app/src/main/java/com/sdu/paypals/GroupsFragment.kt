package com.sdu.paypals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ListenerRegistration
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sdu.paypals.adapter.GroupListAdapter
import com.sdu.paypals.databinding.FragmentGroupsBinding
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.util.openGroupChat
import com.sdu.paypals.util.showErrorSnackbar
import com.sdu.paypals.util.showSnackbar

class GroupsFragment : Fragment() {

    private var _binding: FragmentGroupsBinding? = null
    private val binding get() = _binding!!

    private lateinit var groupAdapter: GroupListAdapter
    private var groupsListener: ListenerRegistration? = null

    companion object {
        private const val MENU_ITEM_SCAN_QR = 2001
    }

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            val scannedGroupId = PayPalsRepository.extractGroupIdFromQr(contents)
            if (!scannedGroupId.isNullOrBlank()) {
                joinAndOpenScannedGroup(scannedGroupId)
            } else {
                _binding?.let { showSnackbar(it.root, "Invalid QR code") }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAppBarQrButton()
        setupRecyclerView()
        setupListeners()

        // If redirected from Home's "+ New group" button, trigger the FAB action immediately
        if (arguments?.getBoolean("openCreateGroup", false) == true) {
            arguments?.putBoolean("openCreateGroup", false)
            binding.root.post {
                if (_binding != null && isAdded) {
                    binding.fabCreateGroup.performClick()
                }
            }
        }

        PayPalsRepository.syncCurrentUserProfile {
            if (_binding != null) {
                observeMyGroups()
            }
        }
    }

    private fun setupAppBarQrButton() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                val item = menu.add(Menu.NONE, MENU_ITEM_SCAN_QR, Menu.NONE, "Scan Group QR")
                item.setIcon(R.drawable.ic_qr_code)
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return if (menuItem.itemId == MENU_ITEM_SCAN_QR) {
                    launchPortraitQrScanner()
                    true
                } else {
                    false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun launchPortraitQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan a PayPals Group QR Code to join directly")
            setBeepEnabled(false)
            captureActivity = PortraitCaptureActivity::class.java
            setOrientationLocked(true)
        }
        qrScanLauncher.launch(options)
    }

    private fun setupRecyclerView() {
        groupAdapter = GroupListAdapter(
            currentUserId = { PayPalsRepository.currentUserId() },
            onGroupClick = { group ->
                openGroupChat(group.id, R.id.action_groupsFragment_to_groupChatFragment)
            }
        )
        binding.rvGroups.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGroups.adapter = groupAdapter
    }

    private fun setupListeners() {
        binding.fabCreateGroup.setOnClickListener {
            findNavController().navigate(R.id.action_groupsFragment_to_createGroupFragment)
        }
    }

    private fun joinAndOpenScannedGroup(groupId: String) {
        if (_binding == null) return
        binding.progressBar.visibility = View.VISIBLE
        PayPalsRepository.joinGroupById(groupId) { result ->
            if (_binding == null) return@joinGroupById
            binding.progressBar.visibility = View.GONE
            result.onSuccess { joinedGroup ->
                openGroupChat(joinedGroup.id, R.id.action_groupsFragment_to_groupChatFragment)
            }.onFailure { e ->
                showErrorSnackbar(binding.root, e, "Could not join group from QR code")
            }
        }
    }

    private fun observeMyGroups() {
        val uid = PayPalsRepository.currentUserId() ?: return
        binding.progressBar.visibility = View.VISIBLE

        groupsListener?.remove()
        groupsListener = PayPalsRepository.observeUserGroups(
            userId = uid,
            onUpdate = { groups ->
                if (_binding == null) return@observeUserGroups
                binding.progressBar.visibility = View.GONE
                groupAdapter.submitList(groups)
                binding.layoutEmptyGroups.visibility =
                    if (groups.isEmpty()) View.VISIBLE else View.GONE
            },
            onError = { e ->
                if (_binding == null) return@observeUserGroups
                binding.progressBar.visibility = View.GONE
                showErrorSnackbar(binding.root, e, "Failed to load groups")
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        groupsListener?.remove()
        _binding = null
    }
}
