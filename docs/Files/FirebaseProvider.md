---
tags:
  - paypals
  - repository
  - firebase
  - pattern-observer
package: com.sdu.paypals.repository
source_path: app/src/main/java/com/sdu/paypals/repository/FirebaseProvider.kt
aliases:
  - FirebaseProvider.kt
---

# FirebaseProvider

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/repository/FirebaseProvider.kt`  
> Internal singleton providing configured `FirebaseAuth` and `FirebaseFirestore` instances (with `PersistentCacheSettings` offline persistence enabled) plus reusable **Observer / Composite Observer** helpers.

## Provided Infrastructure & Patterns
- **`auth` & `db`**: Lazy accessors enabling persistent offline caching on `FirebaseFirestore.getInstance()`.
- **`observeQuery(...)` & `observeDocument(...)`**: Reusable `MetadataChanges.INCLUDE` snapshot listener wrappers handling errors and mapping results cleanly.
- **`observeUserGroupPayments(userId, onUpdate)` (Composite Observer)**: Watches the user's `groups` query and dynamically subscribes/unsubscribes nested per-group `payments` subcollection listeners as group memberships change, returning a single composite `ListenerRegistration`.

## Links & Dependencies
- **Used by**: [[UserProfileRepository]] · [[GroupRepository]] · [[PaymentRepository]] · [[ActivityAndBalanceRepository]]
- **External Config**: [[01 - Build, Config & CI-CD#google-services.json|google-services.json]]
