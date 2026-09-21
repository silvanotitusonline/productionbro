from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VIEW_MODEL = ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/ui/DailyPostViewModel.kt"
REPOSITORY = ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/data/DailyPostRepository.kt"
SCREEN = ROOT / "app/src/main/java/za/org/rtc/community/feature/dailypost/ui/DailyPostDetailScreen.kt"
NAV = ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt"


def test_manual_refresh_restarts_an_unavailable_daily_post_subscription():
    source = VIEW_MODEL.read_text(encoding="utf-8")
    refresh_start = source.index("fun refreshComments")
    refresh_body = source[refresh_start:source.index("fun observeComments", refresh_start)]
    assert "LiveUpdateStatus.UNAVAILABLE" in refresh_body
    assert "observeComments(articleId)" in refresh_body
    assert "loadCommentsInternal(articleId, showLoading = false, reset = true)" in refresh_body


def test_daily_post_realtime_collection_is_owned_by_callback_flow_lifecycle():
    source = REPOSITORY.read_text(encoding="utf-8")
    start = source.index("override fun observeCommentChanges")
    body = source[start:source.index("override suspend fun createComment", start)]
    assert "callbackFlow" in body
    assert "val job = launch(Dispatchers.IO)" in body
    assert "awaitClose" in body
    assert "job.cancel()" in body
    assert "channel.unsubscribe()" in body
    assert "CoroutineScope(Dispatchers.IO)" not in body


def test_daily_post_unavailable_state_exposes_a_manual_refresh_action():
    source = SCREEN.read_text(encoding="utf-8")
    assert "Live updates unavailable. Comments may be stale." in source
    assert "Button(onClick = onRefreshComments)" in source
    navigation = NAV.read_text(encoding="utf-8")
    assert "onRefreshComments = { dailyPostViewModel.refreshComments(articleId) }" in navigation
