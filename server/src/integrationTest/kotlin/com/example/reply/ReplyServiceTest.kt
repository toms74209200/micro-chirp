package com.example.reply

import com.example.TestcontainersConfiguration
import com.example.auth.User
import com.example.auth.UserRepository
import com.example.post.PostCreationResult
import com.example.post.PostEventRepository
import com.example.post.PostEventType
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
class ReplyServiceTest {
    @Autowired
    private lateinit var replyService: ReplyService

    @Autowired
    private lateinit var postService: PostService

    @Autowired
    private lateinit var postEventRepository: PostEventRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `replyToPost with valid request returns Success`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Original post") as PostCreationResult.Success
        val postId = postResult.postId

        val replyContent = "Reply content"
        val result = replyService.replyToPost(postId, userId, replyContent)

        assertThat(result).isInstanceOf(ReplyCreationResult.Success::class.java)
        val success = result as ReplyCreationResult.Success
        assertThat(success.replyToPostId).isEqualTo(postId)
        assertThat(success.userId).isEqualTo(userId)
        assertThat(success.content).isEqualTo(replyContent)
        assertThat(success.createdAt).isNotNull()

        val events = postEventRepository.findByPostIdOrderByOccurredAtAsc(success.replyPostId)
        assertThat(events).hasSize(1)
        assertThat(events[0].postId).isEqualTo(success.replyPostId)
        assertThat(events[0].replyToPostId).isEqualTo(postId)
        assertThat(events[0].eventType).isEqualTo(PostEventType.POST_CREATED.value)
    }

    @Test
    fun `replyToPost with non-existent post returns PostNotFound`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val nonExistentPostId = UUID.randomUUID()

        val result = replyService.replyToPost(nonExistentPostId, userId, "Reply content")

        assertThat(result).isInstanceOf(ReplyCreationResult.PostNotFound::class.java)
    }

    @Test
    fun `replyToPost with non-existent user returns ValidationFailure`() {
        val existingUserId = UUID.randomUUID()
        userRepository.save(User(existingUserId, Instant.now()))
        val postResult = postService.createPost(existingUserId, "Original post") as PostCreationResult.Success
        val postId = postResult.postId

        val nonExistentUserId = UUID.randomUUID()
        val result = replyService.replyToPost(postId, nonExistentUserId, "Reply content")

        assertThat(result).isInstanceOf(ReplyCreationResult.ValidationFailure::class.java)
        val failure = result as ReplyCreationResult.ValidationFailure
        assertThat(failure.errorMessage).isEqualTo("User not found")
    }

    @Test
    fun `replyToPost with empty content returns ValidationFailure`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Original post") as PostCreationResult.Success
        val postId = postResult.postId

        val result = replyService.replyToPost(postId, userId, "")

        assertThat(result).isInstanceOf(ReplyCreationResult.ValidationFailure::class.java)
        val failure = result as ReplyCreationResult.ValidationFailure
        assertThat(failure.errorMessage).isEqualTo("Content is invalid")
    }

    @Test
    fun `replyToPost with content exceeding 280 graphemes returns ValidationFailure`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Original post") as PostCreationResult.Success
        val postId = postResult.postId

        val longContent = "a".repeat(281)
        val result = replyService.replyToPost(postId, userId, longContent)

        assertThat(result).isInstanceOf(ReplyCreationResult.ValidationFailure::class.java)
        val failure = result as ReplyCreationResult.ValidationFailure
        assertThat(failure.errorMessage).isEqualTo("Content is invalid")
    }

    @Test
    fun `replyToPost creates reply that can be queried by replyToPostId`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Original post") as PostCreationResult.Success
        val postId = postResult.postId

        val reply1 = replyService.replyToPost(postId, userId, "Reply 1") as ReplyCreationResult.Success
        val reply2 = replyService.replyToPost(postId, userId, "Reply 2") as ReplyCreationResult.Success

        val replies = postEventRepository.findByReplyToPostIdOrderByOccurredAtAsc(postId)
        assertThat(replies).hasSize(2)
        assertThat(replies[0].postId).isEqualTo(reply1.replyPostId)
        assertThat(replies[1].postId).isEqualTo(reply2.replyPostId)
    }
}
