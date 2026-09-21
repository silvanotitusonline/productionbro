from release_contract_context import ROOT


REPOSITORY = (ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt").read_text(encoding="utf-8")
SYNC_ENGINE = (ROOT / "app/src/main/java/za/org/rtc/community/core/sync/SystemUpdateSyncEngine.kt").read_text(encoding="utf-8")
REFRESH_INDICATOR = (ROOT / "app/src/main/java/za/org/rtc/community/ui/components/SwipeRefreshIndicator.kt").read_text(encoding="utf-8")
PROFILE_SCREEN = (ROOT / "app/src/main/java/za/org/rtc/community/feature/account/ResidentProfileScreen.kt").read_text(encoding="utf-8")
ACCOUNT_HUB = (ROOT / "app/src/main/java/za/org/rtc/community/feature/account/AccountHubScreen.kt").read_text(encoding="utf-8")
NAV_GRAPH = (ROOT / "app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt").read_text(encoding="utf-8")
ANDROID_CI = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")


def test_pull_to_refresh_uses_standard_material_indicator_without_glow():
    assert "PullToRefreshDefaults.Indicator" in REFRESH_INDICATOR
    assert "radialGradient" not in REFRESH_INDICATOR
    assert "FBBF24" not in REFRESH_INDICATOR
    assert "rememberInfiniteTransition" not in REFRESH_INDICATOR


def test_live_content_refresh_is_coalesced_and_not_fifteen_second_polling():
    assert "startAutoSync(intervalMillis = 300_000L)" in REPOSITORY
    assert "intervalMillis: Long = 300_000L" in SYNC_ENGINE
    assert "liveContentRefreshMutex" in REPOSITORY
    assert "if (!liveContentRefreshMutex.tryLock()) return" in REPOSITORY
    assert "liveContentRefreshMutex.unlock()" in REPOSITORY
    assert "event !is za.org.rtc.community.core.sync.SystemUpdateSyncEngine.SystemUpdateEvent.ProfileUpdated" in REPOSITORY


def test_profile_photo_is_available_from_the_real_account_profile_route():
    assert "ActivityResultContracts.PickVisualMedia" in PROFILE_SCREEN
    assert "Choose profile photo" in PROFILE_SCREEN
    assert "Save photo" in PROFILE_SCREEN
    assert "Remove profile photo" in PROFILE_SCREEN
    assert "onUploadProfilePhoto" in PROFILE_SCREEN
    assert "onDeleteProfilePhoto" in PROFILE_SCREEN
    assert "onUploadProfilePhoto = viewModel::uploadProfilePhoto" in NAV_GRAPH
    assert "onDeleteProfilePhoto = viewModel::deleteProfilePhoto" in NAV_GRAPH
    assert "updateLocalProfilePhotoProjection()" in REPOSITORY
    assert "refreshLiveContent()" not in REPOSITORY[REPOSITORY.index("suspend fun uploadProfilePhoto"):REPOSITORY.index("private suspend fun updateLocalProfilePhotoProjection")]


def test_account_menu_exposes_light_dark_and_system_theme_controls():
    assert "ThemePreference.entries" in ACCOUNT_HUB
    assert 'ThemePreference.LIGHT -> "Light"' in ACCOUNT_HUB
    assert 'ThemePreference.DARK -> "Dark"' in ACCOUNT_HUB
    assert 'ThemePreference.SYSTEM -> "System"' in ACCOUNT_HUB
    assert "onSetTheme(preference)" in ACCOUNT_HUB


def test_ci_emulator_uses_a_bounded_disposable_userdata_partition():
    assert "-partition-size 2048" in ANDROID_CI
    assert "-no-snapshot-save" in ANDROID_CI
    assert "Reclaim runner disk for Android emulator userdata" in ANDROID_CI
    assert "sudo rm -rf /usr/share/dotnet /opt/ghc /usr/local/share/boost" in ANDROID_CI
    assert "docker system prune --all --force || true" in ANDROID_CI
    assert "continue-on-error: true" in ANDROID_CI
