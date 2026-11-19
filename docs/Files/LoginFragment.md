---
tags:
  - paypals
  - fragment
  - auth
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/LoginFragment.kt
aliases:
  - LoginFragment.kt
---

# LoginFragment

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/LoginFragment.kt`  
> Authentication screen supporting toggling between **Log In** and **Sign Up** modes against Firebase Authentication.

## Responsibilities
- **Mode Toggle**: Switches form fields cleanly between Login and Sign Up (`tilFullName` visible only during Sign Up).
- **Client Validation**: `validateInputs()` checks non-empty Full Name (in Sign Up), valid `Patterns.EMAIL_ADDRESS` email, and password length (`>= 6`).
- **Firebase Auth Flows**:
  - `performLogin()` calls `auth.signInWithEmailAndPassword(...)`.
  - `performSignUp()` calls `auth.createUserWithEmailAndPassword(...)`, then sets the user's `displayName` via `UserProfileChangeRequest`.
- **Post-Auth Navigation**: `navigateToHome()` triggers [[PayPalsRepository#User Identity & Profile|syncCurrentUserProfile]] (which also claims pending email invites) before navigating to [[HomeFragment]] via [[02 - Manifest, Navigation & Menus#nav_graph.xml|nav_graph.xml]].

## Links & Dependencies
- **Layout**: [[03 - Layouts & Resources#fragment_login.xml|fragment_login.xml]]
- **Repository**: [[PayPalsRepository]] -> [[UserProfileRepository]]
- **Navigation**: [[02 - Manifest, Navigation & Menus#nav_graph.xml|nav_graph.xml]] · [[HomeFragment]] · [[MainActivity]]
