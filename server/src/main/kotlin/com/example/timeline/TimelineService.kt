package com.example.timeline

import com.example.like.LikeEventRepository
import com.example.like.UserLikeStatus
import com.example.like.aggregateLikeEvents
import com.example.post.PostEventRepository
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
        val total: Int,
        val limit: Int,
        val offset: Int,
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
        offset: Int,
        currentUserId: UUID?,
    ): TimelineResult {
        val lastRefreshedAt =
            mvRefreshLogRepository
                .findById(GLOBAL_TIMELINE_MV_NAME)
                .map { it.lastRefreshedAt }
                .orElse(Instant.EPOCH)

        val deltaPostEvents =
            try {
                postEventRepository.findByOccurredAtAfterOrderByOccurredAtAsc(lastRefreshedAt)
            } catch (e: DataAccessException) {
                return TimelineResult.Failure(e)
            }

        val delta = buildTimelineDelta(deltaPostEvents.groupBy { it.postId }, { _ -> true }, objectMapper)

        val deltaOnPage = delta.activePosts.drop(offset).take(limit)
        val remainingForMv = limit - deltaOnPage.size
        val mvOffset = (offset - delta.activePosts.size).coerceAtLeast(0)

        val mvRawPosts =
            try {
                if (remainingForMv > 0) {
                    val mvBuffer = remainingForMv + delta.deletedIds.size
                    timelineJdbcRepository.findGlobalTimeline(mvBuffer.coerceAtLeast(remainingForMv), mvOffset)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                return TimelineResult.Failure(Exception("Failed to query timeline MV: ${e.message}", e))
            }

        val mvPosts = mvRawPosts.filter { it.postId !in delta.deletedIds }.take(remainingForMv)
        val pagePosts = deltaOnPage + mvPosts

        val mvTotal =
            try {
                timelineJdbcRepository.countGlobalTimeline()
            } catch (e: Exception) {
                return TimelineResult.Failure(Exception("Failed to count timeline MV: ${e.message}", e))
            }
        val total = (mvTotal + delta.activePosts.size - delta.deletedFromMvCount.toLong()).coerceAtLeast(0L).toInt()

        if (pagePosts.isEmpty()) {
            return TimelineResult.Success(emptyList(), total, limit, offset)
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

        return TimelineResult.Success(enrichedPosts, total, limit, offset)
    }

    companion object {
        const val GLOBAL_TIMELINE_MV_NAME = "global_timeline_mv"
    }
}
