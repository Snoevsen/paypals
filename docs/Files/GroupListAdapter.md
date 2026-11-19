---
tags:
  - paypals
  - adapter
  - groups
package: com.sdu.paypals
source_path: app/src/main/java/com/sdu/paypals/adapter/GroupListAdapter.kt
aliases:
  - GroupListAdapter.kt
---

# GroupListAdapter

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/adapter/GroupListAdapter.kt`  
> `ListAdapter` binding `GroupChat` items onto `item_group.xml` cards for both the dashboard and main groups screen.

## Responsibilities
- Uses `simpleDiffCallback { it.id }` from [[UiHelpers]] for efficient RecyclerView diffs.
- Binds group category icon (`GroupChat.getIconDrawableRes`), name, description, `Admin` badge visibility (`group.isAdmin`), member count summary, `lastActivityText`, and click callback `onGroupClick`.

## Links & Dependencies
- **Layout & Model**: [[03 - Layouts & Resources#item_group.xml|item_group.xml]] · [[GroupChat]]
- **Helpers & Consumers**: [[UiHelpers]] · [[HomeFragment]] · [[GroupsFragment]]
