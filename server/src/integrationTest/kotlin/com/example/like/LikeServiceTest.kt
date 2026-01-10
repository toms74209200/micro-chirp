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
    fun `likePost with non-existent post returns PostNotFound`() {
        val userId = UUID.randomUUID()
        val nonExistentPostId = UUID.randomUUID()

        val result = likeService.likePost(nonExistentPostId, userId)

        assertThat(result).isInstanceOf(LikeResult.PostNotFound::class.java)
    }

    @Test
    fun `likePost with deleted post returns PostNotFound`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId

        postService.deletePost(postId, userId)

        val result = likeService.likePost(postId, userId)

        assertThat(result).isInstanceOf(LikeResult.PostNotFound::class.java)
    }
}
