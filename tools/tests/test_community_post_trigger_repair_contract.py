from release_contract_context import ROOT


MIGRATION = (
    ROOT
    / "supabase"
    / "migrations"
    / "20260921092855_repair_community_post_search_trigger.sql"
)


def test_community_post_search_trigger_uses_existing_post_columns_only():
    source = MIGRATION.read_text(encoding="utf-8")
    compact = "".join(source.lower().split())
    function_body = source.lower().split("as $$", maxsplit=1)[1]

    assert source.lstrip().lower().startswith("begin;")
    assert source.rstrip().lower().endswith("commit;")
    assert "createorreplacefunctionpublic.community_posts_search_trigger()" in compact
    assert "new.search_vector:=to_tsvector('english',coalesce(new.body,''));" in compact
    assert "new.author_name" not in function_body
    assert "returnnew;" in compact


def test_community_post_search_trigger_repair_does_not_remove_search_maintenance():
    source = MIGRATION.read_text(encoding="utf-8").lower()

    assert "drop trigger" not in source
    assert "drop function" not in source
    assert "search_vector" in source
