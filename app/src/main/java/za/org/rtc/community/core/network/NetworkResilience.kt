package za.org.rtc.community.core.network

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Hard bounds for all remote work. Media uses the longer budget; ordinary requests do not. */
object NetworkResilience {
    const val STANDARD_TIMEOUT_MS = 10_000L
    const val MEDIA_TIMEOUT_MS = 30_000L

    suspend fun <T> standard(block: suspend () -> T): T = within(STANDARD_TIMEOUT_MS, block)

    suspend fun <T> media(block: suspend () -> T): T = within(MEDIA_TIMEOUT_MS, block)

    internal suspend fun <T> within(timeoutMillis: Long, block: suspend () -> T): T =
        withTimeout(timeoutMillis) { block() }

    /**
     * Keeps repository APIs Result-based while enforcing the standard request budget before any
     * caller can leave loading state unresolved.
     */
    suspend fun <T> standardResult(block: suspend () -> T): Result<T> = resultOf { standard(block) }

    /** Same Result boundary as [standardResult], with the media-transfer budget. */
    suspend fun <T> mediaResult(block: suspend () -> T): Result<T> = resultOf { media(block) }

    private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: TimeoutCancellationException) {
        Result.failure(error)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    fun message(error: Throwable, action: String): String = when (error) {
        is TimeoutCancellationException -> "Connection timed out. $action"
        is IOException -> "No network connection. $action"
        else -> action
    }
}
