from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
HOME = ROOT / "app/src/main/java/za/org/rtc/community/feature/home/HomeResidentModernisationCards.kt"
COMPOSER_VM = ROOT / "app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportComposerViewModel.kt"
COMPOSER_SCREEN = ROOT / "app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportComposerScreen.kt"
DETAIL_VM = ROOT / "app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportViewModel.kt"
DETAIL_SCREEN = ROOT / "app/src/main/java/za/org/rtc/community/feature/publicreports/presentation/PublicReportDetailScreen.kt"


def test_home_snapshot_names_the_public_report_domain_and_excludes_community_posts():
    source = HOME.read_text(encoding="utf-8")
    assert 'Text("Public Reports snapshot"' in source
    assert "Civic service-request summary. Community posts are not included." in source
    assert 'contentDescription = "Public Reports snapshot.' in source


def test_public_report_children_distinguish_empty_from_unavailable():
    view_model = DETAIL_VM.read_text(encoding="utf-8")
    screen = DETAIL_SCREEN.read_text(encoding="utf-8")
    assert "enum class PublicReportChildLoadState" in view_model
    for field in ("evidenceState", "timelineState", "commentsState"):
        assert field in view_model
    assert "isFailure -> PublicReportChildLoadState.UNAVAILABLE" in view_model
    assert "PublicReportChildLoadState.EMPTY" in screen
    assert "temporarily unavailable" in screen
    assert "Try again" in screen


def test_contact_permission_is_user_controlled_and_submitted_to_the_draft():
    view_model = COMPOSER_VM.read_text(encoding="utf-8")
    screen = COMPOSER_SCREEN.read_text(encoding="utf-8")
    assert "val contactPermission: Boolean = false" in view_model
    assert "fun setContactPermission(value: Boolean)" in view_model
    assert "contactPermission = current.contactPermission" in view_model
    assert "Allow the municipality to contact me about this report" in screen
    assert "onCheckedChange = viewModel::setContactPermission" in screen
    assert "Your contact permission is private" in screen
