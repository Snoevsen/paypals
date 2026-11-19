---
tags:
  - paypals
  - fragment
  - profile
  - auth
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/ProfileFragment.kt
aliases:
  - ProfileFragment.kt
---

# ProfileFragment

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/ProfileFragment.kt`  
> Manages the signed-in user's account profile, allowing updates to Full Name, Email, and Password (with Firebase re-authentication) and sign-out.

## Responsibilities
- Displays user `displayName` and `email` from `FirebaseAuth.getInstance().currentUser`.
- Validates updates (non-empty name, valid email, password length `>= 6`, and current password requirement whenever changing email or password).
- Re-authenticates sensitive credential changes via `EmailAuthProvider.getCredential(...)`, runs sequential Firebase Auth updates (`updateProfile`, `updateEmail` with `verifyBeforeUpdateEmail` fallback, `updatePassword`), then calls [[PayPalsRepository#User Identity & Profile|propagateProfileUpdate]] to update `users/{uid}` and every group the user belongs to.
- Sign Out clears auth session and returns to [[LoginFragment]] via `action_global_to_loginFragment` in [[02 - Manifest, Navigation & Menus#nav_graph.xml|nav_graph.xml]].

## Links & Dependencies
- **Layout & Navigation**: [[03 - Layouts & Resources#fragment_profile.xml|fragment_profile.xml]] · [[02 - Manifest, Navigation & Menus#nav_graph.xml|nav_graph.xml]]
- **Repositories**: [[PayPalsRepository]] · [[UserProfileRepository]]
- **Destination**: [[LoginFragment]]
