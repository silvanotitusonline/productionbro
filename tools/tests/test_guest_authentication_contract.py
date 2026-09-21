from release_contract_context import ROOT


CONFIG = (ROOT / "supabase/config.toml").read_text(encoding="utf-8")
COORDINATOR = (ROOT / "app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt").read_text(encoding="utf-8")
REPOSITORY = (ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt").read_text(encoding="utf-8")
SAFE_ERROR = (ROOT / "app/src/main/java/za/org/rtc/community/app/SafeUiError.kt").read_text(encoding="utf-8")


def test_local_auth_configuration_supports_direct_and_anonymous_sessions():
    assert "[auth]" in CONFIG
    assert "enable_signup = true" in CONFIG
    assert "enable_anonymous_sign_ins = true" in CONFIG
    assert "[auth.email]" in CONFIG
    assert "enable_confirmations = false" in CONFIG


def test_guest_flow_uses_real_bounded_supabase_session_and_safe_ui_copy():
    guest = REPOSITORY[REPOSITORY.index("suspend fun continueAsGuest"):REPOSITORY.index("fun setRole")]
    assert "NetworkResilience.standard { supabase.auth.signInAnonymously() }" in guest
    assert "startAutoRefreshForCurrentSession()" in guest
    assert "hydrateSupabaseSession()" in guest
    coordinator_guest = COORDINATOR[COORDINATOR.index("fun continueAsGuest"):COORDINATOR.index("fun switchRole")]
    assert "SafeUiError.guestAccess(error)" in coordinator_guest


def test_guest_and_generic_errors_do_not_expose_provider_diagnostics():
    assert "fun guestAccess(error: Throwable)" in SAFE_ERROR
    assert "anonymous_provider_disabled" in SAFE_ERROR
    assert "Guest access is temporarily unavailable." in SAFE_ERROR
    assert 'else -> fallback' in SAFE_ERROR
    assert '"$fallback (${error.javaClass.simpleName}: $detail)"' not in SAFE_ERROR
