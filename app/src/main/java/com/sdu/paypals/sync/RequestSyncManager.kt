package com.sdu.paypals.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.sdu.paypals.util.NotificationHelper
import java.util.concurrent.CopyOnWriteArrayList

enum class RequestState {
    PENDING,
    SUCCESS,
    FAILED,
    FAILED_RETRYING_IN_BACKGROUND,
    BACKGROUND_RETRY_SUCCEEDED
}

data class RequestStatusEvent(
    val state: RequestState,
    val actionLabel: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Manages Fault Tolerance, Offline Availability, Background Retries, and User Feedback
 * for all network requests in PayPals.
 */
object RequestSyncManager {

    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private val listeners = CopyOnWriteArrayList<(RequestStatusEvent) -> Unit>()
    private val connectivityListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    @Volatile
    var isNetworkAvailable: Boolean = true
        private set

    private var appContext: Context? = null
    private var networkCallbackRegistered = false

    private val pendingBackgroundQueue = PendingWriteQueue<() -> Task<*>>()

    fun pendingRetryCount(): Int = pendingBackgroundQueue.count()

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        updateCurrentNetworkState(ctx)

        if (!networkCallbackRegistered) {
            networkCallbackRegistered = true
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            try {
                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        isNetworkAvailable = true
                        notifyConnectivityChanged(true)
                        processBackgroundRetries()
                    }

                    override fun onLost(network: Network) {
                        updateCurrentNetworkState(ctx)
                        notifyConnectivityChanged(isNetworkAvailable)
                    }
                })
            } catch (_: Exception) {
                // Ignore if security/registration fails in test environment
            }
        }
    }

    private fun updateCurrentNetworkState(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNet = cm.activeNetwork
            val caps = if (activeNet != null) cm.getNetworkCapabilities(activeNet) else null
            isNetworkAvailable = caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            isNetworkAvailable = true
        }
    }

    fun addStatusListener(listener: (RequestStatusEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeStatusListener(listener: (RequestStatusEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun addConnectivityListener(listener: (Boolean) -> Unit) {
        connectivityListeners.add(listener)
        mainHandler.post { listener(isNetworkAvailable) }
    }

    fun removeConnectivityListener(listener: (Boolean) -> Unit) {
        connectivityListeners.remove(listener)
    }

    private fun notifyConnectivityChanged(online: Boolean) {
        mainHandler.post {
            for (listener in connectivityListeners) {
                listener(online)
            }
        }
    }

    fun emitStatus(state: RequestState, actionLabel: String, message: String) {
        val event = RequestStatusEvent(state = state, actionLabel = actionLabel, message = message)
        mainHandler.post {
            for (listener in listeners) {
                listener(event)
            }
        }
    }

    /**
     * Executes a Firestore write task with fault tolerance:
     * 1. Emits PENDING immediately.
     * 2. If offline or if the network request does not complete quickly, queues it for background
     *    retry and immediately returns `optimisticResult` so the user is NEVER blocked from
     *    continuing to use the app.
     * 3. When the request eventually succeeds in the background, notifies the user via both
     *    an in-app status event and a system notification.
     */
    fun <T> executeWriteTask(
        actionLabel: String,
        optimisticResult: T,
        taskProvider: () -> Task<*>,
        onImmediateResult: (Result<T>) -> Unit
    ) {
        emitStatus(RequestState.PENDING, actionLabel, "Pending: $actionLabel...")

        val requestId = "${actionLabel}_${System.currentTimeMillis()}"
        var uiCallbackInvoked = false
        var queuedForBackground = false

        val task = try {
            taskProvider()
        } catch (e: Exception) {
            emitStatus(RequestState.FAILED, actionLabel, "Failed: ${e.localizedMessage ?: actionLabel}")
            onImmediateResult(Result.failure(e))
            return
        }

        if (!isNetworkAvailable) {
            uiCallbackInvoked = true
            queuedForBackground = true
            pendingBackgroundQueue.enqueueIfAbsent(requestId, actionLabel, null)
            emitStatus(
                RequestState.FAILED_RETRYING_IN_BACKGROUND,
                actionLabel,
                "Offline — '$actionLabel' failed to reach server and will retry in background."
            )
            onImmediateResult(Result.success(optimisticResult))
        } else {
            mainHandler.postDelayed({
                if (!uiCallbackInvoked && !task.isComplete) {
                    uiCallbackInvoked = true
                    queuedForBackground = true
                    pendingBackgroundQueue.enqueueIfAbsent(requestId, actionLabel, null)
                    emitStatus(
                        RequestState.FAILED_RETRYING_IN_BACKGROUND,
                        actionLabel,
                        "Network slow/unavailable — '$actionLabel' retrying in background."
                    )
                    onImmediateResult(Result.success(optimisticResult))
                }
            }, 2500L)
        }

        task.addOnCompleteListener { completedTask ->
            if (completedTask.isSuccessful) {
                pendingBackgroundQueue.removeById(requestId)
                if (!uiCallbackInvoked) {
                    uiCallbackInvoked = true
                    emitStatus(RequestState.SUCCESS, actionLabel, "Success: $actionLabel")
                    onImmediateResult(Result.success(optimisticResult))
                } else if (queuedForBackground) {
                    notifyBackgroundSuccess(actionLabel)
                }
            } else {
                val ex = completedTask.exception
                if (PendingWriteQueue.isTransientNetworkFailure(isNetworkAvailable, ex)) {
                    pendingBackgroundQueue.enqueueIfAbsent(requestId, actionLabel, taskProvider)
                    if (!uiCallbackInvoked) {
                        uiCallbackInvoked = true
                        emitStatus(
                            RequestState.FAILED_RETRYING_IN_BACKGROUND,
                            actionLabel,
                            "Temporary network error — '$actionLabel' will retry in background."
                        )
                        onImmediateResult(Result.success(optimisticResult))
                    }
                } else {
                    pendingBackgroundQueue.removeById(requestId)
                    val errMsg = ex?.localizedMessage ?: "Request failed"
                    emitStatus(RequestState.FAILED, actionLabel, "Failed ($actionLabel): $errMsg")
                    if (!uiCallbackInvoked) {
                        uiCallbackInvoked = true
                        onImmediateResult(Result.failure(ex ?: IllegalStateException(errMsg)))
                    }
                }
            }
        }
    }

    private fun notifyBackgroundSuccess(actionLabel: String) {
        emitStatus(
            RequestState.BACKGROUND_RETRY_SUCCEEDED,
            actionLabel,
            "Previously failed request succeeded in background: $actionLabel"
        )
        appContext?.let { ctx ->
            NotificationHelper.showBackgroundSyncNotification(ctx, actionLabel)
        }
    }

    /**
     * Triggered automatically when network connectivity is restored.
     * Flushes Firestore pending writes and retries any failed tasks in the background.
     */
    private fun processBackgroundRetries() {
        val snapshot = pendingBackgroundQueue.snapshot()
        if (snapshot.isEmpty()) return

        for (item in snapshot) {
            val retryProvider = item.payload
            if (retryProvider != null) {
                try {
                    retryProvider().addOnSuccessListener {
                        if (pendingBackgroundQueue.markCompleted(item)) {
                            notifyBackgroundSuccess(item.actionLabel)
                        }
                    }
                } catch (_: Exception) {
                    // Will retry on next connectivity check
                }
            } else {
                try {
                    FirebaseFirestore.getInstance().waitForPendingWrites()
                        .addOnSuccessListener {
                            if (pendingBackgroundQueue.markCompleted(item)) {
                                notifyBackgroundSuccess(item.actionLabel)
                            }
                        }
                } catch (_: Exception) {
                }
            }
        }
    }
}
