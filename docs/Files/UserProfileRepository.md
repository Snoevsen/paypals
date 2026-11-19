---
tags:
  - paypals
  - repository
  - users
  - auth
package: com.sdu.paypals.repository
source_path: app/src/main/java/com/sdu/paypals/repository/UserProfileRepository.kt
aliases:
  - UserProfileRepository.kt
---

# UserProfileRepository

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/repository/UserProfileRepository.kt`  
> Internal repository managing Firebase user identity helpers, account validation, profile synchronization (`users/{uid}`), profile update propagation across groups, and pending email invitation claiming.

## Responsibilities
- **Identity Helpers**: `currentUserId()`, `currentUserName()` (display name falling back to email prefix or `"User"`), `currentUserEmail()`.
- **Account Validation**: `validateAndBuildAccountProfile(...)` trims/normalizes inputs, checks non-empty name, email regex, and 6+ char password, returning canonical `Result<Map<String, Any>>`.
- **Profile Sync & Invites**: `syncCurrentUserProfile(...)` merges user data into `users/{uid}` (offline-safe via `isNetworkAvailable` in [[RequestSyncManager]]) and runs `claimPendingEmailInvites(...)` using deterministic IDs from `inviteIdForEmail`.
- **Group Propagation**: `propagateProfileUpdate(...)` uses `executeWriteTask` in [[RequestSyncManager]] to update `users/{uid}` and batch-updates embedded member maps across all groups containing `uid`.

## Links & Dependencies
- **Exposed via**: [[PayPalsRepository]]
- **Uses**: [[FirebaseProvider]] · [[GroupChat]] · [[RequestSyncManager]]
- **Triggered by**: [[MainActivity]] · [[LoginFragment]] · [[HomeFragment]] · [[GroupsFragment]] · [[ProfileFragment]] · [[ExampleUnitTest]]
