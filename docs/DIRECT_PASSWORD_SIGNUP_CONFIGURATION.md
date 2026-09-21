# Direct Password Signup Configuration

RTC Community uses a standard email-and-password registration flow. New residents should receive an authenticated Supabase session at the end of registration and enter the application immediately. The application does **not** use email OTP, magic-link, or confirmation-code onboarding.

## Required production setting

In **Supabase Dashboard → Authentication → Providers → Email**, turn **Confirm email** **off** for the `RTC Community Production` project (`pbzzfzfgwzwdstvnwzqu`). Save the provider settings before releasing the Android build.

In **Supabase Dashboard → Authentication → Settings → User Signups**, turn **Allow anonymous sign-ins** **on** for both `RTC Community Non-Production` (`eqwstpdjoineycrkhpht`) and `RTC Community Production` (`pbzzfzfgwzwdstvnwzqu`). Guests receive a real anonymous Supabase session so the existing RLS/RPC boundary can still identify and rate-limit a caller; they are not simulated local users.

This setting is required because the Android client treats a registration response without access and refresh tokens as a failed registration. When Confirm email is enabled, Supabase creates the account but deliberately withholds an active session until the email link is followed. That behavior caused the previous `Supabase did not return a user after sign-up` failure.

> Turning off Confirm email permits immediate access after a user supplies an email address and a valid password. This is the product policy requested for RTC Community, but it means email-address ownership is not verified at registration. Password recovery remains user-initiated and continues to use Supabase's normal recovery email.

## Application contract

The Android registration flow now reads the active `UserSession` that Supabase Auth installs immediately after `signUpWith(Email)` as the source of truth. It validates access and refresh tokens, starts session refresh, hydrates the authoritative profile, writes the session cache, and transitions directly into the authenticated app. If the production Auth setting is changed back to confirmation-required, registration fails safely with a clear session-establishment message instead of claiming success or leaving an infinite loading state.

## Release verification

After saving the provider settings, register one new non-privileged test account with a unique email address and start one guest session. The expected result is that the registered account enters the app immediately, survives an application restart, and can subsequently sign in using the same email and password. A guest should also enter the app without an `anonymous_provider_disabled` error. No confirmation email or code should be required for the registration path.
