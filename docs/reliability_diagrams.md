# PayPals Reliability QA Diagrams (Mermaid)

Use these two Mermaid diagrams when generating SVG/PNG figures for `docs/implementation_report.typ`.

## Diagram 1: Reliability Architecture (`reliability_architecture.svg`)

```mermaid
flowchart TD
    OS["Android OS: SavedStateRegistry & ConnectivityManager"]

    subgraph UI["UI Layer - Single-Activity Fragments"]
        MA["MainActivity - Banner, NavHost, Touch Unfocus"]
        FRAGS["Fragments - Login, CreateGroup, GroupChat, Activity, GroupSettings, Profile"]
        UIH["UiHelpers - bindSavedText, collectOnStarted, PayShare Dialog"]
    end

    subgraph VM["Lifecycle State Preservation Layer - QA 3"]
        VMS["6 SavedStateHandle ViewModels"]
        SSH["SavedStateHandle + StateFlow - Drafts, Open Dialogs, Excluded UIDs, Filters"]
    end

    subgraph REPO["Domain & Sync Layer - QA 1 & QA 2"]
        FAC["PayPalsRepository Facade"]
        DOM["Domain Repositories: UserProfile, Group, Payment, ActivityAndBalance"]
        FP["FirebaseProvider: Firestore PersistentCacheSettings & Composite Observer"]
        RSM["RequestSyncManager: PendingWriteQueue & NetworkCallback"]
        NH["NotificationHelper: Background Sync & Nudge Alerts"]
    end

    OS -- "Restores Bundle keys on relaunch" --> SSH
    OS -- "Network onAvailable / onLost" --> RSM
    FRAGS <--> UIH
    UIH <--> VMS
    VMS <--> SSH
    FRAGS --> FAC
    FAC --> DOM
    DOM --> FP
    DOM --> RSM
    RSM -- "RequestStatusEvent" --> MA
    RSM -- "Delayed sync completed" --> NH
```

## Diagram 2: Process Death & Recovery Sequence (`lifecycle_recovery_sequence.svg`)

```mermaid
sequenceDiagram
    actor User
    participant Frag as "GroupChatFragment"
    participant Helper as "UiHelpers - bindSavedText"
    participant VM as "GroupChatViewModel"
    participant SSH as "SavedStateHandle / OS Bundle"

    User->>Frag: Tap I Paid, type 180.50, exclude members
    Frag->>VM: openCreateDialog() + updateDraftAmount() + setMemberExcluded()
    VM->>SSH: Store primitive and ArrayList keys
    Note over User,SSH: User minimizes app for call -> OS kills process for RAM
    SSH-->>SSH: SavedStateRegistry preserves Bundle
    User->>Frag: Relaunch PayPals -> GroupChatFragment recreated
    Frag->>VM: by viewModels() injects restored SavedStateHandle
    Frag->>VM: Re-open dialog if isCreateDialogOpen is true
    VM-->>Helper: Emit StateFlow values when STARTED
    Helper-->>Frag: Restore EditText amounts, description & checkboxes
```
