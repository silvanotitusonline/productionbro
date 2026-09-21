from release_contract_context import ROOT


MIGRATION = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260921095931_restore_community_draft_runtime_compatibility.sql"
).read_text(encoding="utf-8")
CLIENT = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "za"
    / "org"
    / "rtc"
    / "community"
    / "supabase"
    / "ProductionUxRepository.kt"
).read_text(encoding="utf-8")
HANDLE_MIGRATION = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260921100121_ensure_unique_anonymous_community_handle.sql"
).read_text(encoding="utf-8")


def test_runtime_draft_rpc_accepts_the_android_client_idempotency_key():
    normalized = " ".join(MIGRATION.split())
    assert "add column if not exists client_post_id uuid" in normalized
    assert "community_posts_author_client_post_id_uq" in normalized
    assert "create or replace function public.create_community_post_draft( p_body text default '', p_client_post_id uuid default null )" in normalized
    assert "and client_post_id = p_client_post_id" in normalized
    assert "grant execute on function public.create_community_post_draft(text, uuid) to authenticated, service_role" in normalized
    assert 'put("p_client_post_id", clientPostId)' in CLIENT


def test_anonymous_runtime_compatibility_is_explicit_and_idempotent():
    normalized = " ".join(MIGRATION.split())
    handle_normalized = " ".join(HANDLE_MIGRATION.split())
    assert "create or replace function private.is_anonymous_guest()" in normalized
    assert "coalesce(auth.jwt() ->> 'is_anonymous', 'false') = 'true'" in normalized
    assert "select private.is_anonymous_guest() or exists" in normalized
    assert "where public.profiles.display_name is distinct from excluded.display_name" in normalized
    assert "or public.profiles.avatar_url is not null" in normalized
    assert "v_anonymous_handle text := 'anonymous_' || replace(left(p_user_id::text, 12), '-', '')" in handle_normalized
    assert "'Anonymous', v_anonymous_handle, null" in handle_normalized
