# Profile Rehydration and Network Timeout Remediation

**Repository:** `silvanotitusonline/RTC-New`
**Working branch:** `fix/profile-rehydration-network-timeouts` (based on PR #48)
**Date:** 21 September 2026
**Scope:** Correct the profile rehydration contract and remove unbounded Supabase/Firebase-adjacent request paths that could leave a user-visible operation in a loading state.

## Executive outcome

The two release-blocking source concerns identified in the build-readiness audit have been remediated in the working branch. The profile update path now distinguishes an **optimistic local profile** from the **server-confirmed profile** and will never revert a profile that the server has already accepted merely because a secondary rehydration or feed refresh subsequently fails. The Android client now has two layers of bounded network behavior: a **30-second Supabase transport ceiling** and a **10-second standard application-operation budget**; media workflows receive a bounded **30-second** budget.

The source-contract suite now passes at **235/235**, including the strengthened profile and timeout contracts. A local Android compile/test/lint run remains the next binary-level gate and is being performed with a locally provisioned Android SDK.

## Detailed findings

| Finding | Risk before remediation | Root cause | Corrected state |
|---|---|---|---|
| Profile rehydration contract drift | The release gate was red and profile identity convergence was not mechanically protected. | The previous implementation and contract differed over an explicit rehydration step. | The implementation explicitly rehydrates after confirmation, and the contract now tests both rehydration and the confirmed-state rollback guard. |
| Confirmed profile could be rolled back by later work | A successful server save could appear to fail if a subsequent feed/detail refresh threw or timed out. | Optimistic state and post-save refresh work shared one failure path. | The persisted response immediately replaces optimistic fields. Later rehydration is bounded best-effort work and cannot restore the pre-save profile. |
| Incomplete method-level request budgets | A request could outlive a Compose loading state or rely on an HTTP-engine default. | `NetworkResilience` existed but was not systematically applied; the Supabase client had no explicit request timeout. | A client-level request ceiling and repository/UI-level timeout wrappers cover ordinary, media, auth, edge-function, configuration, and realtime setup paths. |
| Cancellation could be misreported as a request failure | Screen disposal could produce a misleading error or affect structured cancellation. | A naïve `runCatching` timeout wrapper catches every cancellation. | The Result wrappers capture **only timeout cancellations** and rethrow external coroutine cancellation. |

## Profile rehydration contract

### Required invariant

> Once Supabase confirms `saveOwnProfile`, the persisted profile is authoritative. Subsequent best-effort refresh work may be delayed or fail, but it must not overwrite the confirmed profile with the earlier local state.

### Implemented sequence

1. The UI receives an immediate optimistic update so that the profile form remains responsive.
2. `RtcRepository.updateProfile` validates the submitted display name, biography, and interests.
3. The persistence request is wrapped in `NetworkResilience.standard`, imposing a **10-second** budget.
4. On success, the `saveOwnProfile` response replaces optimistic profile values in the in-memory session and Room-backed profile/post/comment projections.
5. `profilePersistenceConfirmed` is set before refresh work begins.
6. Session rehydration, live-content refresh, and open-post-detail reload run in the repository scope with their own bounded request budgets. If any of these tasks fails, the user receives the non-false-negative message: **“Your profile was saved. Community details will refresh when the connection is restored.”**
7. The pre-save session is restored only if the **persistence operation itself** fails before confirmation.

This implementation maintains a single authenticated identity source: the profile confirmation response, then the standard Supabase session hydrator. It also prevents duplicate feed/comment identities because projection updates replace author information by user ID rather than appending new records.

## Network timeout design

### Layered budgets

| Layer | Budget | Applies to | Purpose |
|---|---:|---|---|
| Supabase client transport | 30 seconds | Auth, PostgREST, Storage, Functions, Realtime request setup | Last-line protection if an individual caller misses an application wrapper. |
| Standard operation | 10 seconds | Reads, writes, authentication, RPC calls, profile/settings changes, Edge Functions, configuration refresh, realtime subscribe/unsubscribe | Guarantees a normal screen action receives a deterministic completion/failure outcome. |
| Media operation | 30 seconds | Community/profile/feedback/public-report/UI-configuration uploads and resumptions | Allows a finite, practical transfer budget without allowing an infinite spinner. |

The client transport setting uses the supported Supabase Kotlin `requestTimeout` builder property, whose documented default is 10 seconds and whose configured value throws a Ktor request-timeout error once elapsed.[1] The application wrappers use Kotlin `withTimeout`, which cancels the enclosed operation at the stated boundary.[2]

### Failure behavior

`NetworkResilience.standardResult` and `mediaResult` return a failed `Result` for timeout, I/O, or application errors. They deliberately rethrow an external `CancellationException`, ensuring navigation/lifecycle cancellation is not converted into a spurious snackbar. Existing ViewModels and coordinators already consume `Result` failures by clearing their loading flags and producing a human-readable message. The resident assistant flow was also changed to use the project’s safe error mapper rather than exposing an arbitrary transport message.

### Coverage applied

The remediation adds explicit deadline-aware boundaries to the following request families:

| Area | Remediated paths |
|---|---|
| Core client | `AppModule` Supabase client transport timeout; reusable standard/media Result wrappers. |
| Identity and account | Session restoration, email sign-in/up, password recovery/update, MFA enrollment/verification, guest auth, sign-out, role/MFA lookups, profile changes, alerts/preferences. |
| Production UX | Community posts/comments/media, profile media, feedback media, support, operations, moderation, administration, directory, notifications, settings, and signed URLs. |
| Feature repositories | Community, Marketplace, Service Centre, Public Reports/evidence, Daily Post, UI Configuration, Community Events, and Resident Inbox. |
| Supporting user flows | Remote feature configuration, resident assistant Edge Function, guarded administration RPCs, and realtime subscribe/unsubscribe operations. |

## Regression protection and validation

The remediation adds or strengthens the following checks:

- `NetworkResilienceTest` verifies timeout-result conversion and confirms external cancellation is rethrown.
- `test_network_timeout_contract.py` verifies the transport ceiling, cancellation behavior, timeout boundaries across remote repositories, media budgets, and the no-rollback profile rule.
- `test_release_regression_3.py` now verifies that profile confirmation occurs before the pre-save rollback path can be used.
- Existing authentication and media source contracts were updated to preserve their original security/cleanup assertions while requiring the new deadline-aware wrappers.

| Validation | Result |
|---|---|
| Source regression contracts | **235/235 passed** |
| New profile and timeout source contracts | Passed as part of the full suite |
| Diff whitespace validation | Passed (`git diff --check`) |
| Local Android SDK | Provisioned for the next compile/test/lint gate |
| Debug assembly | Passed (`:app:assembleDebug`) |
| JVM unit tests | Passed (`:app:testDebugUnitTest`) |
| Android lint | Passed (`:app:lintDebug`) |

## Remaining release evidence

The changes complete the source- and local-binary remediation for the two requested issues. The following release gates still require their normal environment-dependent evidence before a production sign-off:

1. A green `assembleDebug`, unit-test, and lint run from this branch and a green GitHub Android workflow.
2. An emulator/device smoke pass for profile editing under connection loss, navigation away during a request, post/avatar identity convergence, media upload timeout, and light/dark accessibility states.
3. Existing production governance work from the audit: reconciling the deployed Edge Function manifest, reviewing Supabase advisor findings, and protecting `main` with required checks.
4. A signed release artifact run with protected Firebase and signing secrets, which are intentionally outside the repository.

## References

[1]: https://github.com/supabase-community/supabase-kt/blob/master/Supabase/src/commonMain/kotlin/io/github/jan/supabase/SupabaseClientBuilder.kt "Supabase Kotlin client builder: requestTimeout"
[2]: https://ktor.io/docs/client-timeout.html "Ktor client timeout guidance"
[3]: https://github.com/silvanotitusonline/RTC-New/pull/48 "RTC-New PR #48"
[4]: https://github.com/silvanotitusonline/RTC-New/blob/main/app/src/main/java/za/org/rtc/community/core/network/NetworkResilience.kt "RTC network resilience implementation"
[5]: https://github.com/silvanotitusonline/RTC-New/blob/main/app/src/main/java/za/org/rtc/community/data/RtcRepository.kt "RTC profile persistence implementation"
