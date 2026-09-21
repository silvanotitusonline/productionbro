from release_contract_context import ROOT


APP_MODULE = (ROOT / "app/src/main/java/za/org/rtc/community/di/AppModule.kt").read_text()
NETWORK = (ROOT / "app/src/main/java/za/org/rtc/community/core/network/NetworkResilience.kt").read_text()

REMOTE_REPOSITORIES = [
    "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt",
    "app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt",
    "app/src/main/java/za/org/rtc/community/feature/community/SupabaseCommunityRepository.kt",
    "app/src/main/java/za/org/rtc/community/feature/publicreports/data/SupabasePublicReportRepository.kt",
    "app/src/main/java/za/org/rtc/community/feature/publicreports/data/PublicReportEvidenceClient.kt",
    "app/src/main/java/za/org/rtc/community/feature/marketplace/data/remote/SupabaseMarketplaceRepository.kt",
    "app/src/main/java/za/org/rtc/community/feature/marketplace/data/remote/MarketplaceProductionRepository.kt",
    "app/src/main/java/za/org/rtc/community/feature/servicecentre/data/remote/SupabaseServiceCentreRepository.kt",
    "app/src/main/java/za/org/rtc/community/feature/dailypost/data/DailyPostRepository.kt",
    "app/src/main/java/za/org/rtc/community/data/ui_config/UiConfigurationRepository.kt",
    "app/src/main/java/za/org/rtc/community/feature/events/data/remote/SupabaseCommunityEventsRepository.kt",
    "app/src/main/java/za/org/rtc/community/feature/inbox/data/remote/SupabaseResidentInboxRepository.kt",
]


def test_supabase_client_has_transport_timeout_ceiling():
    assert "requestTimeout = NetworkResilience.MEDIA_TIMEOUT_MS.milliseconds" in APP_MODULE
    assert "install(Auth)" in APP_MODULE
    assert "install(Postgrest)" in APP_MODULE
    assert "install(Storage)" in APP_MODULE
    assert "install(Functions)" in APP_MODULE


def test_network_helper_preserves_external_cancellation_and_bounds_results():
    assert "const val STANDARD_TIMEOUT_MS = 10_000L" in NETWORK
    assert "const val MEDIA_TIMEOUT_MS = 30_000L" in NETWORK
    assert "suspend fun <T> standardResult" in NETWORK
    assert "suspend fun <T> mediaResult" in NETWORK
    assert "catch (error: TimeoutCancellationException)" in NETWORK
    assert "catch (error: CancellationException)" in NETWORK
    assert "throw error" in NETWORK


def test_remote_repositories_use_deadline_aware_boundary():
    for relative_path in REMOTE_REPOSITORIES:
        source = (ROOT / relative_path).read_text()
        assert "import za.org.rtc.community.core.network.NetworkResilience" in source, relative_path
        assert (
            "NetworkResilience.standard" in source or "NetworkResilience.media" in source
        ), relative_path


def test_media_transfer_paths_use_the_longer_budget():
    expectations = {
        "app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt": [
            "uploadFeedbackScreenshot(uri: Uri): Result<String> = NetworkResilience.mediaResult",
            "uploadProfilePhoto(uri: Uri, userId: String): Result<String> = NetworkResilience.mediaResult",
        ],
        "app/src/main/java/za/org/rtc/community/feature/publicreports/data/SupabasePublicReportRepository.kt": [
            "uploadEvidenceBytes(storagePath: String, bytes: ByteArray, mimeType: String): Result<Unit> = NetworkResilience.mediaResult",
        ],
        "app/src/main/java/za/org/rtc/community/feature/marketplace/data/remote/SupabaseMarketplaceRepository.kt": [
            "onProgress: (MarketplaceMediaStage, Float) -> Unit,\n    ): Result<MarketplaceMediaAsset> = NetworkResilience.mediaResult",
        ],
        "app/src/main/java/za/org/rtc/community/data/ui_config/UiConfigurationRepository.kt": [
            "height: Int,\n    ): Result<String> = NetworkResilience.mediaResult",
        ],
    }
    for relative_path, fragments in expectations.items():
        source = (ROOT / relative_path).read_text()
        for fragment in fragments:
            assert fragment in source, f"{relative_path}: {fragment}"


def test_profile_confirmation_is_not_rolled_back_by_refresh_work():
    source = (ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt").read_text()
    start = source.index("suspend fun updateProfile")
    end = source.index("suspend fun setNotificationPreference", start)
    profile = source[start:end]
    assert "val persistedProfile = NetworkResilience.standard" in profile
    assert "profilePersistenceConfirmed = true" in profile
    assert "repositoryScope.launch" in profile
    assert "if (!profilePersistenceConfirmed)" in profile
    assert "applyLocalProfile(previousSession)" in profile
