from release_contract_context import ROOT


SOURCE = (ROOT / "supabase/functions/rtc-resident-assistant/index.ts").read_text(encoding="utf-8")
POLICY = (ROOT / "docs/EDGE_FUNCTION_JWT_POLICY.md").read_text(encoding="utf-8")


def test_resident_assistant_uses_server_side_gemini_only():
    assert 'Deno.env.get("RESIDENT_GEMINI_API_KEY")' in SOURCE
    assert 'Deno.env.get("RESIDENT_GEMINI_MODEL") ?? "gemini-3.5-flash-lite"' in SOURCE
    assert 'Deno.env.get("RESIDENT_GEMINI_FALLBACK_MODEL") ?? "gemini-3.6-flash"' in SOURCE
    assert 'Deno.env.get("GEMINI_API_KEY")' not in SOURCE
    assert "generativelanguage.googleapis.com" in SOURCE
    assert "OPENAI_API_KEY" not in SOURCE
    assert "api.openai.com" not in SOURCE
    android_sources = (ROOT / "app/src").rglob("*.kt")
    assert all("GEMINI_API_KEY" not in path.read_text(encoding="utf-8") for path in android_sources)


def test_resident_assistant_has_bounded_authenticated_requests():
    assert "verify_jwt" not in SOURCE  # verification remains a deployment concern
    assert 'authorization.startsWith("Bearer ")' in SOURCE
    assert "MAX_REQUEST_BYTES = 8_192" in SOURCE
    assert "MAX_MESSAGE_LENGTH = 2_000" in SOURCE
    assert "MAX_REQUESTS_PER_MINUTE = 12" in SOURCE
    assert 'claim_ai_rate_limit' in SOURCE
    assert "MODEL_TIMEOUT_MS = 8_000" in SOURCE
    assert "AbortController" in SOURCE
    assert "generateAnswerFromModel(RESIDENT_GEMINI_MODEL" in SOURCE
    assert "generateAnswerFromModel(RESIDENT_GEMINI_FALLBACK_MODEL" in SOURCE
    assert "clearTimeout(timer)" in SOURCE


def test_resident_assistant_accepts_current_publishable_key_sessions():
    assert 'Deno.env.get("SUPABASE_PUBLISHABLE_KEYS")' in SOURCE
    assert "const SUPABASE_PUBLISHABLE_KEY = resolvePublishableKey()" in SOURCE
    assert 'authorization.slice("Bearer ".length)' in SOURCE
    assert "userClient.auth.getUser(accessToken)" in SOURCE


def test_resident_assistant_returns_safe_ui_state_on_provider_failure():
    assert "responseMimeType" in SOURCE
    assert 'return json(200, await generateAnswer' in SOURCE
    assert 'return json(200, fallback(message, []));' in SOURCE
    assert "isLoading = false" in (ROOT / "app/src/main/java/za/org/rtc/community/feature/support/ResidentAssistantViewModel.kt").read_text(encoding="utf-8")


def test_resident_assistant_is_documented_as_jwt_protected():
    assert "rtc-resident-assistant" in POLICY
