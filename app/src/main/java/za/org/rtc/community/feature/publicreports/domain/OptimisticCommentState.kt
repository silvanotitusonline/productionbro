package za.org.rtc.community.feature.publicreports.domain

/** Local projection used while a comment request is pending. */
data class OptimisticCommentState(
    val comments: List<PublicReportComment>,
    val commentCount: Int,
) {
    fun addPending(comment: PublicReportComment, requestId: String): OptimisticCommentState {
        val pendingId = "comm_$requestId"
        if (comments.any { it.id == pendingId }) return this
        return copy(
            comments = comments + comment.copy(id = pendingId),
            commentCount = commentCount + 1,
        )
    }

    fun rollback(requestId: String): OptimisticCommentState {
        val pendingId = "comm_$requestId"
        val filtered = comments.filterNot { it.id == pendingId }
        if (filtered.size == comments.size) return this
        return copy(
            comments = filtered,
            commentCount = (commentCount - (comments.size - filtered.size)).coerceAtLeast(0),
        )
    }
}
