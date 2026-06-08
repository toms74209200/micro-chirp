package com.example.timeline

import com.example.TestcontainersConfiguration
import com.example.auth.User
import com.example.auth.UserRepository
import com.example.like.LikeEvent
import com.example.like.LikeEventRepository
import com.example.like.LikeEventType
import com.example.post.PostCreationResult
import com.example.post.PostService
import com.example.test.tracing.SpanTimingExtension
import com.example.test.tracing.TestPhases
import com.example.view.ViewEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ExtendWith(SpanTimingExtension::class)
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
    private lateinit var viewEventRepository: ViewEventRepository

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
    fun `when getGlobalTimeline with multiple posts then returns posts ordered by createdAt desc`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        val post1 = postService.createPost(userId, "Post 1") as PostCreationResult.Success
        val post2 = postService.createPost(userId, "Post 2") as PostCreationResult.Success

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        phases.act()
        val result = timelineService.getGlobalTimeline(20, null, null) as TimelineResult.Success

        phases.assert()
        val returnedIds = result.posts.map { it.postId }
        val post1Index = returnedIds.indexOf(post1.postId)
        val post2Index = returnedIds.indexOf(post2.postId)

        assertThat(post1Index).isGreaterThanOrEqualTo(0)
        assertThat(post2Index).isGreaterThanOrEqualTo(0)
        assertThat(post2Index).isLessThan(post1Index)
    }

    @Test
    fun `when getGlobalTimeline with deleted post then excludes it from results`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        val post = postService.createPost(userId, "To be deleted") as PostCreationResult.Success
        postService.deletePost(post.postId, userId)

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        phases.act()
        val result = timelineService.getGlobalTimeline(100, null, null) as TimelineResult.Success

        phases.assert()
        assertThat(result.posts.map { it.postId }).doesNotContain(post.postId)
    }

    @Test
    fun `when getGlobalTimeline with limit and cursor then returns paginated results`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        repeat(5) { i -> postService.createPost(userId, "Pagination post $i") }

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        phases.act()
        val page1 = timelineService.getGlobalTimeline(2, null, null) as TimelineResult.Success
        val cursor = page1.posts.last().postId
        val page2 = timelineService.getGlobalTimeline(2, cursor, null) as TimelineResult.Success

        phases.assert()
        assertThat(page1.posts).hasSize(2)
        assertThat(page2.posts).hasSize(2)
        assertThat(page1.posts.map { it.postId }).doesNotContainAnyElementsOf(page2.posts.map { it.postId })
    }

    @Test
    fun `when getGlobalTimeline with liked post then returns its like count`(phases: TestPhases) {
        phases.arrange()
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

        phases.act()
        val result = timelineService.getGlobalTimeline(100, null, null) as TimelineResult.Success

        phases.assert()
        val postItem = result.posts.find { it.postId == post.postId }
        assertThat(postItem).isNotNull()
        assertThat(postItem!!.likeCount).isEqualTo(1)
    }

    @Test
    fun `when getGlobalTimeline with post created after last MV refresh then returns it as a delta post`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        val deltaPost = postService.createPost(userId, "Delta post") as PostCreationResult.Success

        phases.act()
        val result = timelineService.getGlobalTimeline(100, null, null) as TimelineResult.Success

        phases.assert()
        assertThat(result.posts.map { it.postId }).contains(deltaPost.postId)
    }

    @Test
    fun `when getGlobalTimeline with post deleted after last MV refresh then excludes it from results`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        val post = postService.createPost(userId, "Delta deleted post") as PostCreationResult.Success
        postService.deletePost(post.postId, userId)

        phases.act()
        val result = timelineService.getGlobalTimeline(100, null, null) as TimelineResult.Success

        phases.assert()
        assertThat(result.posts.map { it.postId }).doesNotContain(post.postId)
    }

    @Test
    fun `when getUserTimeline with target user then returns only posts by that user`(phases: TestPhases) {
        phases.arrange()
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        userRepository.save(User(user1, Instant.now()))
        userRepository.save(User(user2, Instant.now()))

        val post1 = postService.createPost(user1, "User1 post") as PostCreationResult.Success
        val post2 = postService.createPost(user2, "User2 post") as PostCreationResult.Success

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        phases.act()
        val result = timelineService.getUserTimeline(user1, 20, null, null) as TimelineResult.Success

        phases.assert()
        assertThat(result.posts.map { it.postId }).contains(post1.postId)
        assertThat(result.posts.map { it.postId }).doesNotContain(post2.postId)
    }

    @Test
    fun `when getUserTimeline with delta posts by target user then returns those delta posts`(phases: TestPhases) {
        phases.arrange()
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        userRepository.save(User(user1, Instant.now()))
        userRepository.save(User(user2, Instant.now()))

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        val deltaPost1 = postService.createPost(user1, "Delta post by user1") as PostCreationResult.Success
        postService.createPost(user2, "Delta post by user2") as PostCreationResult.Success

        phases.act()
        val result = timelineService.getUserTimeline(user1, 20, null, null) as TimelineResult.Success

        phases.assert()
        assertThat(result.posts.map { it.postId }).contains(deltaPost1.postId)
        assertThat(result.posts.map { it.userId }.toSet()).containsOnly(user1)
    }

    @Test
    fun `when getUserTimeline with deleted post by another user then returns only posts by target user`(phases: TestPhases) {
        phases.arrange()
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        userRepository.save(User(user1, Instant.now()))
        userRepository.save(User(user2, Instant.now()))

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        postService.createPost(user1, "User1 post 1")
        postService.createPost(user1, "User1 post 2")
        postService.createPost(user1, "User1 post 3")

        val user2Post = postService.createPost(user2, "User2 post to delete") as PostCreationResult.Success
        postService.deletePost(user2Post.postId, user2)

        phases.act()
        val result = timelineService.getUserTimeline(user1, 10, null, null) as TimelineResult.Success

        phases.assert()
        assertThat(result.posts).hasSize(3)
        assertThat(result.posts.map { it.userId }.toSet()).containsOnly(user1)
    }

    @Test
    fun `when getUserTimeline with post deleted after last MV refresh then excludes it from results`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        val postInMv = postService.createPost(userId, "Post that will be deleted") as PostCreationResult.Success
        val postKept = postService.createPost(userId, "Post that stays") as PostCreationResult.Success

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        postService.deletePost(postInMv.postId, userId)

        phases.act()
        val result = timelineService.getUserTimeline(userId, 10, null, null) as TimelineResult.Success

        phases.assert()
        assertThat(result.posts.map { it.postId }).containsOnly(postKept.postId)
    }

    @Test
    fun `when getUserTimeline with limit and cursor then returns paginated results`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        repeat(5) { i -> postService.createPost(userId, "User pagination post $i") }

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        phases.act()
        val page1 = timelineService.getUserTimeline(userId, 2, null, null) as TimelineResult.Success
        val cursor = page1.posts.last().postId
        val page2 = timelineService.getUserTimeline(userId, 2, cursor, null) as TimelineResult.Success

        phases.assert()
        assertThat(page1.posts).hasSize(2)
        assertThat(page2.posts).hasSize(2)
        assertThat(page1.posts.map { it.postId }).doesNotContainAnyElementsOf(page2.posts.map { it.postId })
    }

    @Test
    fun `when getUserTimeline with cursor post belonging to another user then returns Failure with IllegalArgumentException`(phases: TestPhases) {
        phases.arrange()
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()
        userRepository.save(User(user1, Instant.now()))
        userRepository.save(User(user2, Instant.now()))

        val user2Post = postService.createPost(user2, "User2 post") as PostCreationResult.Success

        phases.act()
        val result = timelineService.getUserTimeline(user1, 10, user2Post.postId, null)

        phases.assert()
        assertThat(result).isInstanceOf(TimelineResult.Failure::class.java)
        assertThat((result as TimelineResult.Failure).exception).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `when getGlobalTimeline with currentUserId then returns isLikedByCurrentUser`(phases: TestPhases) {
        phases.arrange()
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

        phases.act()
        val result = timelineService.getGlobalTimeline(100, null, userId) as TimelineResult.Success

        phases.assert()
        val postItem = result.posts.find { it.postId == post.postId }
        assertThat(postItem!!.isLikedByCurrentUser).isTrue()
    }

    @Test
    fun `when getGlobalTimeline with currentUserId then records view events for returned posts`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        val viewerId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        userRepository.save(User(viewerId, Instant.now()))

        val post = postService.createPost(userId, "Post to view") as PostCreationResult.Success

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        phases.act()
        timelineService.getGlobalTimeline(20, null, viewerId)

        phases.assert()
        val viewCount = viewEventRepository.countByPostId(post.postId)
        assertThat(viewCount).isGreaterThan(0)
    }

    @Test
    fun `when getGlobalTimeline with null currentUserId then does not record view events`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        val post = postService.createPost(userId, "Post no viewer") as PostCreationResult.Success

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }
        val before = viewEventRepository.countByPostId(post.postId)

        phases.act()
        timelineService.getGlobalTimeline(20, null, null)

        phases.assert()
        val after = viewEventRepository.countByPostId(post.postId)
        assertThat(after).isEqualTo(before)
    }

    @Test
    fun `when getUserTimeline with currentUserId then records view events for returned posts`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        val viewerId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        userRepository.save(User(viewerId, Instant.now()))

        val post = postService.createPost(userId, "User post to view") as PostCreationResult.Success

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }

        phases.act()
        timelineService.getUserTimeline(userId, 20, null, viewerId)

        phases.assert()
        val viewCount = viewEventRepository.countByPostId(post.postId)
        assertThat(viewCount).isGreaterThan(0)
    }

    @Test
    fun `when getUserTimeline with null currentUserId then does not record view events`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))

        val post = postService.createPost(userId, "User post no viewer") as PostCreationResult.Success

        jdbcTemplate.execute("REFRESH MATERIALIZED VIEW posts_mv")
        mvRefreshLogRepository.findById(TimelineService.POSTS_MV_NAME).ifPresent { log ->
            log.lastRefreshedAt = Instant.now()
            mvRefreshLogRepository.save(log)
        }
        val before = viewEventRepository.countByPostId(post.postId)

        phases.act()
        timelineService.getUserTimeline(userId, 20, null, null)

        phases.assert()
        val after = viewEventRepository.countByPostId(post.postId)
        assertThat(after).isEqualTo(before)
    }
}
