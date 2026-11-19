// PayPals — Implementation & Reliability QA Report (Typst)
// Target length: max 4 pages (10.5pt text, standard margins + 2 rendered Mermaid figures)
// Note: Uncomment the #set block below if compiling standalone; omit if inserting into an existing Typst report.

/*
#set page(
  paper: "a4",
  margin: (x: 2.0cm, y: 2.0cm),
  numbering: "1 / 1",
)
#set text(font: "New Computer Modern", size: 10.5pt)
#set par(justify: true, leading: 0.6em)
*/

= Implementation of PayPals & Reliability Quality Attributes

== Implementation of the Application

*PayPals* (`com.sdu.paypals`) is an Android group expense-splitting and payment-tracking application implemented in Kotlin targeting SDK 37 (`minSdk = 24`) with Material 3 components, ViewBinding, AndroidX Navigation (`nav_graph.xml`), Firebase Authentication, Cloud Firestore (`firebaseBom = 33.10.0`), Android 12+ SplashScreen (`core-splashscreen`), and ZXing portrait QR scanning (`PortraitCaptureActivity`).

=== Layered Architecture & Key Design Patterns

To keep UI controllers lightweight and decouple business rules from Firebase transport, the application is structured into four cooperating layers:

#table(
  columns: (auto, 1fr, 1.3fr),
  inset: 6pt,
  align: (left, left, left),
  [*Layer*], [*Key Modules*], [*Responsibilities & Patterns*],
  [UI & Navigation],
  [`MainActivity`, 9 Fragments, 3 `ListAdapter`s, `UiHelpers`],
  [Single-activity host with `BottomNavigationView` tab syncing across nested group routes, dynamic auth start destination, Strategy-based `FilterMode`/`SortMode` (`ActivityFragment`), Decorator-based dialog unfocusing (`enableTapOutsideToUnfocus`), and `simpleDiffCallback` Factory.],
  [Lifecycle State (`viewmodel/`)],
  [`LoginViewModel`, `CreateGroupViewModel`, `GroupChatViewModel`, `ActivityFilterViewModel`, `GroupSettingsViewModel`, `ProfileViewModel`],
  [`androidx.lifecycle.ViewModel` + `SavedStateHandle` paired with `StateFlow` (`savedStateHandle.getStateFlow`) to preserve in-progress forms, filters, and open dialogs across configuration changes and OS process death.],
  [Domain & Facade (`repository/`)],
  [`PayPalsRepository`, `FirebaseProvider`, `UserProfileRepository`, `GroupRepository`, `PaymentRepository`, `ActivityAndBalanceRepository`],
  [*Facade Pattern*: `PayPalsRepository` unifies identity/profile management, group & QR membership, bill splitting & nudges, and balance calculations (`UserBalanceSummary`). *Composite Observer Pattern*: `FirebaseProvider.observeUserGroupPayments` manages nested `groups -> payments` listeners.],
  [Offline Sync & Feedback (`sync/`, `util/`)],
  [`RequestSyncManager`, `PendingWriteQueue`, `NotificationHelper`],
  [Monitors `ConnectivityManager.NetworkCallback`, caches writes for background retry, prevents UI blocking (`optimisticResult`), and dispatches status banners and local notifications.],
)

=== Core Application Workflows

- *Account Onboarding & Group Sync*: `LoginFragment` supports Log In / Sign Up validation (`UserProfileRepository.validateAndBuildAccountProfile`) and syncs `users/{uid}` documents while claiming pending email invitations (`claimPendingEmailInvites`).
- *Groups, Balances & Bill Splitting*: `GroupsFragment` joins groups via portrait ZXing QR codes (`paypals://join?groupId=...`) or creates groups (`CreateGroupFragment`). Inside `GroupChatFragment`, users track expenses ("Request") via an equal-split calculator supporting per-member exclusions (`PaymentRepository.buildPaymentRequest`), inspect itemized participant receipts, pay shares (`showPayShareConfirmationDialog`), ping unpaid members (`nudgeUnpaidParticipants`), or inspect member-level balances (`GroupBalanceFragment`).
- *Cross-Group Activity & Home Dashboard*: `HomeFragment` aggregates total owed/owes (`observeTotalUserBalance`), the 3 most recent groups, and the latest 5 activities, while `ActivityFragment` lets users search by name, timestamp, or amount (`formatAmount`) and toggle between user-paid expenses, payments received, and reminders.

== Reliability Quality Attributes (QAs)

PayPals addresses reliability across both *network/offline fault tolerance* and *Android lifecycle state preservation (Reliability QA 3)*:

1. *Reliability QA 1 & QA 2 — Network Fault Tolerance, Offline Availability & User Feedback*:
   `FirebaseProvider.db` configures Cloud Firestore with `PersistentCacheSettings` and subscribes with `MetadataChanges.INCLUDE`, keeping cached groups, payments, and balances readable offline. All mutating writes go through `RequestSyncManager.executeWriteTask`:
   - Emits `RequestState.PENDING` immediately to `MainActivity`'s top banner (`tvRequestStatusBanner`).
   - If the device is offline, a request exceeds `2500ms`, or Firestore fails with a transient network error (`UNAVAILABLE` or `DEADLINE_EXCEEDED` classified by `PendingWriteQueue.isTransientNetworkFailure`), the operation is queued in `PendingWriteQueue`, `optimisticResult` is returned immediately so the UI remains unblocked, and `FAILED_RETRYING_IN_BACKGROUND` is emitted.
   - When `ConnectivityManager.NetworkCallback.onAvailable` fires (or the queued Firestore task finishes), `processBackgroundRetries()` executes retries or calls `waitForPendingWrites()`, emitting `BACKGROUND_RETRY_SUCCEEDED` and posting a notification via `NotificationHelper.showBackgroundSyncNotification`.

2. *Reliability QA 3 — State Preservation & Recovery (`ViewModel` + `SavedStateHandle`)*:
   Added `androidx.lifecycle:lifecycle-viewmodel-ktx`, `lifecycle-viewmodel-savedstate`, `lifecycle-runtime-ktx` (`2.11.0`), and `androidx.fragment:fragment-ktx` (`1.6.2`) in `gradle/libs.versions.toml` and `app/build.gradle.kts`. Six `ViewModel`s inject `SavedStateHandle` so transient UI state survives OS background termination, incoming phone calls, permission dialogs, rotation, and multi-window resizing:
   - *Form Drafts*: `LoginViewModel` (`isSignUpMode`, `fullName`, `email` — omitting plaintext passwords for security), `CreateGroupViewModel` (`groupName`, `groupDescription`, `selectedIconKey`), `GroupSettingsViewModel` (unsaved metadata edits guarded via `populateFromGroupIfNeeded`), and `ProfileViewModel` (name/email edits).
   - *Open Modal Dialog Recovery*: `GroupChatViewModel` persists `isCreateDialogOpen`, `draftAmount`, `draftDescription`, and `excludedMemberIds` (`ArrayList<String>`) as well as `pendingPaySharePaymentId` and `draftPayShareNote`. In `GroupChatFragment.onDestroyView()`, open `AlertDialog` windows call `dismiss()` (avoiding `WindowLeaked` errors without triggering `OnCancelListener`), allowing dialogs and their inputs to reopen automatically when the group/payment stream rebinds after recreation.
   - *Loop-Safe Lifecycle Binding*: `UiHelpers.kt` provides `bindSavedText` and `collectOnStarted` (`repeatOnLifecycle(Lifecycle.State.STARTED)`), syncing `StateFlow` state back to `EditText` widgets via `setTextIfDifferent` while suppressing redundant `TextWatcher` feedback loops.

== Reliability Architecture Diagram

#figure(
  // Render Architecture Mermaid diagram (Diagram 1 in docs/reliability_diagrams.md) to SVG/PNG and place below:
  rect(width: 100%, height: 4.5cm, stroke: 0.6pt + gray, radius: 4pt)[
    #align(center + horizon)[
      _Insert rendered Mermaid Diagram 1 (`reliability_architecture.svg`) here_ \
      `#image("reliability_architecture.svg", width: 100%)`
    ]
  ],
  caption: [PayPals Reliability Architecture combining `ViewModel` + `SavedStateHandle` (QA 3) and `RequestSyncManager` + Firestore `PersistentCacheSettings` (QA 1 & QA 2).],
)

#figure(
  // Render Lifecycle Recovery Sequence Mermaid diagram (Diagram 2 in docs/reliability_diagrams.md) to SVG/PNG and place below:
  rect(width: 100%, height: 4.0cm, stroke: 0.6pt + gray, radius: 4pt)[
    #align(center + horizon)[
      _Insert rendered Mermaid Diagram 2 (`lifecycle_recovery_sequence.svg`) here_ \
      `#image("reliability_recovery_sequence.svg", width: 95%)`
    ]
  ],
  caption: [Process-death recovery flow for an in-progress "Request" payment dialog in `GroupChatFragment`.],
)

== Verification & Testing of Reliability Quality Attributes

Automated unit tests in `app/src/test/java/com/sdu/paypals/ExampleUnitTest.kt` directly verify both *State Preservation & Recovery (QA 3)* and *Fault-Tolerant Retry Queueing (QA 1 & QA 2)* on the JVM without requiring emulator flakiness.

=== Tested Reliability Scenarios (`ExampleUnitTest.kt`)

- *OS Process Death Simulation (`recreateSavedStateAfterProcessDeath`)*: Snapshots every key/value held inside a `SavedStateHandle` (`original.keys().associateWith { original.get<Any?>(it) }`) and instantiates a brand-new `ViewModel(SavedStateHandle(persistedBundleMap))`, matching Android `SavedStateRegistry` Bundle restoration after `adb shell am kill com.sdu.paypals`.
  - `savedStateHandle_preservesLoginAndCreateGroupDraftsAcrossProcessDeath`: Verifies signup mode, full name, email, and new group draft inputs/icon (`GroupIcon.TRIP`) survive recreation and reset on submission (`clearState` / `clearDraft`).
  - `savedStateHandle_preservesOpenPaymentDialogAndExcludedMembersAcrossProcessDeath`: Verifies that an open payment "Request" dialog (`isCreateDialogOpen = true`, `draftAmount = "180.50"`, `draftDescription = "Ferry tickets"`, excluded members `["u_2", "u_3"]`) and PayShare dialog note survive process death and clear cleanly afterward.
  - `savedStateHandle_preservesActivityFiltersAndGroupSettingsEditsAcrossProcessDeath`: Verifies search query, Strategy enums (`FilterMode.EXPENSES_PAID_BY_ME`, `SortMode.AMOUNT_DESC`), card limit (`25`), and unsaved group edits survive relaunch without being overwritten by incoming Firestore data (`populateFromGroupIfNeeded` returns `false`).
- *Offline Queue Deduplication & Transient Failure Classification*:
  - `pendingWriteQueue_supportsOfflineDeduplicationAndTransientErrorClassification`: Verifies `PendingWriteQueue` prevents duplicate retry entries for the same request ID, guarantees `markCompleted(item)` returns `true` only once (preventing duplicate background-success notifications), and confirms `isTransientNetworkFailure` / `isTransientCodeName` treat offline state and `UNAVAILABLE` / `DEADLINE_EXCEEDED` as retryable while rejecting non-network errors (`PERMISSION_DENIED`, `IllegalArgumentException`).

=== Observed Test Results & Manual Device Verification

Running `.\gradlew.bat assembleDebug test --console=plain` completed with `BUILD SUCCESSFUL` and all *14 tests in `ExampleUnitTest` passed* (`tests="14" skipped="0" failures="0" errors="0"`, total `0.113s` reported in `app/build/test-results/testDebugUnitTest/TEST-com.sdu.paypals.ExampleUnitTest.xml`).

To complement JVM tests at the OS level, full process termination can be verified manually on an Android device/emulator by opening a form or the "Request" dialog, backgrounding PayPals, running `adb shell am kill com.sdu.paypals`, and relaunching from Recents to observe immediate restoration of all form inputs, dialog state, and cached Firestore data.
