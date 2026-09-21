from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VIEW_MODEL = ROOT / "app/src/main/java/za/org/rtc/community/feature/community/CommunityViewModel.kt"
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/feature/community/SupabaseCommunityRepository.kt"
TESTS = ROOT / "app/src/test/java/za/org/rtc/community/feature/community/CommunityViewModelTest.kt"


def test_realtime_refresh_is_lifecycle_owned_debounced_and_deduplicated():
    source = VIEW_MODEL.read_text(encoding="utf-8")
    assert "viewModelScope.launch" in source
    assert "observeCommunityFeedRealtime()" in source
    assert ".distinctUntilChanged()" in source
    assert ".debounce(250)" in source
    assert "refreshFeed(initial = false)" in source
    assert "Realtime is a refresh signal, not a second source of truth." in source


def test_realtime_channel_closes_when_the_flow_collector_is_cancelled():
    source = REPOSITORY.read_text(encoding="utf-8")
    start = source.index("override fun observeCommunityFeedRealtime")
    body = source[start:]
    assert "callbackFlow" in body
    assert "awaitClose" in body
    assert "job.cancel()" in body
    assert "channel.unsubscribe()" in body


def test_realtime_behavior_has_duplicate_event_regression_coverage():
    source = TESTS.read_text(encoding="utf-8")
    assert "realtime insert triggers one debounced authoritative refresh" in source
    assert "realtimeEvents.tryEmit(\"new\")" in source
    assert "advanceTimeBy(251)" in source
    assert "assertEquals(2, repository.requests.size)" in source
