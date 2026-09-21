package za.org.rtc.community.feature.community

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.core.CommunityRealtimeNotification
import za.org.rtc.community.core.auth.SupabaseUserIdentity
import za.org.rtc.community.core.MediaItem
import za.org.rtc.community.core.MediaKind
import za.org.rtc.community.core.MediaTargetType
import za.org.rtc.community.data.local.CachedCommentDao
import za.org.rtc.community.data.local.CachedCommentEntity
import za.org.rtc.community.data.local.CachedPostDao
import za.org.rtc.community.data.local.CachedPostEntity
import za.org.rtc.community.core.network.NetworkResilience

@Singleton
class SupabaseCommunityRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val cachedPostDao: CachedPostDao,
    private val cachedCommentDao: CachedCommentDao,
    private val syncEngine: za.org.rtc.community.core.sync.SystemUpdateSyncEngine = za.org.rtc.community.core.sync.SystemUpdateSyncEngine(),
    @ApplicationContext private val context: Context,
) : CommunityRepository {
    private val clock = Clock.systemUTC()
    private val signedUrlCache = SignedUrlCache(
        capacity = SIGNED_URL_CACHE_CAPACITY,
        refreshSkew = Duration.ofSeconds(SIGNED_URL_REFRESH_SKEW_SECONDS),
        clock = clock,
    )
    private val mediaPaths = ConcurrentHashMap<String, String>()

    suspend fun addLocalPost(post: CommunityPost) {
        cachedPostDao.insertPost(post.toCachedEntity())
        syncEngine.triggerSystemWideUpdate(
            za.org.rtc.community.core.sync.SystemUpdateSyncEngine.SystemUpdateEvent.PostCreated(post.id, post.author)
        )
    }

    override suspend fun loadFeedPage(
        cursor: CommunityCursor?,
        limit: Int,
    ): Result<CommunityFeedPage> = NetworkResilience.standardResult {
        val visibleLimit = limit.coerceIn(1, MAX_VISIBLE_PAGE_SIZE)
        val serverLimit = (visibleLimit + 1).coerceAtMost(MAX_SERVER_PAGE_SIZE)

        val remotePosts: List<CommunityPost> = NetworkResilience.standardResult {
            val rows = supabase.postgrest.rpc(
                function = "community_post_page_v3",
                parameters = buildJsonObject {
                    cursor?.let {
                        put("p_before_created_at", it.createdAt)
                        put("p_before_id", it.id)
                    }
                    put("p_limit", serverLimit)
                },
            ).decodeList<CommunityFeedRow>()
            coroutineScope {
                rows.map { row -> async { row.toCommunityPost() } }.awaitAll()
            }
        }.getOrElse {
            runCatching {
                val rows = supabase.postgrest.rpc(
                    function = "community_post_page_v2",
                    parameters = buildJsonObject {
                        cursor?.let {
                            put("p_before_created_at", it.createdAt)
                            put("p_before_id", it.id)
                        }
                        put("p_limit", serverLimit)
                    },
                ).decodeList<CommunityFeedRow>()
                coroutineScope {
                    rows.map { row -> async { row.toCommunityPost() } }.awaitAll()
                }
            }.getOrElse {
                // Range query fallback using Supabase range
                runCatching {
                    val rows = supabase.from("community_post_feed")
                        .select {
                            order(column = "created_at", order = Order.DESCENDING)
                            range(0, (serverLimit - 1).toLong())
                        }.decodeList<CommunityFeedRow>()
                    coroutineScope {
                        rows.map { row -> async { row.toCommunityPost() } }.awaitAll()
                    }
                }.getOrDefault(emptyList())
            }
        }

        if (remotePosts.isNotEmpty()) {
            cachedPostDao.insertPosts(remotePosts.map { it.toCachedEntity() })
        }

        val allCached = cachedPostDao.getAllPosts().map { it.toCommunityPost() }
        val finalPosts = if (cursor != null) {
            allCached.filter { it.createdAt < cursor.createdAt || (it.createdAt == cursor.createdAt && it.id < cursor.id) }
        } else {
            allCached
        }
        buildCommunityFeedPage(posts = finalPosts, visibleLimit = visibleLimit)
    }

    override suspend fun loadPost(postId: String): Result<CommunityPost?> = NetworkResilience.standardResult {
        val remotePost = NetworkResilience.standardResult {
            val row = supabase.from("community_post_feed").select {
                filter { eq("id", postId) }
                limit(1)
            }.decodeList<CommunityFeedRow>().firstOrNull()
            row?.toCommunityPost()
        }.getOrNull()

        if (remotePost != null) {
            cachedPostDao.insertPost(remotePost.toCachedEntity())
            remotePost
        } else {
            cachedPostDao.getPostById(postId)?.toCommunityPost()
        }
    }

    override suspend fun loadComments(postId: String): Result<List<CommunityComment>> = NetworkResilience.standardResult {
        val remoteComments = NetworkResilience.standardResult {
            val rows = supabase.from("community_comment_feed").select {
                filter { eq("post_id", postId) }
                order(column = "created_at", order = Order.ASCENDING)
            }.decodeList<CommunityCommentRow>()
            coroutineScope {
                rows.map { row -> async { row.toCommunityComment() } }.awaitAll()
            }
        }.getOrDefault(emptyList())

        if (remoteComments.isNotEmpty()) {
            cachedCommentDao.insertComments(remoteComments.map { it.toCachedEntity() })
        }

        cachedCommentDao.getCommentsForPost(postId).map { it.toCommunityComment() }
    }

    override suspend fun createComment(postId: String, body: String, parentId: String?): Result<Unit> = NetworkResilience.standardResult {
        val cleanBody = body.trim()
        require(cleanBody.length in 1..280) { "A comment must contain 1 to 280 characters." }

        val newComment = CommunityComment(
            id = "comment_${UUID.randomUUID()}",
            postId = postId,
            authorId = supabase.auth.currentUserOrNull()?.id ?: "anonymous",
            author = currentUserDisplayName(),
            handle = currentUserHandle(),
            content = cleanBody,
            createdAt = Instant.now(clock).toString(),
            parentId = parentId,
        )
        cachedCommentDao.insertComment(newComment.toCachedEntity())

        // Update comment count on cached post
        val existingPost = cachedPostDao.getPostById(postId)
        if (existingPost != null) {
            cachedPostDao.insertPost(existingPost.copy(comments = existingPost.comments + 1))
        }

        try {
            NetworkResilience.standard {
                supabase.postgrest.rpc(
                function = "create_community_comment",
                parameters = buildJsonObject {
                    put("p_post_id", postId)
                    put("p_body", cleanBody)
                    parentId?.let { put("p_parent_id", it) }
                    },
                ).decodeSingle<String>()
            }
        } catch (error: Throwable) {
            cachedCommentDao.deleteComment(newComment.id)
            if (existingPost != null) {
                cachedPostDao.insertPost(existingPost)
            }
            throw error
        }
        Unit
    }

    private fun currentUserDisplayName(): String {
        val user = supabase.auth.currentUserOrNull() ?: return "Community member"
        if (user.email.isNullOrBlank()) return "Anonymous"
        return SupabaseUserIdentity.displayName(user.userMetadata, user.email, user.id)
    }

    private fun currentUserHandle(): String {
        val user = supabase.auth.currentUserOrNull() ?: return "@member"
        if (user.email.isNullOrBlank()) return "@anonymous"
        return SupabaseUserIdentity.handle(user.userMetadata, user.email, user.id)
    }

    override suspend fun updateComment(commentId: String, body: String): Result<Unit> = NetworkResilience.standardResult {
        val cleanBody = body.trim()
        require(cleanBody.length in 1..280) { "A comment must contain 1 to 280 characters." }

        // Find and update comment in Room
        // Fetch all comments and update matching ID
        runCatching {
            supabase.from("community_comments").update(CommunityCommentChangePayload(body = cleanBody)) {
                filter { eq("id", commentId) }
            }
        }
        Unit
    }

    override suspend fun deleteComment(commentId: String): Result<Unit> = NetworkResilience.standardResult {
        cachedCommentDao.deleteComment(commentId)
        syncEngine.triggerSystemWideUpdate(
            za.org.rtc.community.core.sync.SystemUpdateSyncEngine.SystemUpdateEvent.CommentDeleted(commentId)
        )
        runCatching {
            supabase.from("community_comments").delete {
                filter { eq("id", commentId) }
            }
        }.onFailure {
            supabase.from("community_comments").update(
                CommunityCommentChangePayload(
                    state = "DELETED_BY_AUTHOR",
                    deletedAt = Instant.now(clock).toString(),
                )
            ) {
                filter { eq("id", commentId) }
            }
        }
        Unit
    }

    override suspend fun updatePost(postId: String, body: String, category: String): Result<Unit> = NetworkResilience.standardResult {
        val cleanBody = body.trim()
        require(cleanBody.length in 1..280) { "A post must contain 1 to 280 characters." }
        supabase.postgrest.rpc(
            function = "edit_community_post",
            parameters = buildJsonObject {
                put("p_post_id", postId)
                put("p_body", cleanBody)
                put("p_category_slug", category)
            },
        )
        val cached = cachedPostDao.getPostById(postId)
        if (cached != null) {
            cachedPostDao.insertPost(cached.copy(content = cleanBody))
        }
        Unit
    }

    override suspend fun deletePost(postId: String): Result<Unit> = NetworkResilience.standardResult {
        val cachedPost = cachedPostDao.getPostById(postId)
        if (cachedPost?.isPendingSync == true) {
            // A previous build could retain an optimistic placeholder after a rejected publish.
            // It has no server ID and must be removable locally rather than sent to the owner-only
            // deletion RPC, which correctly cannot find it.
            cachedPostDao.deletePost(postId)
            cachedCommentDao.deleteCommentsForPost(postId)
            syncEngine.triggerSystemWideUpdate(
                za.org.rtc.community.core.sync.SystemUpdateSyncEngine.SystemUpdateEvent.PostDeleted(postId)
            )
        } else {
            supabase.postgrest.rpc(
                function = "delete_community_post",
                parameters = buildJsonObject { put("p_post_id", postId) },
            )
            // The RPC is the source of truth. Remove all local projections only
            // after it succeeds so a failed request cannot hide a live post.
            cachedPostDao.deletePost(postId)
            cachedCommentDao.deleteCommentsForPost(postId)
            syncEngine.triggerSystemWideUpdate(
                za.org.rtc.community.core.sync.SystemUpdateSyncEngine.SystemUpdateEvent.PostDeleted(postId)
            )
        }
        Unit
    }

    override suspend fun moderateComment(commentId: String, reason: String): Result<Unit> = NetworkResilience.standardResult {
        val cleanReason = reason.trim()
        require(cleanReason.length in 3..1_000) {
            "A moderation reason must contain 3 to 1,000 characters."
        }
        cachedCommentDao.deleteComment(commentId)
        runCatching {
            supabase.postgrest.rpc(
                function = "moderate_community_comment_v1",
                parameters = buildJsonObject {
                    put("p_comment_id", commentId)
                    put("p_reason", cleanReason)
                },
            ).decodeSingle<Boolean>()
        }
        Unit
    }

    override suspend fun toggleLike(postId: String): Result<CommunityLikeOutcome> = NetworkResilience.standardResult {
        var localLiked = false
        var localReactions = 0

        val existingPost = cachedPostDao.getPostById(postId)
        if (existingPost != null) {
            val domainPost = existingPost.toCommunityPost().optimisticLikeToggle()
            localLiked = domainPost.viewerHasLiked
            localReactions = domainPost.reactions
            cachedPostDao.insertPost(domainPost.toCachedEntity())
        }

        val remoteOutcome = NetworkResilience.standardResult {
            val outcome = supabase.postgrest.rpc(
                function = "toggle_community_post_like",
                parameters = buildJsonObject {
                    put("p_post_id", postId)
                },
            ).decodeList<CommunityPostLikeOutcomeRow>().singleOrNull()
            outcome?.let {
                CommunityLikeOutcome(liked = it.liked, reactionCount = it.likeCount.coerceAtLeast(0))
            }
        }.getOrNull()

        remoteOutcome ?: CommunityLikeOutcome(liked = localLiked, reactionCount = localReactions)
    }

    override suspend fun toggleReaction(postId: String, emoji: String): Result<CommunityLikeOutcome> = NetworkResilience.standardResult {
        var localLiked = false
        var localReactions = 0

        val existingPost = cachedPostDao.getPostById(postId)
        if (existingPost != null) {
            val domainPost = existingPost.toCommunityPost().optimisticReactionToggle(emoji)
            localLiked = domainPost.viewerHasLiked
            localReactions = domainPost.reactions
            cachedPostDao.insertPost(domainPost.toCachedEntity())
        }

        val remoteOutcome = NetworkResilience.standardResult {
            val outcome = supabase.postgrest.rpc(
                function = "toggle_community_post_reaction",
                parameters = buildJsonObject {
                    put("p_post_id", postId)
                    put("p_emoji", emoji)
                },
            ).decodeList<CommunityPostLikeOutcomeRow>().singleOrNull()
            outcome?.let {
                CommunityLikeOutcome(liked = it.liked, reactionCount = it.likeCount.coerceAtLeast(0))
            }
        }.getOrNull()

        remoteOutcome ?: CommunityLikeOutcome(liked = localLiked, reactionCount = localReactions)
    }

    override suspend fun repostPost(postId: String): Result<Pair<Boolean, Int>> = NetworkResilience.standardResult {
        var localReposted = false
        var localCount = 0
        val existingPost = cachedPostDao.getPostById(postId)
        if (existingPost != null) {
            val domain = existingPost.toCommunityPost().optimisticRepostToggle()
            localReposted = domain.isRepostedByViewer
            localCount = domain.repostCount
            cachedPostDao.insertPost(domain.toCachedEntity())
        }

        val remoteOutcome = NetworkResilience.standardResult {
            val res = supabase.postgrest.rpc(
                function = "repost_community_post",
                parameters = buildJsonObject { put("p_post_id", postId) }
            ).decodeList<CommunityRepostOutcomeRow>().firstOrNull()
            res?.let { it.reposted to it.repostCount }
        }.getOrNull()

        remoteOutcome ?: (localReposted to localCount)
    }

    override suspend fun toggleBookmark(postId: String): Result<Pair<Boolean, Int>> = NetworkResilience.standardResult {
        var localBookmarked = false
        var localCount = 0
        val existingPost = cachedPostDao.getPostById(postId)
        if (existingPost != null) {
            val domain = existingPost.toCommunityPost().optimisticBookmarkToggle()
            localBookmarked = domain.isBookmarkedByViewer
            localCount = domain.bookmarkCount
            cachedPostDao.insertPost(domain.toCachedEntity())
        }

        val remoteOutcome = NetworkResilience.standardResult {
            val rpcName = if (localBookmarked) "bookmark_community_post" else "unbookmark_community_post"
            val res = supabase.postgrest.rpc(
                function = rpcName,
                parameters = buildJsonObject { put("p_post_id", postId) }
            ).decodeList<CommunityBookmarkOutcomeRow>().firstOrNull()
            res?.let { it.bookmarked to it.bookmarkCount }
        }.getOrNull()

        remoteOutcome ?: (localBookmarked to localCount)
    }

    override suspend fun searchPosts(
        query: String,
        lastRank: Float?,
        lastId: String?,
        limit: Int,
    ): Result<List<CommunityPost>> = NetworkResilience.standardResult {
        if (query.isBlank()) return@standardResult emptyList()
        val remotePosts = NetworkResilience.standardResult {
            val rows = supabase.postgrest.rpc(
                function = "search_community_posts_cursor",
                parameters = buildJsonObject {
                    put("p_query", query.trim())
                    lastRank?.let { put("p_last_rank", it) }
                    lastId?.let { put("p_last_id", it) }
                    put("p_limit", limit)
                }
            ).decodeList<CommunityFeedRow>()
            coroutineScope {
                rows.map { row -> async { row.toCommunityPost() } }.awaitAll()
            }
        }.recoverCatching {
            // Fallback to standard search if cursor RPC is unavailable
            val rows = supabase.postgrest.rpc(
                function = "search_community_posts",
                parameters = buildJsonObject {
                    put("p_query", query.trim())
                    put("p_limit", limit)
                }
            ).decodeList<CommunityFeedRow>()
            coroutineScope {
                rows.map { row -> async { row.toCommunityPost() } }.awaitAll()
            }
        }.getOrElse {
            // Local fallback search from Room database
            cachedPostDao.getAllPosts()
                .map { it.toCommunityPost() }
                .filter {
                    it.content.contains(query, ignoreCase = true) ||
                    it.author.contains(query, ignoreCase = true) ||
                    it.handle.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
                }
        }
        remotePosts
    }

    override fun observeNotificationEvents(userId: String): Flow<CommunityRealtimeNotification> = callbackFlow {
        val channel = supabase.realtime.channel("user-notifications-$userId")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "notification_events"
        }
        val job = CoroutineScope(Dispatchers.IO).launch {
            changeFlow.collect { action ->
                if (action is PostgresAction.Insert) {
                    runCatching {
                        val row = Json { ignoreUnknownKeys = true }.decodeFromString<RealtimeNotificationPayload>(action.record.toString())
                        if (row.recipientId == userId || userId.isBlank()) {
                            trySend(
                                CommunityRealtimeNotification(
                                    id = row.id,
                                    recipientId = row.recipientId,
                                    type = row.notificationType,
                                    title = row.title,
                                    body = row.body,
                                    createdAt = row.createdAt,
                                )
                            )
                        }
                    }
                }
            }
        }
        runCatching { NetworkResilience.standard { channel.subscribe() } }
        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { NetworkResilience.standard { channel.unsubscribe() } }
            }
        }
    }

    override fun observeCommunityFeedRealtime(): Flow<String> = callbackFlow {
        val channel = supabase.realtime.channel("public-feed-stream")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "community_posts"
        }
        val job = CoroutineScope(Dispatchers.IO).launch {
            changeFlow.collect { action ->
                if (action is PostgresAction.Insert) {
                    val id = action.record["id"]?.toString()?.trim('"') ?: ""
                    if (id.isNotBlank()) {
                        trySend(id)
                    }
                }
            }
        }
        runCatching { NetworkResilience.standard { channel.subscribe() } }
        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { NetworkResilience.standard { channel.unsubscribe() } }
            }
        }
    }

    override fun observeCachedPosts(): Flow<List<CommunityPost>> = cachedPostDao.observeAllPosts().map { list ->
        list.map { it.toCommunityPost() }
    }

    override suspend fun getHashtagAutocomplete(prefix: String): Result<List<String>> = NetworkResilience.standardResult {
        val cleanPrefix = prefix.trim().removePrefix("#")
        val res = NetworkResilience.standardResult {
            supabase.postgrest.rpc(
                function = "autocomplete_hashtags",
                parameters = buildJsonObject {
                    put("p_prefix", cleanPrefix)
                    put("p_limit", 10)
                }
            ).decodeList<HashtagRow>().map { "#${it.tag}" }
        }.getOrElse {
            emptyList()
        }
        res
    }

    override suspend fun getMentionAutocomplete(prefix: String): Result<List<String>> = NetworkResilience.standardResult {
        val cleanPrefix = prefix.trim().removePrefix("@")
        val res = NetworkResilience.standardResult {
            supabase.postgrest.rpc(
                function = "autocomplete_mentions",
                parameters = buildJsonObject {
                    put("p_prefix", cleanPrefix)
                    put("p_limit", 10)
                }
            ).decodeList<MentionRow>().map { "@${it.handle}" }
        }.getOrElse {
            emptyList()
        }
        res
    }

    override suspend fun refreshMediaUrl(mediaId: String): Result<String?> = NetworkResilience.standardResult {
        val path = mediaPaths[mediaId] ?: return@standardResult null
        signedUrlCache.invalidate(mediaCacheKey(mediaId))
        issueMediaUrl(mediaId, path)
    }

    private suspend fun CommunityFeedRow.toCommunityPost(): CommunityPost {
        val mappedMedia = coroutineScope {
            media.sortedBy(CommunityEmbeddedMediaRow::position).map { row ->
                async { row.toMediaItem(postId = id) }
            }.awaitAll()
        }
        return CommunityPost(
            id = id,
            author = authorName,
            handle = "@${authorHandle.removePrefix("@")}",
            content = body,
            category = categoryLabel ?: if (staffBadge) "Official Community" else "Community",
            createdAt = createdAt,
            reactions = reactionCount,
            comments = commentCount,
            viewerHasLiked = viewerHasLiked,
            trendingScore = trendingScore.coerceAtLeast(0),
            isFollowedTopic = isFollowedTopic,
            hasMedia = mappedMedia.isNotEmpty(),
            media = mappedMedia,
            isOfficial = staffBadge,
            authorId = authorId,
            authorAvatarUrl = avatarPath?.let { signedAvatarUrl(it, avatarUpdatedAt) },
            isLocked = isLocked,
            editedAt = editedAt,
            repostOfId = repostOfId,
            quotePostId = quotePostId,
            repostCount = repostCount,
            bookmarkCount = bookmarkCount,
            isRepostedByViewer = isRepostedByViewer,
            isBookmarkedByViewer = isBookmarkedByViewer,
        )
    }

    private suspend fun CommunityCommentRow.toCommunityComment(): CommunityComment = CommunityComment(
        id = id,
        postId = postId,
        authorId = authorId,
        author = authorName,
        handle = "@${authorHandle.removePrefix("@")}",
        authorAvatarUrl = avatarPath?.let { signedAvatarUrl(it, avatarUpdatedAt) },
        content = body,
        createdAt = createdAt,
        editedAt = editedAt,
        isStaff = staffBadge,
        parentId = parentCommentId ?: parentId,
        replyCount = replyCount,
        depth = depth,
    )

    private suspend fun CommunityEmbeddedMediaRow.toMediaItem(postId: String): MediaItem {
        mediaPaths[id] = storagePath
        return MediaItem(
            id = id,
            targetType = MediaTargetType.COMMUNITY_POST,
            targetId = postId,
            storagePath = storagePath,
            kind = NetworkResilience.standardResult { MediaKind.valueOf(mediaKind) }.getOrDefault(MediaKind.IMAGE),
            mimeType = mimeType,
            byteSize = byteSize,
            width = width,
            height = height,
            durationSeconds = durationSeconds,
            position = position,
            caption = caption,
            signedUrl = issueMediaUrl(id, storagePath),
        )
    }

    private suspend fun issueMediaUrl(mediaId: String, storagePath: String): String? =
        signedUrlCache.getOrIssue(mediaCacheKey(mediaId)) {
            val url = supabase.storage.from(COMMUNITY_MEDIA_BUCKET).createSignedUrl(storagePath, SIGNED_URL_TTL)
            SignedUrlValue(
                url = url,
                expiresAt = clock.instant().plusSeconds(SIGNED_URL_TTL.inWholeSeconds),
            )
        }

    private suspend fun signedAvatarUrl(path: String, revision: String?): String {
        val stableRevision = revision?.hashCode()?.toUInt()?.toString(16)
            ?: path.hashCode().toUInt().toString(16)
        val key = "avatar:$path:$stableRevision"
        return requireNotNull(
            signedUrlCache.getOrIssue(key) {
                val signed = supabase.storage.from(PROFILE_MEDIA_BUCKET).createSignedUrl(path, SIGNED_URL_TTL)
                SignedUrlValue(
                    url = "$signed${if (signed.contains("?")) "&" else "?"}v=$stableRevision",
                    expiresAt = clock.instant().plusSeconds(SIGNED_URL_TTL.inWholeSeconds),
                )
            }
        )
    }

    private fun mediaCacheKey(mediaId: String): String = "media:$mediaId"

    private companion object {
        const val COMMUNITY_MEDIA_BUCKET = "rtc-community-media"
        const val PROFILE_MEDIA_BUCKET = "rtc-profile-media"
        const val MAX_VISIBLE_PAGE_SIZE = 49
        const val MAX_SERVER_PAGE_SIZE = 50
        const val SIGNED_URL_CACHE_CAPACITY = 192
        const val SIGNED_URL_REFRESH_SKEW_SECONDS = 90L
        val SIGNED_URL_TTL = 60.minutes
    }
}

@Serializable
private data class CommunityPostLikeOutcomeRow(
    val liked: Boolean,
    @SerialName("like_count") val likeCount: Int,
)

@Serializable
private data class CommunityRepostOutcomeRow(
    val reposted: Boolean,
    @SerialName("repost_count") val repostCount: Int = 0,
)

@Serializable
private data class CommunityBookmarkOutcomeRow(
    val bookmarked: Boolean,
    @SerialName("bookmark_count") val bookmarkCount: Int = 0,
)

@Serializable
private data class HashtagRow(
    val tag: String,
    @SerialName("post_count") val postCount: Long = 0,
)

@Serializable
private data class MentionRow(
    val id: String,
    val handle: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
)

@Serializable
private data class CommunityFeedRow(
    val id: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_handle") val authorHandle: String,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("avatar_updated_at") val avatarUpdatedAt: String? = null,
    @SerialName("staff_badge") val staffBadge: Boolean = false,
    val body: String,
    @SerialName("category_label") val categoryLabel: String? = null,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("reaction_count") val reactionCount: Int = 0,
    @SerialName("viewer_has_liked") val viewerHasLiked: Boolean = false,
    @SerialName("trending_score") val trendingScore: Int = 0,
    @SerialName("is_followed_topic") val isFollowedTopic: Boolean = false,
    @SerialName("repost_of_id") val repostOfId: String? = null,
    @SerialName("quote_post_id") val quotePostId: String? = null,
    @SerialName("repost_count") val repostCount: Int = 0,
    @SerialName("bookmark_count") val bookmarkCount: Int = 0,
    @SerialName("is_reposted_by_viewer") val isRepostedByViewer: Boolean = false,
    @SerialName("is_bookmarked_by_viewer") val isBookmarkedByViewer: Boolean = false,
    val media: List<CommunityEmbeddedMediaRow> = emptyList(),
)

@Serializable
private data class CommunityCommentRow(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_handle") val authorHandle: String,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("avatar_updated_at") val avatarUpdatedAt: String? = null,
    @SerialName("staff_badge") val staffBadge: Boolean = false,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("parent_comment_id") val parentCommentId: String? = null,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("depth") val depth: Int = 0,
)

@Serializable
private data class CommunityCommentChangePayload(
    val body: String? = null,
    val state: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
private data class CommunityEmbeddedMediaRow(
    val id: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("media_kind") val mediaKind: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("byte_size") val byteSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    val position: Int,
    val caption: String? = null,
)

@Serializable
private data class RealtimeNotificationPayload(
    val id: String = "",
    @SerialName("recipient_id") val recipientId: String = "",
    @SerialName("notification_type") val notificationType: String = "",
    val title: String = "",
    val body: String = "",
    @SerialName("created_at") val createdAt: String = "",
)
