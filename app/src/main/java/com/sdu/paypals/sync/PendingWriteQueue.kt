package com.sdu.paypals.sync

import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pure JVM-testable background retry queue and transient-network classifier used by
 * [RequestSyncManager] to implement fault-tolerant non-blocking request dispatching.
 */
internal class PendingWriteQueue<P> {

    data class Item<P>(
        val id: String,
        val actionLabel: String,
        val payload: P?
    )

    private val items = CopyOnWriteArrayList<Item<P>>()

    fun count(): Int = items.size

    fun snapshot(): List<Item<P>> = items.toList()

    /**
     * Adds an operation to the pending queue if an item with [id] is not already queued.
     */
    fun enqueueIfAbsent(id: String, actionLabel: String, payload: P? = null): Boolean {
        if (items.any { it.id == id }) return false
        return items.add(Item(id = id, actionLabel = actionLabel, payload = payload))
    }

    fun removeById(id: String): Boolean = items.removeAll { it.id == id }

    fun markCompleted(item: Item<P>): Boolean = items.remove(item)

    companion object {
        /**
         * Classifies whether a write failure stems from offline state or a transient error code
         * (`UNAVAILABLE` / `DEADLINE_EXCEEDED`), making it eligible for background retry.
         */
        fun isTransientCodeName(isNetworkAvailable: Boolean, codeName: String?): Boolean {
            if (!isNetworkAvailable) return true
            return codeName == "UNAVAILABLE" || codeName == "DEADLINE_EXCEEDED"
        }

        /**
         * Determines whether a write failure stems from offline/transient network conditions
         * (eligible for background retry) rather than permanent permission/validation errors.
         */
        fun isTransientNetworkFailure(isNetworkAvailable: Boolean, error: Throwable?): Boolean {
            val codeName = (error as? FirebaseFirestoreException)?.code?.name
            return isTransientCodeName(isNetworkAvailable, codeName)
        }
    }
}
