package za.org.rtc.community.data.local

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import za.org.rtc.community.supabase.ProductionUxRepository

/** Recovers durable upload rows after process/network interruption. */
@HiltWorker
class CommunityUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val database: RtcDatabase,
    private val production: ProductionUxRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        // The durable outbox is account-scoped. Never resume media when no verified owner is active.
        val ownerUserId = production.currentAuthenticatedUserIdOrNull() ?: return Result.success()
        val staleBefore = System.currentTimeMillis() - STALE_UPLOADING_AFTER_MILLIS
        val pending = database.uploadOutboxDao().pendingForOwner(ownerUserId)
        val staleUploading = database.uploadOutboxDao().staleUploadingForOwner(ownerUserId, staleBefore)
        val pendingDraftIds = pending.map { it.draftId }.distinct()
        val recoverableDraftIds = (pendingDraftIds + staleUploading.map { it.draftId }).distinct()
        if (recoverableDraftIds.isEmpty()) return Result.success()
        var retryNeeded = false
        recoverableDraftIds.forEach { draftId ->
            production.resumeCommunityUpload(draftId)
                .onFailure { retryNeeded = true }
        }
        return if (retryNeeded) Result.retry() else Result.success()
    }

    private companion object {
        const val STALE_UPLOADING_AFTER_MILLIS = 5 * 60 * 1000L
    }
}
