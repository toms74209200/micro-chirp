package com.example.view

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
class ViewServiceTest {
    @Autowired
    private lateinit var viewService: ViewService

    @Autowired
    private lateinit var postService: PostService

    @Autowired
    private lateinit var viewEventRepository: ViewEventRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `recordView with valid request returns Success and persists event`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId

        val result = viewService.recordView(postId, userId)

        assertThat(result).isInstanceOf(ViewResult.Success::class.java)
        val success = result as ViewResult.Success
        assertThat(success.postId).isEqualTo(postId)
        assertThat(success.userId).isEqualTo(userId)
        assertThat(success.viewedAt).isNotNull()

        val events = viewEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
        assertThat(events).hasSize(1)
        assertThat(events[0].postId).isEqualTo(postId)
        assertThat(events[0].userId).isEqualTo(userId)
    }

    @Test
    fun `recordView with non-existent post returns Failure with ViewPostNotFoundException`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val nonExistentPostId = UUID.randomUUID()

        val result = viewService.recordView(nonExistentPostId, userId)

        assertThat(result).isInstanceOf(ViewResult.Failure::class.java)
        val failure = result as ViewResult.Failure
        assertThat(failure.exception).isInstanceOf(ViewPostNotFoundException::class.java)
    }

    @Test
    fun `recordView with non-existent user returns Failure with ViewUserNotFoundException`() {
        val authorId = UUID.randomUUID()
        userRepository.save(User(authorId, Instant.now()))
        val postResult = postService.createPost(authorId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId
        val nonExistentUserId = UUID.randomUUID()

        val result = viewService.recordView(postId, nonExistentUserId)

        assertThat(result).isInstanceOf(ViewResult.Failure::class.java)
        val failure = result as ViewResult.Failure
        assertThat(failure.exception).isInstanceOf(ViewUserNotFoundException::class.java)
    }

    @Test
    fun `recordView multiple times increments viewCount`() {
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        userRepository.save(User(userId1, Instant.now()))
        userRepository.save(User(userId2, Instant.now()))
        val postResult = postService.createPost(userId1, "Test post") as PostCreationResult.Success
        val postId = postResult.postId

        viewService.recordView(postId, userId1)
        viewService.recordView(postId, userId2)

        val events = viewEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
        assertThat(events).hasSize(2)

        val getPostResult = postService.getPost(postId, null) as com.example.post.PostRetrievalResult.Success
        assertThat(getPostResult.viewCount).isEqualTo(2)
    }
}
