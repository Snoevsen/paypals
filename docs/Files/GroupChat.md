---
tags:
  - paypals
  - model
  - firestore
  - groups
package: com.sdu.paypals.model
source_path: app/src/main/java/com/sdu/paypals/model/GroupChat.kt
aliases:
  - GroupChat.kt
  - GroupIcon
  - GroupMember
---

# GroupChat

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/model/GroupChat.kt`  
> Defines core group data models (`GroupIcon`, `GroupMember`, `GroupChat`), Firestore map/snapshot serialization (`toMap` / `fromDocument`), validation constants, and admin/leave governance logic.

## Classes & Enums

### GroupIcon
Enum mapping keys (`groups`, `home`, `trip`, `food`, `shopping`, `celebration`) to display labels and `@DrawableRes` icons in [[03 - Layouts & Resources#Drawables & Launcher / Splash Icons|drawables]]. Includes `fromKey(...)`.

### GroupMember & GroupChat
- **`GroupMember`**: Lightweight participant projection (`uid`, `displayName`, `email`) with `toMap()` and `fromMap(...)`.
- **`GroupChat`**:
  - Constants: `MAX_PAYMENT_DESCRIPTION_LENGTH = 100`, `MAX_GROUP_NAME_LENGTH = 50`, `MAX_GROUP_DESC_LENGTH = 140`.
  - Helper checks: `isMember(userId)`, `isAdmin(userId)`, `canUserLeave(userId)` (blocks sole admins from leaving while other members remain), and `getIconDrawableRes()`.
  - Companion `fromDocument(doc)` and instance `toMap()`.

## Links & Dependencies
- **Used by Repositories**: [[GroupRepository]] · [[UserProfileRepository]] · [[PaymentRepository]] · [[PayPalsRepository]]
- **Used by UI & Adapters**: [[GroupListAdapter]] · [[GroupsFragment]] · [[CreateGroupFragment]] · [[GroupChatFragment]] · [[GroupBalanceFragment]] · [[GroupSettingsFragment]] · [[UiHelpers]]
- **Tested by**: [[ExampleUnitTest]]
