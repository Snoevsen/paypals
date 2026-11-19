---
tags:
  - paypals
  - sync
  - offline
  - testing
package: com.sdu.paypals.sync
source_path: app/src/main/java/com/sdu/paypals/sync/PendingWriteQueue.kt
aliases:
  - PendingWriteQueue.kt
---

# PendingWriteQueue

> [!info] File Summary
> **Path**: `app/src/main/java/com/sdu/paypals/sync/PendingWriteQueue.kt`  
> Pure JVM-testable background retry queue (`enqueueIfAbsent`, `removeById`, `markCompleted`, `snapshot`) and transient-failure classifier (`isTransientCodeName`, `isTransientNetworkFailure`) used by [[RequestSyncManager]].

## Links & Dependencies
- **Used by**: [[RequestSyncManager]]
- **Tested by**: [[ExampleUnitTest]]
