package za.org.rtc.community.feature.publicreports.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OptimisticCommentStateTest {
    private val existing = comment("existing", "Already here")

    @Test
    fun `same client request id is idempotent`() {
        val initial = OptimisticCommentState(listOf(existing), commentCount = 1)
        val first = initial.addPending(comment("ignored", "Pending"), requestId = "request-1")
        val retry = first.addPending(comment("ignored-again", "Pending"), requestId = "request-1")

        assertEquals(2, first.comments.size)
        assertEquals(2, first.commentCount)
        assertEquals(first, retry)
        assertEquals(1, retry.comments.count { it.id == "comm_request-1" })
    }

    @Test
    fun `rollback removes only the pending request and restores its count`() {
        val initial = OptimisticCommentState(listOf(existing), commentCount = 1)
        val optimistic = initial.addPending(comment("ignored", "Pending"), requestId = "request-2")
        val rolledBack = optimistic.rollback("request-2")

        assertEquals(initial, rolledBack)
        assertEquals(listOf(existing), rolledBack.comments)
        assertEquals(1, rolledBack.commentCount)
    }

    @Test
    fun `rollback of an unknown request is a no op`() {
        val initial = OptimisticCommentState(listOf(existing), commentCount = 1)

        assertSame(initial, initial.rollback("missing"))
    }

    private fun comment(id: String, body: String) = PublicReportComment(
        id = id,
        reportId = "report-1",
        authorDisplayName = "Resident",
        body = body,
        createdAt = Instant.parse("2026-09-20T18:00:00Z"),
    )
}
