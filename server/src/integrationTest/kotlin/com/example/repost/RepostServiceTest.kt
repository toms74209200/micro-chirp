package com.example.repost

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
class RepostServiceTest {
    @Autowired
    private lateinit var repostService: RepostService

    @Autowired
    private lateinit var postService: PostService

    @Autowired
    private lateinit var repostEventRepository: RepostEventRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `repostPost with valid request returns Success`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val postResult = postService.createPost(userId, "Test post") as PostCreationResult.Success
        val postId = postResult.postId

        val result = repostService.repostPost(postId, userId)

        assertThat(result).isInstanceOf(RepostResult.Success::class.java)
        val success = result as RepostResult.Success
        assertThat(success.postId).isEqualTo(postId)
        assertThat(success.userId).isEqualTo(userId)
        assertThat(success.repostedAt).isNotNull()

        val events = repostEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
        assertThat(events).hasSize(1)
        assertThat(events[0].postId).isEqualTo(postId)
        assertThat(events[0].userId).isEqualTo(userId)
        assertThat(events[0].eventType).isEqualTo(RepostEventType.REPOSTED.value)
    }

    @Test
    fun `repostPost with non-existent post returns PostNotFound`() {
        val userId = UUID.randomUUID()
        val nonExistentPostId = UUID.randomUUID()

        val result = repostService.repostPost(nonExistentPostId, userId)

        assertThat(result).isInstanceOf(RepostResult.PostNotFound::class.java)
    }
}
