package com.example.like

import com.example.TestcontainersConfiguration
import com.example.auth.User
import com.example.auth.UserRepository
import com.example.post.PostCreationResult
import com.example.post.PostService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
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
    fun `likePost with valid request returns Success`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId

        val result = likeService.likePost(postId, userId)

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
    fun `likePost with non-existent post returns Failure with LikePostNotFoundException`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val nonExistentPostId = UUID.randomUUID()

        val result = likeService.likePost(nonExistentPostId, userId)

        assertThat(result).isInstanceOf(LikeResult.Failure::class.java)
        val failure = result as LikeResult.Failure
        assertThat(failure.exception).isInstanceOf(LikePostNotFoundException::class.java)
    }

    @Test
    fun `likePost with non-existent user returns Failure with LikeUserNotFoundException`() {
        val authorId = UUID.randomUUID()
        userRepository.save(User(authorId, Instant.now()))
        val postResult = postService.createPost(authorId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId
        val nonExistentUserId = UUID.randomUUID()

        val result = likeService.likePost(postId, nonExistentUserId)

        assertThat(result).isInstanceOf(LikeResult.Failure::class.java)
        val failure = result as LikeResult.Failure
        assertThat(failure.exception).isInstanceOf(LikeUserNotFoundException::class.java)
    }

    @Test
    fun `unlikePost with valid request returns Success and creates unlike event`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId
        likeService.likePost(postId, userId)

        val result = likeService.unlikePost(postId, userId)

        assertThat(result).isInstanceOf(UnlikeResult.Success::class.java)
        val events = likeEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
        assertThat(events).hasSize(2)
        assertThat(events[0].eventType).isEqualTo(LikeEventType.LIKED.value)
        assertThat(events[1].eventType).isEqualTo(LikeEventType.UNLIKED.value)
    }

    @Test
    fun `unlikePost with non-existent post returns Failure with LikePostNotFoundException`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val nonExistentPostId = UUID.randomUUID()

        val result = likeService.unlikePost(nonExistentPostId, userId)

        assertThat(result).isInstanceOf(UnlikeResult.Failure::class.java)
        val failure = result as UnlikeResult.Failure
        assertThat(failure.exception).isInstanceOf(LikePostNotFoundException::class.java)
    }

    @Test
    fun `unlikePost with non-existent user returns Failure with LikeUserNotFoundException`() {
        val authorId = UUID.randomUUID()
        userRepository.save(User(authorId, Instant.now()))
        val postResult = postService.createPost(authorId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId
        val nonExistentUserId = UUID.randomUUID()

        val result = likeService.unlikePost(postId, nonExistentUserId)

        assertThat(result).isInstanceOf(UnlikeResult.Failure::class.java)
        val failure = result as UnlikeResult.Failure
        assertThat(failure.exception).isInstanceOf(LikeUserNotFoundException::class.java)
    }
}
