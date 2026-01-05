package com.example.post

import com.example.TestcontainersConfiguration
import com.example.auth.User
import com.example.auth.UserRepository
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PostServiceTest {
    @Autowired
    private lateinit var postService: PostService

    @Autowired
    private lateinit var postEventRepository: PostEventRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `when createPost with valid content then returns Success with post details`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val content = RandomStringUtils.randomAlphanumeric(50)

        val result = postService.createPost(userId, content)

        assertThat(result).isInstanceOf(PostCreationResult.Success::class.java)
        val success = result as PostCreationResult.Success
        assertThat(success.postId).isNotNull()
        assertThat(success.userId).isEqualTo(userId)
        assertThat(success.content).isEqualTo(content)
        assertThat(success.createdAt).isNotNull()

        val events = postEventRepository.findByPostIdOrderByOccurredAtAsc(success.postId)
        assertThat(events).hasSize(1)
        assertThat(events[0].postId).isEqualTo(success.postId)
        assertThat(events[0].eventType).isEqualTo("post_created")
    }

    @Test
    fun `when createPost with blank content then returns ValidationFailure`() {
        val userId = UUID.randomUUID()
        val content = "   "

        val result = postService.createPost(userId, content)

        assertThat(result).isInstanceOf(PostCreationResult.ValidationFailure::class.java)
        val failure = result as PostCreationResult.ValidationFailure
        assertThat(failure.errorMessage).isEqualTo("Content is invalid")
    }

    @Test
    fun `when createPost with content exceeding 280 graphemes then returns ValidationFailure`() {
        val userId = UUID.randomUUID()
        val content = RandomStringUtils.randomAlphanumeric(281)

        val result = postService.createPost(userId, content)

        assertThat(result).isInstanceOf(PostCreationResult.ValidationFailure::class.java)
        val failure = result as PostCreationResult.ValidationFailure
        assertThat(failure.errorMessage).isEqualTo("Content is invalid")
    }

    @Test
    fun `when createPost with non-existent userId then returns ValidationFailure`() {
        val userId = UUID.randomUUID()
        val content = RandomStringUtils.randomAlphanumeric(50)

        val result = postService.createPost(userId, content)

        assertThat(result).isInstanceOf(PostCreationResult.ValidationFailure::class.java)
        val failure = result as PostCreationResult.ValidationFailure
        assertThat(failure.errorMessage).isEqualTo("User not found")
    }

    @Test
    fun `when getPost with existing post then returns Success with post details`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val content = "Test post content"
        val createResult = postService.createPost(userId, content) as PostCreationResult.Success

        val result = postService.getPost(createResult.postId, null)

        assertThat(result).isInstanceOf(PostRetrievalResult.Success::class.java)
        val success = result as PostRetrievalResult.Success
        assertThat(success.postId).isEqualTo(createResult.postId)
        assertThat(success.userId).isEqualTo(userId)
        assertThat(success.content).isEqualTo(content)
        assertThat(success.createdAt.epochSecond).isEqualTo(createResult.createdAt.epochSecond)
        assertThat(success.likeCount).isEqualTo(0)
        assertThat(success.repostCount).isEqualTo(0)
        assertThat(success.replyCount).isEqualTo(0)
        assertThat(success.viewCount).isEqualTo(0)
    }

    @Test
    fun `when getPost with non-existent postId then returns NotFound`() {
        val nonExistentPostId = UUID.randomUUID()

        val result = postService.getPost(nonExistentPostId, null)

        assertThat(result).isInstanceOf(PostRetrievalResult.NotFound::class.java)
    }

    @Test
    fun `when getPost with currentUserId then returns isLikedByCurrentUser and isRepostedByCurrentUser`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val content = "Test post content"
        val createResult = postService.createPost(userId, content) as PostCreationResult.Success
        val currentUserId = UUID.randomUUID()

        val result = postService.getPost(createResult.postId, currentUserId)

        assertThat(result).isInstanceOf(PostRetrievalResult.Success::class.java)
        val success = result as PostRetrievalResult.Success
        assertThat(success.isLikedByCurrentUser).isFalse()
        assertThat(success.isRepostedByCurrentUser).isFalse()
    }
}
