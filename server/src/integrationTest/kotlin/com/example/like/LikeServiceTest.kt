package com.example.like

import com.example.TestcontainersConfiguration
import com.example.auth.User
import com.example.auth.UserRepository
import com.example.post.PostCreationResult
import com.example.post.PostService
import com.example.test.tracing.SpanTimingExtension
import com.example.test.tracing.TestPhases
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@ExtendWith(SpanTimingExtension::class)
class LikeServiceTest {
    @Autowired
    private lateinit var likeService: LikeService

    @Autowired
    private lateinit var postService: PostService

    @Autowired
    private lateinit var likeEventRepository: LikeEventRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `when likePost with valid request then returns Success`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId

        phases.act()
        val result = likeService.likePost(postId, userId)

        phases.assert()
        assertThat(result).isInstanceOf(LikeResult.Success::class.java)
        val success = result as LikeResult.Success
        assertThat(success.postId).isEqualTo(postId)
        assertThat(success.userId).isEqualTo(userId)
        assertThat(success.likedAt).isNotNull()

        val events = likeEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
        assertThat(events).hasSize(1)
        assertThat(events[0].postId).isEqualTo(postId)
        assertThat(events[0].userId).isEqualTo(userId)
        assertThat(events[0].eventType).isEqualTo(LikeEventType.LIKED.value)
    }

    @Test
    fun `when likePost with non-existent post then returns Failure with LikePostNotFoundException`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val nonExistentPostId = UUID.randomUUID()

        phases.act()
        val result = likeService.likePost(nonExistentPostId, userId)

        phases.assert()
        assertThat(result).isInstanceOf(LikeResult.Failure::class.java)
        val failure = result as LikeResult.Failure
        assertThat(failure.exception).isInstanceOf(LikePostNotFoundException::class.java)
    }

    @Test
    fun `when likePost with non-existent user then returns Failure with LikeUserNotFoundException`(phases: TestPhases) {
        phases.arrange()
        val authorId = UUID.randomUUID()
        userRepository.save(User(authorId, Instant.now()))
        val postResult = postService.createPost(authorId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId
        val nonExistentUserId = UUID.randomUUID()

        phases.act()
        val result = likeService.likePost(postId, nonExistentUserId)

        phases.assert()
        assertThat(result).isInstanceOf(LikeResult.Failure::class.java)
        val failure = result as LikeResult.Failure
        assertThat(failure.exception).isInstanceOf(LikeUserNotFoundException::class.java)
    }

    @Test
    fun `when unlikePost with valid request then returns Success and creates unlike event`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId
        likeService.likePost(postId, userId)

        phases.act()
        val result = likeService.unlikePost(postId, userId)

        phases.assert()
        assertThat(result).isInstanceOf(UnlikeResult.Success::class.java)
        val events = likeEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
        assertThat(events).hasSize(2)
        assertThat(events[0].eventType).isEqualTo(LikeEventType.LIKED.value)
        assertThat(events[1].eventType).isEqualTo(LikeEventType.UNLIKED.value)
    }

    @Test
    fun `when unlikePost with non-existent post then returns Failure with LikePostNotFoundException`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val nonExistentPostId = UUID.randomUUID()

        phases.act()
        val result = likeService.unlikePost(nonExistentPostId, userId)

        phases.assert()
        assertThat(result).isInstanceOf(UnlikeResult.Failure::class.java)
        val failure = result as UnlikeResult.Failure
        assertThat(failure.exception).isInstanceOf(LikePostNotFoundException::class.java)
    }

    @Test
    fun `when unlikePost with non-existent user then returns Failure with LikeUserNotFoundException`(phases: TestPhases) {
        phases.arrange()
        val authorId = UUID.randomUUID()
        userRepository.save(User(authorId, Instant.now()))
        val postResult = postService.createPost(authorId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId
        val nonExistentUserId = UUID.randomUUID()

        phases.act()
        val result = likeService.unlikePost(postId, nonExistentUserId)

        phases.assert()
        assertThat(result).isInstanceOf(UnlikeResult.Failure::class.java)
        val failure = result as UnlikeResult.Failure
        assertThat(failure.exception).isInstanceOf(LikeUserNotFoundException::class.java)
    }
}
