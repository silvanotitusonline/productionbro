# Go live now (RTC Community)

Schema on the **connected lab** project `eqwstpdjoineycrkhpht` is RLS-complete.
A Play Store / production APK is **not** a GO until the human steps below are done.
This file is the only remaining checklist. Do not expand it with product ideas.

## Already true (lab `eqwst…`, 2026-09-21)

- Every `public` table has RLS + at least one policy.
- Civic/cases/audit are RPC-first (deny-direct).
- `process-account-deletions` and `media-orphan-cleanup` keep `verify_jwt=false` **on purpose** and fail closed on a scheduler secret (not open service-role).
- Daily Post: FK indexes + `(select auth.uid())` policies applied.

## You must do (cannot be done from this agent)

1. **One GitHub repo.** Canonical: `silvanotitusonline/productionbro`. Archive `RTC-New` / Live copies or stop pushing to them.
2. **Point Grok + the Android app at production** `pbzzfzfgwzwdstvnwzqu` (reconnect the Supabase connector). Replay `20260921121500_daily_post_indexes_and_rls_initplan.sql` there.
3. **GitHub Actions secrets** on `productionbro`:
   - `RTC_PROD_SUPABASE_URL`
   - `RTC_PROD_SUPABASE_PUBLISHABLE_KEY`
   - `GOOGLE_SERVICES_JSON_BASE64`
   - `RTC_ANDROID_KEYSTORE_BASE64`
   - `RTC_ANDROID_KEYSTORE_PASSWORD`
   - `RTC_ANDROID_KEY_ALIAS`
   - `RTC_ANDROID_KEY_PASSWORD`
4. Run workflow **Android Production Verification** on `main`. Need green: contracts, RPC manifest, unit, lint, `assembleDebug`. Release APK/AAB uploads when secrets exist.
5. **Branch protection** on `main`: required check = `verify-android`.
6. Confirm Edge secrets exist on **production**:
   - `ACCOUNT_DELETION_SCHEDULER_SECRET`
   - alert-dispatch secret used by `assert_rtc_alert_dispatch_secret`
7. **Device smoke** (one physical phone): sign-in, FCM, civic create + photo, feed video, MFA QR, TalkBack, 200% font, process death, light/dark.
8. Play Console: closed testing track, Data safety form, 16 KB page-size, target API 35/36.

Until 4 + 6 + 7 are done, do not call the app production-ready.
