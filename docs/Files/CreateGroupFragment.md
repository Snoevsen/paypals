---
tags:
  - paypals
  - fragment
  - groups
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/CreateGroupFragment.kt
aliases:
  - CreateGroupFragment.kt
---

# CreateGroupFragment

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/CreateGroupFragment.kt`  
> Form screen allowing a user to create a new payment group with name, description, and an icon category.

## Responsibilities
- Validates non-empty group name before submission.
- Resolves selected Material `Chip` ID to a `GroupIcon.key` via declarative `CHIP_TO_ICON` mapping table and `resolveGroupIconKey` in [[UiHelpers]].
- Calls [[PayPalsRepository#Group Management|createGroup]], pops `createGroupFragment` from the back stack, and navigates straight into [[GroupChatFragment]] via `openGroupChat` in [[UiHelpers]].

## Links & Dependencies
- **Layout**: [[03 - Layouts & Resources#fragment_create_group.xml|fragment_create_group.xml]]
- **Models, Data & Helpers**: [[GroupChat]] · [[PayPalsRepository]] · [[GroupRepository]] · [[UiHelpers]]
- **Screens**: [[GroupsFragment]] · [[GroupChatFragment]]
