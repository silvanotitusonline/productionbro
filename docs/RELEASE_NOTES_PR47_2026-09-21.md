# RTC Community Android — Release Notes

**Release scope:** PR #47 and the associated resilience, authentication, navigation, configuration, and synthetic-data cleanup work  
**Merged PR:** [#47 — Fix resident Profile navigation and session role synchronization][1]  
**Merge commit:** `5d16688948455542d5f3210c52ab8d5f804fe4`  
**Date:** 21 September 2026

## Overview

This release hardens the RTC Community Android application for production-oriented use. It resolves the resident Profile navigation failure, introduces anonymous Guest Mode, makes profile updates reactive across the application, separates account preferences from application settings, strengthens network failure handling, and removes synthetic content from the codebase and Supabase environments.

Testing remains paused pending the requested go-ahead. The existing CI failures are recorded as validation follow-up items rather than release sign-off.

## User-facing changes

### Resident Profile navigation

The Account tab now opens a dedicated resident Profile route instead of falling back to Home. The route is wired into the main navigation graph with single-top behavior to avoid duplicate destinations. Loading and missing-session states are handled without throwing an exception that could restart the main activity.

### Guest Mode

The authentication flow now provides **Continue as Guest**. Google Sign-In UI and its direct client-side flow were removed from the Android application. Guest sessions use Supabase anonymous authentication and enter the main application without requiring an email address, password, or display name.

Guest identities render as **Anonymous** with a default placeholder avatar. Guest profile fields are read-only. Attempts to modify a guest profile are blocked with this message:

> Guest profiles cannot be modified. Please register a full account to customize your profile.

Guests can still submit community comments and Public Reports. Guest-created content carries the anonymous identity rather than an editable resident profile.

### Reactive profile synchronization

Profile data now has a single reactive source of truth backed by `StateFlow` and the local Room cache. A successful profile update writes to Supabase and updates the local cache immediately. Account headers, community content, and comment surfaces can reflect a changed display name without an application restart.

### Account preferences and application settings

Account-specific preferences are separated from the broader App Settings screen. App Settings covers application-level concerns such as cache clearing, build information, language or region surfaces, and legal or informational links. Preference updates use bounded network operations and always clear loading state in failure paths, preventing indefinite spinners.

## Resilience and data-integrity changes

Network resilience behavior is centralized in `NetworkResilience.kt`. Key paths use bounded request timeouts, optimistic UI mutations roll back when persistence fails, and idempotent `clientRequestId` handling prevents duplicate comment or report submissions during retries.

The guest authorization migration aligns anonymous-session permissions with the deployed Supabase RPC signatures. Anonymous users may create permitted community interactions, while profile records remain protected from guest modification. Role resolution and Supabase metadata mapping were updated so resident and staff sessions hydrate consistently.

## Synthetic-data eradication

The no-mock policy was applied to source code and Supabase data. The Android repository no longer relies on hardcoded runtime mock lists, synthetic content generators, portrait fixtures, offline mock repositories, or Room initialization callbacks that populate fake feed content. Empty feeds and comment sections are treated as supported product states.

In RTC Production, the seven identified synthetic accounts and associated rows were removed.

In RTC Non-Production, the cleanup removed five fake Daily Post articles, 1,000 fake Daily Post comments, two synthetic Community Feed posts, two synthetic official notices, ten deterministic marketplace demo businesses and their offerings, and the ten dedicated marketplace demo accounts with dependent marketplace and audit records. The `daily-post-scheduler` Edge Function was removed and its test cron job was disabled.

The final targeted verification reported zero remaining rows for the identified synthetic Community Feed posts, synthetic official notices, deterministic marketplace businesses, and marketplace demo accounts.

## Supabase and repository configuration

The Non-Production Supabase project reference is `eqwstpdjoineycrkhpht`, with API URL `https://eqwstpdjoineycrkhpht.supabase.co`. The Production project reference remains `pbzzfzfgwzwdstvnwzqu`.

Every installable Android APK now targets the Production Supabase project. The Android build expects the production publishable configuration through Gradle properties, with CI allowed to override the same production values through environment variables:

| Configuration | Purpose |
| --- | --- |
| `RTC_PROD_SUPABASE_URL` | Production Supabase API URL. |
| `RTC_PROD_SUPABASE_PUBLISHABLE_KEY` | Production publishable client key. |
| `GOOGLE_SERVICES_JSON_BASE64` | Optional Firebase Android configuration for notification or release-candidate builds; this is not Google Sign-In. |

Secret values are intentionally excluded. Publishable keys belong in the configured build or repository-secret mechanism. Service-role keys and other privileged credentials must remain server-side and must not be placed in the Android application.

The Non-Production project has private storage buckets for community media, marketplace media, profile media, secure evidence, exports, and related operational assets. No bucket was identified as public during the configuration audit.

## Validation status and follow-up

Testing was intentionally paused during the final mock-data cleanup. The full compile, unit, lint, instrumentation, and Supabase verification sequence should resume only after the user provides the requested go-ahead.

The existing PR checks were not a release sign-off: Android production verification, release-gate contract checks, and Supabase local verification were recorded as failed before the merge. These failures require investigation during the next validation pass.

The Non-Production audit also reported two existing Google identity rows. Google Sign-In is removed from the Android code, but the Supabase provider setting and ownership of those historical identities should be reviewed before a production release. They were not deleted automatically because the audit did not establish that they were synthetic accounts.

## References

[1]: https://github.com/silvanotitusonline/RTC-New/pull/47 "RTC-New pull request #47"
[2]: https://github.com/silvanotitusonline/RTC-New "RTC-New GitHub repository"
[3]: https://github.com/silvanotitusonline/RTC-New/commit/5d16688948455542d5f3210c52ab8d5f804fe4 "RTC-New PR #47 merge commit"
[4]: https://github.com/silvanotitusonline/RTC-New/blob/main/app/src/main/java/za/org/rtc/community/core/network/NetworkResilience.kt "RTC-New network resilience implementation"
[5]: https://github.com/silvanotitusonline/RTC-New/blob/main/app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt "RTC-New navigation graph"
[6]: https://github.com/silvanotitusonline/RTC-New/blob/main/supabase/migrations/20260920000000_guest_mode_permissions.sql "RTC-New guest mode permissions migration"

**Author:** Manus AI  
21 September 2026
