package com.sdu.paypals.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.sdu.paypals.model.PaymentRequest

/**
 * Provides shared [FirebaseAuth] and [FirebaseFirestore] instances with offline persistent cache
 * enabled, plus reusable Observer helpers for real-time Firestore queries and composite group
 * payment streams.
 */
internal object FirebaseProvider {

    private var persistenceConfigured = false

    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    val db: FirebaseFirestore
        get() {
            val instance = FirebaseFirestore.getInstance()
            if (!persistenceConfigured) {
                persistenceConfigured = true
                try {
                    val settings = FirebaseFirestoreSettings.Builder(instance.firestoreSettings)
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                    instance.firestoreSettings = settings
                } catch (_: Exception) {
                    // Firestore settings already initialized
                }
            }
            return instance
        }

    fun <T> observeQuery(
        query: Query,
        metadataChanges: MetadataChanges = MetadataChanges.INCLUDE,
        onError: (Exception) -> Unit = {},
        mapper: (QuerySnapshot?) -> T,
        onUpdate: (T) -> Unit
    ): ListenerRegistration {
        return query.addSnapshotListener(metadataChanges) { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onUpdate(mapper(snapshot))
        }
    }

    fun <T> observeDocument(
        docRef: DocumentReference,
        metadataChanges: MetadataChanges = MetadataChanges.INCLUDE,
        onError: (Exception) -> Unit = {},
        mapper: (DocumentSnapshot?) -> T,
        onUpdate: (T) -> Unit
    ): ListenerRegistration {
        return docRef.addSnapshotListener(metadataChanges) { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            onUpdate(mapper(snapshot))
        }
    }

    /**
     * Composite Observer that watches all groups belonging to [userId] and automatically manages
     * per-group `payments` subcollection listeners as memberships are added or removed.
     */
    fun observeUserGroupPayments(
        userId: String,
        onUpdate: (Map<String, List<PaymentRequest>>) -> Unit
    ): ListenerRegistration {
        val groupPaymentListeners = mutableMapOf<String, ListenerRegistration>()
        val paymentsByGroup = mutableMapOf<String, List<PaymentRequest>>()

        val groupsReg = db.collection("groups")
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                val currentGroupIds = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()

                val removedIds = groupPaymentListeners.keys - currentGroupIds
                for (remId in removedIds) {
                    groupPaymentListeners.remove(remId)?.remove()
                    paymentsByGroup.remove(remId)
                }

                for (groupId in currentGroupIds) {
                    if (!groupPaymentListeners.containsKey(groupId)) {
                        val reg = db.collection("groups").document(groupId)
                            .collection("payments")
                            .addSnapshotListener(MetadataChanges.INCLUDE) { paySnap, _ ->
                                val list = paySnap?.documents
                                    ?.mapNotNull { PaymentRequest.fromDocument(it) }
                                    ?: emptyList()
                                paymentsByGroup[groupId] = list
                                onUpdate(paymentsByGroup.toMap())
                            }
                        groupPaymentListeners[groupId] = reg
                    }
                }
                onUpdate(paymentsByGroup.toMap())
            }

        return ListenerRegistration {
            groupsReg.remove()
            groupPaymentListeners.values.forEach { it.remove() }
            groupPaymentListeners.clear()
        }
    }
}
