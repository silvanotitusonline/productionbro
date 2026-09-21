# RTC-New Build-Readiness Audit

**Audit date:** 21 September 2026
**Repository:** `silvanotitusonline/RTC-New`
**Supabase production:** `RTC Community Production` (`pbzzfzfgwzwdstvnwzqu`)
**Supabase non-production:** `RTC Community Non-Production` (`eqwstpdjoineycrkhpht`)

## Executive conclusion

RTC-New is a substantial production-oriented Android application rather than an unfinished prototype. The repository contains a large Jetpack Compose client, Room persistence, Supabase RPC integrations, Edge Function source, migrations, release contracts, and CI workflows. The production Supabase project is active and healthy, and the application’s primary database RPC surface is present.

The project is **not yet ready for production release sign-off**. The current release position is best described as **feature-complete in breadth, validation-incomplete in depth**. The main blockers are a failing source regression contract, incomplete method-level network timeout coverage, unresolved CI/device verification, stale Edge Function deployment tracking, and an unprotected `main` branch.

No repository or production database changes were made during this audit.

## Current repository position

The checked-out `main` branch is clean and points to commit `f696e2a` (`Add PR 47 release notes`). The latest functional merge is PR #47, which introduced resident Profile navigation, guest mode, profile rehydration, preference separation, resilience improvements, and synthetic-data cleanup.

The repository currently contains approximately **252 Kotlin production source files**, **47 unit-test files**, **3 instrumentation-test files**, **79 forward migration files**, **11 Supabase pgTAP test files**, and **46 test/contract utility files**. The codebase is therefore broad and operationally structured, but the breadth increases the importance of systematic integration and device validation.

Five pull requests remain open. PR #48, “Fix profile rehydration and local Supabase reset,” is the active remediation branch. Its latest observed state was `UNSTABLE`; the Contracts/Security and Supabase Local Verification checks had passed, while Android Compile/Test/Lint was still running. Earlier Android failures were caused by the emulator not reaching `sys.boot_completed` within the workflow timeout, not by a reported application assertion failure.

## Verification results

| Area | Result | Assessment |
|---|---|---|
| Repository state | Clean `main` checkout | Good |
| Supabase production status | `ACTIVE_HEALTHY`, PostgreSQL 17.6.1.155, `eu-west-1` | Good |
| Production RPC coverage for sampled Android RPC names | No missing names found | Good, based on sampled contract list |
| Production migration ledger | Present through `guest_mode_permissions_v2` | Good |
| Source regression contracts | **229/230 passed** | Release blocker |
| Supabase local pgTAP on PR #48 | Passed on the latest observed PR check | Positive, but must remain green after merge |
| Android CI | Previous run failed at emulator boot; latest PR run was still in progress at observation time | Release blocker until green |
| Local Gradle verification | Could not start because sandbox had no Android SDK; Java compiler was also initially missing | Environment limitation, not a source verdict |
| `main` branch protection | Not configured | Release-process blocker |

The single failing source contract was `test_profile_updates_rehydrate_community_identity_projections`. The implementation currently saves the profile, applies local state, refreshes live content, and reloads the open community post detail. The contract still expects an explicit `hydrateSupabaseSession()` call and a precise source shape. This appears to be a contract-versus-implementation drift issue, but it must be reconciled rather than ignored because the release gate intentionally treats source contracts as required.

## Android implementation findings

The project has several strong foundations. Identity is centralized through `SupabaseUserIdentity` and session state is represented through `StateFlow`. Guest sessions are explicitly identified as anonymous. Profile updates propagate to Room-backed projections and emit profile update events. Community mutations include optimistic updates and rollback paths for likes, reactions, bookmarks, posts, and comments. Navigation uses Compose Navigation with protected-route wrappers, route-level state collection, and lifecycle-scoped ViewModels.

However, the timeout requirement in the project instructions is not met uniformly. `NetworkResilience` provides 10-second standard and 30-second media budgets, but the static audit found many direct Supabase call sites without a local timeout marker. The largest gaps were observed in `ProductionUxRepository`, `SupabaseMarketplaceRepository`, `SupabaseServiceCentreRepository`, `DailyPostRepository`, `UiConfigurationRepository`, `SupabaseCommunityEventsRepository`, `SupabaseResidentInboxRepository`, and several authentication or administration paths. The central Supabase client in `AppModule.kt` installs Auth, Postgrest, Storage, Functions, and Realtime, but does not configure an HTTP-level connect, request, or socket timeout.

This means a method may still depend on the underlying HTTP client or an unbounded path even though selected critical operations use `NetworkResilience.standard` or `NetworkResilience.media`. The project instruction requires every Firebase or Supabase asynchronous request to have a strict timeout, so this should be treated as a P1 hardening task.

The local Room database uses `.fallbackToDestructiveMigration(true)`. That may be acceptable for a disposable or beta client, but it is not normally appropriate for a production application whose data-preservation expectations are not explicitly destructive. The release decision should either remove this fallback or document and test the intended data-loss behavior.

## Supabase production findings

The production project is active and healthy. Its migration ledger includes the recent guest-mode, daily-post, media, moderation, account, community-post idempotency, and admin-dashboard changes. The sampled set of RPC names extracted from Android source had no missing production functions in the production catalog query.

The production advisor output still reports the following categories:

| Advisor category | Current signal | Meaning |
|---|---:|---|
| RLS enabled without policies | 26 tables, informational | These appear to be protected RPC-only or internal tables in many cases, but the policy/grant posture should remain documented and reviewed |
| Anonymous `SECURITY DEFINER` execution | 10 functions, warning | Public civic-report and UI-configuration reads require endpoint-specific privacy and grant review |
| Authenticated `SECURITY DEFINER` execution | 187 functions, warning | Many are intentional RPC command/query boundaries, but the allowlist and authorization review must remain exhaustive |
| Unindexed foreign keys | 47 findings | Could affect delete/update/query performance as data grows |
| Auth initialization-plan issue | 1 finding | `community_comments` policy should avoid per-row repeated auth evaluation |
| Unused indexes | 101 findings in the current advisor output | Do not remove blindly; review against expected workload and rollout history |

There were no sampled recent PostgreSQL or Edge Function log rows containing the literal `ERROR`/`error` filter used in this audit. This is a useful signal but not proof of absence of operational failures because log schemas and error wording vary.

The production project currently exposes **18 active Edge Functions**. The checked-in `supabase/functions/DEPLOYED_SOURCE_MANIFEST.json` describes only six functions, has a snapshot date of 26 August 2026, and does not represent the current production deployment inventory. Several repository functions were modified after that snapshot. This is a release-governance and traceability gap: source, deployed versions, JWT settings, and hashes cannot currently be reconciled from the manifest alone.

## CI and release-process findings

The latest historical Android verification failure reached the instrumentation stage but timed out waiting for the emulator to boot. The workflow then terminated the emulator. This should be addressed through emulator image/cache stability, timeout tuning, or a retry strategy before device-gated release claims are made.

The Supabase local verification failure was more concrete. The pgTAP suite ran 206 tests and failed three checks in `security_definer_classification.sql`: the reviewed authenticated `SECURITY DEFINER` count and fingerprint were stale, and the test detected 186 functions outside the explicit reviewed set. PR #48 subsequently showed a passing Supabase local verification check, indicating that remediation is in progress, but the merged result still needs a fresh full run.

The release-gate contract suite currently fails only the profile rehydration contract described above. PR #48’s latest observed Contracts/Security check passed, so the branch may already contain the correction. The final decision should be based on a completed green run for the merge commit, not on the prior partial state.

`main` is not protected. There is no branch-protection rule requiring the Android and Supabase checks before merging. This allows a release-significant branch to remain mergeable while validation is unstable.

The release build is deliberately fail-closed and requires production Supabase values, Firebase configuration, and signing material. That is good practice. The repository’s release notes also correctly state that release signing material and privileged Supabase keys must stay outside source control. A signed release artifact has not been verified in this audit because those credentials are intentionally unavailable in the sandbox.

## Priority actions before release sign-off

1. **Make the active PR or follow-up commit fully green.** Re-run the complete Android workflow, Supabase local verification, contract suite, Edge authorization tests, lint, unit tests, debug APK build, instrumentation tests, and release-gate artifacts on the merge candidate.
2. **Resolve the profile rehydration contract drift.** Either update the implementation so it explicitly satisfies the intended identity-refresh contract or update the contract to test the current authoritative behavior. Do not weaken the contract without documenting why.
3. **Apply strict timeouts consistently.** Configure Ktor/Supabase HTTP timeouts centrally and wrap every direct Supabase/Auth/Storage/Functions operation that can outlive the UI request budget. Ensure every failure clears loading state and exposes a human-readable error.
4. **Stabilize emulator verification.** Increase boot tolerance or add a bounded retry around emulator startup, then retain a separate device-smoke pass for notifications, media playback, deep links, MFA QR scanning, TalkBack, 200% font scale, light/dark/system themes, process death, and compact/medium/expanded layouts.
5. **Reconcile the Edge Function manifest.** Refresh the manifest against the actual 18-function production inventory, including versions, JWT settings, and deployment hashes. Explicitly record retired payment endpoints and any intentionally source-only functions.
6. **Protect `main`.** Require the Android verification, Supabase local verification, and release-gate checks before merge. Add a documented release approval rule for the signed production artifact.
7. **Review production advisor findings by risk.** Prioritize anonymous executable `SECURITY DEFINER` functions, the remaining RLS/no-policy tables, unindexed foreign keys on write-heavy or delete-heavy paths, and the community-comments auth init-plan issue.
8. **Complete device and release evidence.** Produce a signed AAB/APK, verify Firebase notification registration, and retain a small release evidence bundle containing commit SHA, migration version, Edge Function inventory, test results, and artifact checksums.

## Final status

**Overall:** Amber / not release-ready.
**Product breadth:** High.
**Backend availability:** Healthy.
**Source quality:** Strong but not fully contract-clean.
**CI confidence:** Insufficient until the active Android run completes green.
**Production hardening:** In progress; timeout coverage, advisor findings, deployment manifest freshness, and branch protection remain open.

## References

[1]: https://github.com/silvanotitusonline/RTC-New "RTC-New GitHub repository"
[2]: https://github.com/silvanotitusonline/RTC-New/pull/48 "RTC-New pull request #48"
[3]: https://supabase.com/dashboard/project/pbzzfzfgwzwdstvnwzqu "RTC Community Production Supabase dashboard"
[4]: https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy "Supabase RLS enabled without policy advisor"
[5]: https://supabase.com/docs/guides/database/database-linter?lint=0028_anon_security_definer_function_executable "Supabase anonymous SECURITY DEFINER advisor"
[6]: https://supabase.com/docs/guides/database/database-linter?lint=0029_authenticated_security_definer_function_executable "Supabase authenticated SECURITY DEFINER advisor"
[7]: https://supabase.com/docs/guides/database/database-linter?lint=0001_unindexed_foreign_keys "Supabase unindexed foreign-key advisor"
[8]: https://github.com/silvanotitusonline/RTC-New/blob/main/app/src/main/java/za/org/rtc/community/core/network/NetworkResilience.kt "RTC-New network resilience implementation"
[9]: https://github.com/silvanotitusonline/RTC-New/blob/main/supabase/functions/DEPLOYED_SOURCE_MANIFEST.json "RTC-New deployed Edge Function manifest"

**Author:** Manus AI
21 September 2026
