package za.org.rtc.community.data.local

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import za.org.rtc.community.feature.publicreports.domain.PublicReportRepository

/** Resumes durable Public Report evidence after process, network, or finalization failure. */
@HiltWorker
class PublicReportEvidenceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: PublicReportRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        repository.resumeEvidence()
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    companion object {
        const val WORK_NAME = "rtc-public-report-evidence-recovery"
    }
}
