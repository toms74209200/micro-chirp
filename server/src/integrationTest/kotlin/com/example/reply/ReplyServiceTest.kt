package com.example.reply

import com.example.TestcontainersConfiguration
import com.example.auth.User
import com.example.auth.UserRepository
import com.example.post.PostCreationResult
import com.example.post.PostEventRepository
import com.example.post.PostEventType
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
    fun `replyToPost with valid request returns Success`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Original post") as PostCreationResult.Success
        val postId = postResult.postId

        phases.act()
        val replyContent = "Reply content"
        val result = replyService.replyToPost(postId, userId, replyContent)

        phases.assert()
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
    fun `replyToPost with non-existent post returns PostNotFound`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val nonExistentPostId = UUID.randomUUID()

        phases.act()
        val result = replyService.replyToPost(nonExistentPostId, userId, "Reply content")

        phases.assert()
        assertThat(result).isInstanceOf(ReplyCreationResult.PostNotFound::class.java)
    }

    @Test
    fun `replyToPost with non-existent user returns ValidationFailure`(phases: TestPhases) {
        phases.arrange()
        val existingUserId = UUID.randomUUID()
        userRepository.save(User(existingUserId, Instant.now()))
        val postResult = postService.createPost(existingUserId, "Original post") as PostCreationResult.Success
        val postId = postResult.postId
        val nonExistentUserId = UUID.randomUUID()

        phases.act()
        val result = replyService.replyToPost(postId, nonExistentUserId, "Reply content")

        phases.assert()
        assertThat(result).isInstanceOf(ReplyCreationResult.ValidationFailure::class.java)
        val failure = result as ReplyCreationResult.ValidationFailure
        assertThat(failure.errorMessage).isEqualTo("User not found")
    }

    @Test
    fun `replyToPost with empty content returns ValidationFailure`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Original post") as PostCreationResult.Success
        val postId = postResult.postId

        phases.act()
        val result = replyService.replyToPost(postId, userId, "")

        phases.assert()
        assertThat(result).isInstanceOf(ReplyCreationResult.ValidationFailure::class.java)
        val failure = result as ReplyCreationResult.ValidationFailure
        assertThat(failure.errorMessage).isEqualTo("Content is invalid")
    }

    @Test
    fun `replyToPost with content exceeding 280 graphemes returns ValidationFailure`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Original post") as PostCreationResult.Success
        val postId = postResult.postId

        phases.act()
        val longContent = "a".repeat(281)
        val result = replyService.replyToPost(postId, userId, longContent)

        phases.assert()
        assertThat(result).isInstanceOf(ReplyCreationResult.ValidationFailure::class.java)
        val failure = result as ReplyCreationResult.ValidationFailure
        assertThat(failure.errorMessage).isEqualTo("Content is invalid")
    }

    @Test
    fun `replyToPost creates reply that can be queried by replyToPostId`(phases: TestPhases) {
        phases.arrange()
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Original post") as PostCreationResult.Success
        val postId = postResult.postId

        phases.act()
        val reply1 = replyService.replyToPost(postId, userId, "Reply 1") as ReplyCreationResult.Success
        val reply2 = replyService.replyToPost(postId, userId, "Reply 2") as ReplyCreationResult.Success

        phases.assert()
        val replies = postEventRepository.findByReplyToPostIdOrderByOccurredAtAsc(postId)
        assertThat(replies).hasSize(2)
        assertThat(replies[0].postId).isEqualTo(reply1.replyPostId)
        assertThat(replies[1].postId).isEqualTo(reply2.replyPostId)
    }
}
