package za.org.rtc.community.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class NetworkResilienceTest {
    @Test
    fun `standard and media timeout budgets match product contract`() {
        assertEquals(10_000L, NetworkResilience.STANDARD_TIMEOUT_MS)
        assertEquals(30_000L, NetworkResilience.MEDIA_TIMEOUT_MS)
    }

    @Test
    fun `user safe messages hide transport details`() {
        assertEquals(
            "No network connection. Comment was not posted.",
            NetworkResilience.message(IOException("socket details"), "Comment was not posted."),
        )
        assertEquals("Try again.", NetworkResilience.message(IllegalStateException("database details"), "Try again."))
    }

    @Test
    fun `timeout result captures a timeout inside the request boundary`() = runBlocking {
        val result = NetworkResilience.standardResult {
            NetworkResilience.within(1) {
                delay(20)
                "unreachable"
            }
        }

        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
    }

    @Test(expected = CancellationException::class)
    fun `external cancellation is never converted into a failure result`() {
        runBlocking {
            NetworkResilience.standardResult<String> {
                throw CancellationException("screen left composition")
            }
        }
    }
}
