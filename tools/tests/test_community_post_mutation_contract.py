from release_contract_context import ROOT


SANITIZER_MIGRATION = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260921094205_restore_community_post_text_sanitizer.sql"
).read_text(encoding="utf-8")
BOOTSTRAP_MIGRATION = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260921094311_repair_anonymous_community_profile_bootstrap.sql"
).read_text(encoding="utf-8")


def test_community_post_sanitizer_is_restored_for_the_security_definer_draft_rpc():
    normalized = " ".join(SANITIZER_MIGRATION.split())
    assert "create or replace function public.sanitize_input_text(p_value text)" in normalized
    assert "returns text" in normalized
    assert "immutable" in normalized
    assert "set search_path = pg_catalog" in normalized
    assert "btrim(regexp_replace(coalesce(p_value, ''), '[[:cntrl:]]', '', 'g'))" in normalized
    assert "revoke all on function public.sanitize_input_text(text) from public, anon" in normalized
    assert "grant execute on function public.sanitize_input_text(text) to authenticated" in normalized


def test_anonymous_profile_bootstrap_does_not_retrigger_profile_updates_forever():
    normalized = " ".join(BOOTSTRAP_MIGRATION.split())
    assert "create or replace function private.ensure_community_profile_for_account(p_user_id uuid)" in normalized
    assert "on conflict (id) do update" in normalized
    assert "where public.profiles.display_name is distinct from excluded.display_name" in normalized
    assert "or public.profiles.avatar_url is not null" in normalized
    assert "where public.community_profiles.display_name is distinct from excluded.display_name" in normalized
    assert "or public.community_profiles.avatar_path is not null" in normalized
