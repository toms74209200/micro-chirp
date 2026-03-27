package com.example.timeline

import com.example.TestcontainersConfiguration
import com.example.auth.User
import com.example.auth.UserRepository
import com.example.like.LikeEvent
import com.example.like.LikeEventRepository
import com.example.like.LikeEventType
import com.example.post.PostCreationResult
import com.example.post.PostService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class TimelineServiceTest {
    @Autowired
    private lateinit var timelineService: TimelineService

    @Autowired
    private lateinit var postService: PostService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var likeEventRepository: LikeEventRepository

    @Autowired
    private lateinit var mvRefreshLogRepository: MvRefreshLogRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun refreshMv() {
        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }
    }

    @Test
    fun `getGlobalTimeline returns posts ordered by createdAt desc`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        val post1 = postService.createPost(userId, "Post 1") as PostCreationResult.Success
        val post2 = postService.createPost(userId, "Post 2") as PostCreationResult.Success

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        val result = timelineService.getGlobalTimeline(20, null, null) as TimelineResult.Success

        val returnedIds = result.posts.map { it.postId }
        val post1Index = returnedIds.indexOf(post1.postId)
        val post2Index = returnedIds.indexOf(post2.postId)

        assertThat(post1Index).isGreaterThanOrEqualTo(0)
        assertThat(post2Index).isGreaterThanOrEqualTo(0)
        assertThat(post2Index).isLessThan(post1Index)
    }

    @Test
    fun `getGlobalTimeline excludes deleted posts`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        val post = postService.createPost(userId, "To be deleted") as PostCreationResult.Success
        postService.deletePost(post.postId, userId)

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        val result = timelineService.getGlobalTimeline(100, null, null) as TimelineResult.Success

        assertThat(result.posts.map { it.postId }).doesNotContain(post.postId)
    }

    @Test
    fun `getGlobalTimeline respects limit and cursor`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        repeat(5) { i -> postService.createPost(userId, "Pagination post $i") }

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        val page1 = timelineService.getGlobalTimeline(2, null, null) as TimelineResult.Success
        val cursor = page1.posts.last().postId
        val page2 = timelineService.getGlobalTimeline(2, cursor, null) as TimelineResult.Success

        assertThat(page1.posts).hasSize(2)
        assertThat(page2.posts).hasSize(2)
        assertThat(page1.posts.map { it.postId }).doesNotContainAnyElementsOf(page2.posts.map { it.postId })
    }

    @Test
    fun `getGlobalTimeline includes like count`() {
        val userId = UUID.randomUUID()
        val likerId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        userRepository.save(User(likerId, Instant.now()))

        val post = postService.createPost(userId, "Post with like") as PostCreationResult.Success

        likeEventRepository.save(
            LikeEvent(
                eventId = UUID.randomUUID(),
                postId = post.postId,
                userId = likerId,
                eventType = LikeEventType.LIKED.value,
                occurredAt = Instant.now(),
            ),
        )

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        val result = timelineService.getGlobalTimeline(100, null, null) as TimelineResult.Success
        val postItem = result.posts.find { it.postId == post.postId }

        assertThat(postItem).isNotNull()
        assertThat(postItem!!.likeCount).isEqualTo(1)
    }

    @Test
    fun `getGlobalTimeline shows delta posts created after last MV refresh`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        val deltaPost = postService.createPost(userId, "Delta post") as PostCreationResult.Success

        val result = timelineService.getGlobalTimeline(100, null, null) as TimelineResult.Success

        assertThat(result.posts.map { it.postId }).contains(deltaPost.postId)
    }

    @Test
    fun `getGlobalTimeline excludes delta-deleted posts`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        val post = postService.createPost(userId, "Delta deleted post") as PostCreationResult.Success
        postService.deletePost(post.postId, userId)

        val result = timelineService.getGlobalTimeline(100, null, null) as TimelineResult.Success

        assertThat(result.posts.map { it.postId }).doesNotContain(post.postId)
    }

    @Test
    fun `getGlobalTimeline returns isLikedByCurrentUser when userId provided`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        val post = postService.createPost(userId, "Post") as PostCreationResult.Success
        likeEventRepository.save(
            LikeEvent(
                eventId = UUID.randomUUID(),
                postId = post.postId,
                userId = userId,
                eventType = LikeEventType.LIKED.value,
                occurredAt = Instant.now(),
            ),
        )

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        val result = timelineService.getGlobalTimeline(100, null, userId) as TimelineResult.Success
        val postItem = result.posts.find { it.postId == post.postId }

        assertThat(postItem!!.isLikedByCurrentUser).isTrue()
    }
}
