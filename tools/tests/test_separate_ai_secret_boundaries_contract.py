from release_contract_context import ROOT


RESIDENT = (ROOT / "supabase/functions/rtc-resident-assistant/index.ts").read_text(encoding="utf-8")
ADMIN = (ROOT / "supabase/functions/rtc-admin-ai/index.ts").read_text(encoding="utf-8")


def test_resident_and_administration_ai_use_distinct_secret_names():
    assert 'Deno.env.get("RESIDENT_GEMINI_API_KEY")' in RESIDENT
    assert 'Deno.env.get("ADMIN_GEMINI_API_KEY")' in ADMIN
    assert 'Deno.env.get("GEMINI_API_KEY")' not in RESIDENT
    assert 'Deno.env.get("GEMINI_API_KEY")' not in ADMIN
    assert "ADMIN_GEMINI_API_KEY" not in RESIDENT
    assert "RESIDENT_GEMINI_API_KEY" not in ADMIN


def test_administration_ai_remains_guarded_and_bounded():
    assert 'Deno.env.get("ADMIN_GEMINI_MODEL") ?? "gemini-3.5-flash-lite"' in ADMIN
    assert 'Deno.env.get("ADMIN_GEMINI_FALLBACK_MODEL") ?? "gemini-3.6-flash"' in ADMIN
    assert "EXTERNAL_REQUEST_TIMEOUT_MS = 8_000" in ADMIN
    assert "fetchWithinTimeout" in ADMIN
    assert "generateProposalFromModel(ADMIN_GEMINI_MODEL" in ADMIN
    assert "generateProposalFromModel(ADMIN_GEMINI_FALLBACK_MODEL" in ADMIN
    assert "verifyCaller" in ADMIN
    assert "recaptchaToken" in ADMIN
    assert "PENDING_CONFIRMATION" in ADMIN
    assert "confirm_ai_proposal" in ADMIN


def test_android_client_never_contains_service_ai_keys():
    android_sources = (ROOT / "app/src").rglob("*.kt")
    forbidden = ("ADMIN_GEMINI_API_KEY", "RESIDENT_GEMINI_API_KEY")
    assert all(not any(key in path.read_text(encoding="utf-8") for key in forbidden) for path in android_sources)
