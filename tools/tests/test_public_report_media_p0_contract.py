from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/feature/publicreports/data/SupabasePublicReportRepository.kt"
VIEW_MODEL = ROOT / "app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportComposerViewModel.kt"
MIGRATION = ROOT / "supabase/migrations/20260919093000_enforce_private_community_media_bucket.sql"
OUTBOX_MIGRATION = ROOT / "supabase/migrations/20260919101000_media_orphan_cleanup.sql"
CLEANUP_FUNCTION = ROOT / "supabase/functions/media-orphan-cleanup/index.ts"
ROOM = ROOT / "app/src/main/java/za/org/rtc/community/data/local/RtcDatabase.kt"
COMMUNITY_WORKER = ROOT / "app/src/main/java/za/org/rtc/community/data/local/CommunityUploadWorker.kt"


ALLOWED_MIME_TYPES = ("image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm")


def test_public_report_create_cannot_return_a_local_id_after_remote_failure():
    source = REPOSITORY.read_text(encoding="utf-8")
    create = source[source.index("override suspend fun create"):source.index("override suspend fun currentUserId")]
    assert "supabase.postgrest.rpc" in create
    assert ".decodeSingle<String>()" in create
    assert "runCatching {\n            supabase.postgrest.rpc" not in create
    assert 'val reportId = "report_${' not in create


def test_public_report_evidence_failure_is_durable_and_retryable():
    view_model = VIEW_MODEL.read_text(encoding="utf-8")
    repository = REPOSITORY.read_text(encoding="utf-8")
    for phase in ("DRAFT_CREATED", "UPLOADING", "PARTIAL_UPLOAD", "READY_FOR_REVIEW", "FAILED_RETRYABLE"):
        assert phase in view_model
    assert "enqueueEvidence" in view_model
    assert "resumeEvidence(reportId)" in view_model
    assert "publicReportEvidenceOutboxDao" in repository
    assert 'state = "RETRY"' in repository
    assert "Your files were kept; try publishing again." in view_model


def test_community_media_bucket_is_private_and_constrained_in_all_checked_in_contracts():
    migration = MIGRATION.read_text(encoding="utf-8")
    baseline = (ROOT / "supabase/migrations/20260825093000_reconstruct_community_core_baseline.sql").read_text(encoding="utf-8")
    for source in (migration, baseline):
        assert "rtc-community-media" in source
        assert "false" in source
        assert "20971520" in source
        for mime_type in ALLOWED_MIME_TYPES:
            assert mime_type in source


def test_public_report_outbox_and_stale_community_recovery_are_source_controlled():
    room = ROOM.read_text(encoding="utf-8")
    worker = COMMUNITY_WORKER.read_text(encoding="utf-8")
    assert "public_report_evidence_outbox" in room
    assert "state = 'UPLOADING' AND updatedAtEpochMillis < :staleBeforeEpochMillis" in room
    assert "STALE_UPLOADING_AFTER_MILLIS" in worker
    assert "5 * 60 * 1000L" in worker


def test_server_cleanup_is_bounded_authenticated_and_preserves_active_drafts():
    migration = OUTBOX_MIGRATION.read_text(encoding="utf-8")
    function = CLEANUP_FUNCTION.read_text(encoding="utf-8")
    assert "media_orphan_candidates" in migration
    assert "p_limit" in migration
    assert "least(coalesce(p_limit, 100), 500)" in migration
    assert "p.state = 'DRAFT'" in migration
    assert "verifySchedulerCaller" in function
    assert "Math.min(Math.trunc(requestedLimit), 500)" in function
    assert "service.storage.from(bucket).remove(paths)" in function
