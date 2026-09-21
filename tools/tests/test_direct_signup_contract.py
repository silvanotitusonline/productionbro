from release_contract_context import ROOT


REPOSITORY = (ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt").read_text(encoding="utf-8")
COORDINATOR = (ROOT / "app/src/main/java/za/org/rtc/community/app/RtcAuthenticationCoordinator.kt").read_text(encoding="utf-8")
WELCOME = (ROOT / "app/src/main/java/za/org/rtc/community/feature/account/PublicWelcomeScreen.kt").read_text(encoding="utf-8")


def test_signup_uses_immediate_authenticated_session_instead_of_current_user_state():
    signup = REPOSITORY[REPOSITORY.index("suspend fun signUpWithEmail"):REPOSITORY.index("suspend fun requestPasswordRecovery")]
    assert "supabase.auth.signUpWith(Email)" in signup
    assert "val registeredSession = requireNotNull(supabase.auth.currentSessionOrNull())" in signup
    assert "val userId = requireNotNull(registeredSession.user?.id)" in signup
    assert "registeredSession.accessToken.isNotBlank()" in signup
    assert "registeredSession.refreshToken.isNotBlank()" in signup
    assert "supabase.auth.startAutoRefreshForCurrentSession()" in signup
    assert "currentUserOrNull" not in signup
    assert "redirectUrl = \"rtc://community\"" not in signup


def test_signup_requires_immediate_session_and_hydrates_authoritative_identity():
    signup = REPOSITORY[REPOSITORY.index("suspend fun signUpWithEmail"):REPOSITORY.index("suspend fun requestPasswordRecovery")]
    assert "Standard password registration requires Supabase Auth email confirmation to be disabled" in REPOSITORY
    assert "hydrateSupabaseSession()" in signup
    assert "Account registration did not establish a usable session" in signup
    assert "database.cachedSessionDao().upsertSession" in signup


def test_signup_success_completes_authenticated_application_flow_without_confirmation_copy():
    coordinator_signup = COORDINATOR[COORDINATOR.index("fun signUpWithEmail"):COORDINATOR.index("fun dismissAuthenticationMessage")]
    assert "completeAuthentication()" in coordinator_signup
    assert "confirmationRequired = true" not in coordinator_signup
    assert "confirmation email" not in coordinator_signup.lower()
    assert "start using it straight away" in WELCOME
