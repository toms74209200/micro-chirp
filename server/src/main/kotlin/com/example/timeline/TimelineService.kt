package com.example.timeline

import com.example.like.LikeEventRepository
import com.example.like.UserLikeStatus
import com.example.like.aggregateLikeEvents
import com.example.post.PostEventRepository
import com.example.post.PostEventType
import com.example.post.countActiveReplies
import com.example.repost.RepostEventRepository
import com.example.repost.UserRepostStatus
import com.example.repost.aggregateRepostEvents
import com.example.view.ViewEventRepository
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

sealed interface TimelineResult {
    data class PostItem(
        val postId: UUID,
        val userId: UUID,
        val content: String,
        val createdAt: Instant,
        val likeCount: Int,
        val repostCount: Int,
        val replyCount: Int,
        val viewCount: Int,
        val isLikedByCurrentUser: Boolean?,
        val isRepostedByCurrentUser: Boolean?,
    )

    data class Success(
        val posts: List<PostItem>,
        val limit: Int,
    ) : TimelineResult

    data class Failure(
        val exception: Exception,
    ) : TimelineResult
}

@Service
class TimelineService(
    private val timelineJdbcRepository: TimelineJdbcRepository,
    private val postEventRepository: PostEventRepository,
    private val likeEventRepository: LikeEventRepository,
    private val repostEventRepository: RepostEventRepository,
    private val viewEventRepository: ViewEventRepository,
    private val mvRefreshLogRepository: MvRefreshLogRepository,
    private val objectMapper: ObjectMapper,
) {
    @WithSpan
    fun getGlobalTimeline(
        limit: Int,
        afterPostId: UUID?,
        currentUserId: UUID?,
    ): TimelineResult {
        val cursor: Pair<Instant, UUID>? =
            if (afterPostId != null) {
                try {
                    val event =
                        postEventRepository.findFirstByPostIdAndEventType(afterPostId, PostEventType.POST_CREATED.value)
                            ?: return TimelineResult.Failure(IllegalArgumentException("Post not found: $afterPostId"))
                    event.occurredAt to afterPostId
                } catch (e: DataAccessException) {
                    return TimelineResult.Failure(e)
                }
            } else {
                null
            }

        val lastRefreshedAt =
            mvRefreshLogRepository
                .findById(POSTS_MV_NAME)
                .map { it.lastRefreshedAt }
                .orElse(Instant.EPOCH)

        val deltaPostEvents =
            try {
                postEventRepository.findByOccurredAtAfterOrderByOccurredAtAsc(lastRefreshedAt)
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }

        val delta = buildTimelineDelta(deltaPostEvents.groupBy { it.postId }, { _ -> true }, objectMapper)

        val deltaOnPage =
            delta.activePosts
                .filter { cursor == null || it.createdAt < cursor.first || (it.createdAt == cursor.first && it.postId < cursor.second) }
                .take(limit)
        val remainingForMv = limit - deltaOnPage.size

        val mvRawPosts =
            try {
                if (remainingForMv > 0) {
                    val mvBuffer = remainingForMv + delta.deletedFromMvCount
                    if (cursor == null) {
                        timelineJdbcRepository.findGlobalTimeline(mvBuffer.coerceAtLeast(remainingForMv))
                    } else {
                        timelineJdbcRepository.findGlobalTimelineAfter(mvBuffer.coerceAtLeast(remainingForMv), cursor.first, cursor.second)
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                return TimelineResult.Failure(Exception("Failed to query timeline MV: ${e.message}", e))
            }

        val mvPosts = mvRawPosts.filter { it.postId !in delta.deletedIds }.take(remainingForMv)
        val pagePosts = deltaOnPage + mvPosts

        if (pagePosts.isEmpty()) {
            return TimelineResult.Success(emptyList(), limit)
        }

        val postIds = pagePosts.map { it.postId }

        val allLikeEvents =
            try {
                likeEventRepository.findByPostIdInOrderByOccurredAtAsc(postIds)
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }
        val likesByPostId = allLikeEvents.groupBy { it.postId }

        val allRepostEvents =
            try {
                repostEventRepository.findByPostIdInOrderByOccurredAtAsc(postIds)
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }
        val repostsByPostId = allRepostEvents.groupBy { it.postId }

        val viewCountByPostId =
            try {
                viewEventRepository
                    .countsByPostIdIn(postIds)
                    .associate { it.getPostId() to it.getCount().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }

        val replyCreatedEvents =
            try {
                postEventRepository.findByReplyToPostIdInOrderByOccurredAtAsc(postIds)
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }
        val replyPostIdsByParent = replyCreatedEvents.groupBy({ it.replyToPostId!! }, { it.postId })
        val allReplyPostIds = replyCreatedEvents.map { it.postId }.distinct()
        val allReplyEventsByPostId =
            if (allReplyPostIds.isEmpty()) {
                emptyMap()
            } else {
                try {
                    postEventRepository.findByPostIdInOrderByOccurredAtAsc(allReplyPostIds).groupBy { it.postId }
                } catch (e: DataAccessException) {
                    return TimelineResult.Failure(e)
                }
            }

        val enrichedPosts =
            pagePosts.map { post ->
                val postLikeEvents = likesByPostId[post.postId] ?: emptyList()
                val aggregatedLikes = aggregateLikeEvents(postLikeEvents)
                val postRepostEvents = repostsByPostId[post.postId] ?: emptyList()
                val aggregatedReposts = aggregateRepostEvents(postRepostEvents)
                val replyPostIds = replyPostIdsByParent[post.postId] ?: emptyList()
                val replyEventsByPostId = replyPostIds.associateWith { allReplyEventsByPostId[it] ?: emptyList() }
                val replyCount = countActiveReplies(replyEventsByPostId, objectMapper)
                val viewCount = viewCountByPostId[post.postId] ?: 0
                TimelineResult.PostItem(
                    postId = post.postId,
                    userId = post.userId,
                    content = post.content,
                    createdAt = post.createdAt,
                    likeCount = aggregatedLikes.likeCount,
                    repostCount = aggregatedReposts.repostCount,
                    replyCount = replyCount,
                    viewCount = viewCount,
                    isLikedByCurrentUser =
                        currentUserId?.let { uid ->
                            when (UserLikeStatus.fromEvents(postLikeEvents, uid)) {
                                UserLikeStatus.Liked -> true
                                UserLikeStatus.NotLiked -> false
                            }
                        },
                    isRepostedByCurrentUser =
                        currentUserId?.let { uid ->
                            when (UserRepostStatus.fromEvents(postRepostEvents, uid)) {
                                UserRepostStatus.Reposted -> true
                                UserRepostStatus.NotReposted -> false
                            }
                        },
                )
            }

        if (currentUserId != null) {
            try {
                viewEventRepository.saveAll(
                    postIds.map { postId ->
                        com.example.view.ViewEvent(
                            eventId = UUID.randomUUID(),
                            postId = postId,
                            userId = currentUserId,
                            occurredAt = Instant.now(),
                        )
                    },
                )
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }
        }

        return TimelineResult.Success(enrichedPosts, limit)
    }

    @WithSpan
    fun getUserTimeline(
        targetUserId: UUID,
        limit: Int,
        afterPostId: UUID?,
        currentUserId: UUID?,
    ): TimelineResult {
        val cursor: Pair<Instant, UUID>? =
            if (afterPostId != null) {
                try {
                    val event =
                        postEventRepository.findFirstByPostIdAndEventType(afterPostId, PostEventType.POST_CREATED.value)
                            ?: return TimelineResult.Failure(IllegalArgumentException("Post not found: $afterPostId"))
                    val data =
                        objectMapper.readValue(event.eventData, Map::class.java) as? Map<*, *>
                            ?: return TimelineResult.Failure(IllegalArgumentException("Post not found: $afterPostId"))
                    val userId =
                        UUID.fromString(
                            data["userId"] as? String
                                ?: return TimelineResult.Failure(IllegalArgumentException("Post not found: $afterPostId")),
                        )
                    if (userId != targetUserId) return TimelineResult.Failure(IllegalArgumentException("Post not found: $afterPostId"))
                    event.occurredAt to afterPostId
                } catch (e: DataAccessException) {
                    return TimelineResult.Failure(e)
                }
            } else {
                null
            }

        val lastRefreshedAt =
            mvRefreshLogRepository
                .findById(POSTS_MV_NAME)
                .map { it.lastRefreshedAt }
                .orElse(Instant.EPOCH)

        val deltaPostEvents =
            try {
                postEventRepository.findByOccurredAtAfterOrderByOccurredAtAsc(lastRefreshedAt)
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }

        val delta =
            buildTimelineDelta(
                deltaPostEvents.groupBy { it.postId },
                { userId -> userId == targetUserId },
                objectMapper,
            )

        val deltaOnPage =
            delta.activePosts
                .filter { cursor == null || it.createdAt < cursor.first || (it.createdAt == cursor.first && it.postId < cursor.second) }
                .take(limit)
        val remainingForMv = limit - deltaOnPage.size

        val mvRawPosts =
            try {
                if (remainingForMv > 0) {
                    val mvBuffer = remainingForMv + delta.deletedFromMvCount
                    if (cursor == null) {
                        timelineJdbcRepository.findUserTimeline(targetUserId, mvBuffer.coerceAtLeast(remainingForMv))
                    } else {
                        timelineJdbcRepository.findUserTimelineAfter(
                            targetUserId,
                            mvBuffer.coerceAtLeast(remainingForMv),
                            cursor.first,
                            cursor.second,
                        )
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                return TimelineResult.Failure(Exception("Failed to query timeline MV: ${e.message}", e))
            }

        val mvPosts = mvRawPosts.filter { it.postId !in delta.deletedIds }.take(remainingForMv)
        val pagePosts = deltaOnPage + mvPosts

        if (pagePosts.isEmpty()) {
            return TimelineResult.Success(emptyList(), limit)
        }

        val postIds = pagePosts.map { it.postId }

        val allLikeEvents =
            try {
                likeEventRepository.findByPostIdInOrderByOccurredAtAsc(postIds)
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }
        val likesByPostId = allLikeEvents.groupBy { it.postId }

        val allRepostEvents =
            try {
                repostEventRepository.findByPostIdInOrderByOccurredAtAsc(postIds)
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }
        val repostsByPostId = allRepostEvents.groupBy { it.postId }

        val viewCountByPostId =
            try {
                viewEventRepository
                    .countsByPostIdIn(postIds)
                    .associate { it.getPostId() to it.getCount().coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }

        val replyCreatedEvents =
            try {
                postEventRepository.findByReplyToPostIdInOrderByOccurredAtAsc(postIds)
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }
        val replyPostIdsByParent = replyCreatedEvents.groupBy({ it.replyToPostId!! }, { it.postId })
        val allReplyPostIds = replyCreatedEvents.map { it.postId }.distinct()
        val allReplyEventsByPostId =
            if (allReplyPostIds.isEmpty()) {
                emptyMap()
            } else {
                try {
                    postEventRepository.findByPostIdInOrderByOccurredAtAsc(allReplyPostIds).groupBy { it.postId }
                } catch (e: DataAccessException) {
                    return TimelineResult.Failure(e)
                }
            }

        val enrichedPosts =
            pagePosts.map { post ->
                val postLikeEvents = likesByPostId[post.postId] ?: emptyList()
                val aggregatedLikes = aggregateLikeEvents(postLikeEvents)
                val postRepostEvents = repostsByPostId[post.postId] ?: emptyList()
                val aggregatedReposts = aggregateRepostEvents(postRepostEvents)
                val replyPostIds = replyPostIdsByParent[post.postId] ?: emptyList()
                val replyEventsByPostId = replyPostIds.associateWith { allReplyEventsByPostId[it] ?: emptyList() }
                val replyCount = countActiveReplies(replyEventsByPostId, objectMapper)
                val viewCount = viewCountByPostId[post.postId] ?: 0
                TimelineResult.PostItem(
                    postId = post.postId,
                    userId = post.userId,
                    content = post.content,
                    createdAt = post.createdAt,
                    likeCount = aggregatedLikes.likeCount,
                    repostCount = aggregatedReposts.repostCount,
                    replyCount = replyCount,
                    viewCount = viewCount,
                    isLikedByCurrentUser =
                        currentUserId?.let { uid ->
                            when (UserLikeStatus.fromEvents(postLikeEvents, uid)) {
                                UserLikeStatus.Liked -> true
                                UserLikeStatus.NotLiked -> false
                            }
                        },
                    isRepostedByCurrentUser =
                        currentUserId?.let { uid ->
                            when (UserRepostStatus.fromEvents(postRepostEvents, uid)) {
                                UserRepostStatus.Reposted -> true
                                UserRepostStatus.NotReposted -> false
                            }
                        },
                )
            }

        if (currentUserId != null) {
            try {
                viewEventRepository.saveAll(
                    postIds.map { postId ->
                        com.example.view.ViewEvent(
                            eventId = UUID.randomUUID(),
                            postId = postId,
                            userId = currentUserId,
                            occurredAt = Instant.now(),
                        )
                    },
                )
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }
        }

        return TimelineResult.Success(enrichedPosts, limit)
    }

    companion object {
        const val POSTS_MV_NAME = "posts_mv"
    }
}
