package com.sdu.paypals

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.sdu.paypals.databinding.ActivityMainBinding
import com.sdu.paypals.repository.PayPalsRepository
import com.sdu.paypals.sync.RequestState
import com.sdu.paypals.sync.RequestStatusEvent
import com.sdu.paypals.sync.RequestSyncManager
import com.sdu.paypals.util.NotificationHelper
import com.sdu.paypals.util.handleUnfocusOutsideEditText

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var nudgeListener: ListenerRegistration? = null
    private var observedUid: String? = null
    private val bannerHideHandler = Handler(Looper.getMainLooper())
    private var hideBannerRunnable: Runnable? = null

    private val syncStatusListener: (RequestStatusEvent) -> Unit = { event ->
        updateStatusBannerForEvent(event)
    }

    private val connectivityListener: (Boolean) -> Unit = { isOnline ->
        updateStatusBannerForConnectivity(isOnline)
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createNotificationChannel(this)
        RequestSyncManager.init(this)
        RequestSyncManager.addStatusListener(syncStatusListener)
        RequestSyncManager.addConnectivityListener(connectivityListener)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }

        setSupportActionBar(binding.toolbar)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        navController = navHostFragment.navController

        // Dynamically choose start destination based on whether user is logged in
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            navGraph.setStartDestination(R.id.homeFragment)
            PayPalsRepository.syncCurrentUserProfile()
        } else {
            navGraph.setStartDestination(R.id.loginFragment)
        }
        navController.setGraph(navGraph, null)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.groupsFragment,
                R.id.activityFragment,
                R.id.profileFragment
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        setupBottomNavigation()

        // Show/hide toolbar and bottom navigation & sync active bottom bar tab
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.loginFragment -> {
                    binding.appBarLayout.visibility = View.GONE
                    binding.contentMain.bottomNavigation.visibility = View.GONE
                    stopNudgeListener()
                }
                else -> {
                    binding.appBarLayout.visibility = View.VISIBLE
                    binding.contentMain.bottomNavigation.visibility = View.VISIBLE
                    syncBottomNavigationSelection(destination.id)
                    ensureNudgeListenerStarted()
                }
            }
        }

        handleIncomingIntent(intent)
    }

    private fun setupBottomNavigation() {
        val bottomNav = binding.contentMain.bottomNavigation

        bottomNav.setOnItemSelectedListener { item ->
            navigateToBottomBarDestination(item.itemId)
        }

        bottomNav.setOnItemReselectedListener { item ->
            if (navController.currentDestination?.id != item.itemId) {
                navigateToBottomBarDestination(item.itemId)
            }
        }
    }

    fun navigateToBottomBarDestination(destinationId: Int, args: Bundle? = null): Boolean {
        if (args == null && navController.currentDestination?.id == destinationId) {
            return true
        }
        // If destination is already in the back stack and no custom args are passed, pop back to it
        if (args == null && navController.popBackStack(destinationId, false)) {
            return true
        }
        val startDestId = navController.graph.findStartDestination().id
        val navOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(false)
            .setPopUpTo(startDestId, inclusive = false, saveState = false)
            .build()
        return try {
            navController.navigate(destinationId, args, navOptions)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun syncBottomNavigationSelection(destinationId: Int) {
        val menuId = when (destinationId) {
            R.id.homeFragment -> R.id.homeFragment
            R.id.groupsFragment,
            R.id.groupChatFragment,
            R.id.groupBalanceFragment,
            R.id.groupSettingsFragment,
            R.id.createGroupFragment -> R.id.groupsFragment
            R.id.activityFragment -> R.id.activityFragment
            R.id.profileFragment -> R.id.profileFragment
            else -> return
        }
        binding.contentMain.bottomNavigation.menu.findItem(menuId)?.isChecked = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(incomingIntent: Intent?) {
        if (incomingIntent == null || FirebaseAuth.getInstance().currentUser == null) return

        // 1. Check notification tap
        val openGroupId = incomingIntent.getStringExtra("open_group_id")
        if (!openGroupId.isNullOrBlank()) {
            navController.navigate(
                R.id.groupChatFragment,
                Bundle().apply { putString("groupId", openGroupId) }
            )
            return
        }

        // 2. Check QR code deep link (paypals://join?groupId=...)
        val dataUri = incomingIntent.data
        if (dataUri != null && dataUri.scheme == "paypals" && dataUri.host == "join") {
            val qrGroupId = dataUri.getQueryParameter("groupId")
            if (!qrGroupId.isNullOrBlank()) {
                PayPalsRepository.joinGroupById(qrGroupId) { res ->
                    res.onSuccess { joinedGroup ->
                        navController.navigate(
                            R.id.groupChatFragment,
                            Bundle().apply { putString("groupId", joinedGroup.id) }
                        )
                    }.onFailure { e ->
                        Snackbar.make(
                            binding.root,
                            e.localizedMessage ?: "Could not join group from QR code",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * Allows tapping outside any focused EditText / textfield anywhere in the app
     * to clear focus and hide the soft keyboard.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        handleUnfocusOutsideEditText(ev, currentFocus)
        return super.dispatchTouchEvent(ev)
    }

    private fun ensureNudgeListenerStarted() {
        val uid = PayPalsRepository.currentUserId() ?: return
        if (observedUid == uid && nudgeListener != null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        nudgeListener?.remove()
        observedUid = uid
        nudgeListener = PayPalsRepository.observeIncomingNudges(uid) { nudge ->
            NotificationHelper.showNudgeNotification(this, nudge)
            val formatted = PayPalsRepository.formatAmount(nudge.amountOwed)
            Snackbar.make(
                binding.root,
                "Nudge from ${nudge.senderName}: You owe $formatted for \"${nudge.paymentDescription}\" in ${nudge.groupName}",
                Snackbar.LENGTH_LONG
            ).setAction("Open") {
                navController.navigate(
                    R.id.groupChatFragment,
                    Bundle().apply { putString("groupId", nudge.groupId) }
                )
            }.show()
        }
    }

    private fun updateStatusBannerForConnectivity(isOnline: Boolean) {
        val banner = binding.contentMain.tvRequestStatusBanner
        hideBannerRunnable?.let { bannerHideHandler.removeCallbacks(it) }
        if (!isOnline) {
            val pending = RequestSyncManager.pendingRetryCount()
            banner.visibility = View.VISIBLE
            banner.setBackgroundColor(Color.parseColor("#E65100"))
            banner.text = if (pending > 0) {
                "Offline mode — $pending request(s) queued to retry in background"
            } else {
                "Offline mode — Viewing cached data; new requests will retry automatically"
            }
        } else if (RequestSyncManager.pendingRetryCount() == 0) {
            banner.visibility = View.GONE
        }
    }

    private fun updateStatusBannerForEvent(event: RequestStatusEvent) {
        val banner = binding.contentMain.tvRequestStatusBanner
        hideBannerRunnable?.let { bannerHideHandler.removeCallbacks(it) }

        when (event.state) {
            RequestState.PENDING -> {
                banner.visibility = View.VISIBLE
                banner.setBackgroundColor(Color.parseColor("#1565C0"))
                banner.text = event.message
            }
            RequestState.SUCCESS -> {
                banner.visibility = View.VISIBLE
                banner.setBackgroundColor(Color.parseColor("#2E7D32"))
                banner.text = event.message
                scheduleBannerHide(2500L)
            }
            RequestState.FAILED -> {
                banner.visibility = View.VISIBLE
                banner.setBackgroundColor(Color.parseColor("#C62828"))
                banner.text = event.message
                scheduleBannerHide(4000L)
            }
            RequestState.FAILED_RETRYING_IN_BACKGROUND -> {
                banner.visibility = View.VISIBLE
                banner.setBackgroundColor(Color.parseColor("#EF6C00"))
                banner.text = "Pending background retry: ${event.actionLabel}"
                Snackbar.make(
                    binding.root,
                    event.message,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            RequestState.BACKGROUND_RETRY_SUCCEEDED -> {
                banner.visibility = View.VISIBLE
                banner.setBackgroundColor(Color.parseColor("#2E7D32"))
                banner.text = "Background retry succeeded: ${event.actionLabel}"
                Snackbar.make(
                    binding.root,
                    event.message,
                    Snackbar.LENGTH_LONG
                ).show()
                scheduleBannerHide(4000L)
            }
        }
    }

    private fun scheduleBannerHide(delayMs: Long) {
        val runnable = Runnable {
            if (RequestSyncManager.isNetworkAvailable) {
                binding.contentMain.tvRequestStatusBanner.visibility = View.GONE
            } else {
                updateStatusBannerForConnectivity(false)
            }
        }
        hideBannerRunnable = runnable
        bannerHideHandler.postDelayed(runnable, delayMs)
    }

    private fun stopNudgeListener() {
        nudgeListener?.remove()
        nudgeListener = null
        observedUid = null
    }

    override fun onDestroy() {
        super.onDestroy()
        hideBannerRunnable?.let { bannerHideHandler.removeCallbacks(it) }
        RequestSyncManager.removeStatusListener(syncStatusListener)
        RequestSyncManager.removeConnectivityListener(connectivityListener)
        stopNudgeListener()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
