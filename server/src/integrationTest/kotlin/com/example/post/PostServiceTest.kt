package com.example.post

import com.example.TestcontainersConfiguration
import com.example.auth.User
import com.example.auth.UserRepository
import com.example.like.LikeEvent
import com.example.like.LikeEventRepository
import com.example.like.LikeEventType
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

    @Autowired
    private lateinit var likeEventRepository: LikeEventRepository

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
    fun `when createPost with blank content then returns Failure with PostValidationException`() {
        val userId = UUID.randomUUID()
        val content = "   "

        val result = postService.createPost(userId, content)

        assertThat(result).isInstanceOf(PostCreationResult.Failure::class.java)
        val failure = result as PostCreationResult.Failure
        assertThat(failure.exception).isInstanceOf(PostValidationException::class.java)
    }

    @Test
    fun `when createPost with content exceeding 280 graphemes then returns Failure with PostValidationException`() {
        val userId = UUID.randomUUID()
        val content = RandomStringUtils.randomAlphanumeric(281)

        val result = postService.createPost(userId, content)

        assertThat(result).isInstanceOf(PostCreationResult.Failure::class.java)
        val failure = result as PostCreationResult.Failure
        assertThat(failure.exception).isInstanceOf(PostValidationException::class.java)
    }

    @Test
    fun `when createPost with non-existent userId then returns Failure with PostUserNotFoundException`() {
        val userId = UUID.randomUUID()
        val content = RandomStringUtils.randomAlphanumeric(50)

        val result = postService.createPost(userId, content)

        assertThat(result).isInstanceOf(PostCreationResult.Failure::class.java)
        val failure = result as PostCreationResult.Failure
        assertThat(failure.exception).isInstanceOf(PostUserNotFoundException::class.java)
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
    fun `when getPost with non-existent postId then returns Failure with PostNotFoundException`() {
        val nonExistentPostId = UUID.randomUUID()

        val result = postService.getPost(nonExistentPostId, null)

        assertThat(result).isInstanceOf(PostRetrievalResult.Failure::class.java)
        val failure = result as PostRetrievalResult.Failure
        assertThat(failure.exception).isInstanceOf(PostNotFoundException::class.java)
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

    @Test
    fun `when getPost with deleted post then returns Failure with PostNotFoundException`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val content = "Test post content"
        val createResult = postService.createPost(userId, content) as PostCreationResult.Success
        postService.deletePost(createResult.postId, userId)

        val result = postService.getPost(createResult.postId, null)

        assertThat(result).isInstanceOf(PostRetrievalResult.Failure::class.java)
        val failure = result as PostRetrievalResult.Failure
        assertThat(failure.exception).isInstanceOf(PostNotFoundException::class.java)
    }

    @Test
    fun `when deletePost with valid request then returns Success and creates delete event`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val content = "Test post content"
        val createResult = postService.createPost(userId, content) as PostCreationResult.Success

        val result = postService.deletePost(createResult.postId, userId)

        assertThat(result).isInstanceOf(PostDeletionResult.Success::class.java)
        val events = postEventRepository.findByPostIdOrderByOccurredAtAsc(createResult.postId)
        assertThat(events).hasSize(2)
        assertThat(events[0].eventType).isEqualTo("post_created")
        assertThat(events[1].eventType).isEqualTo("post_deleted")
        assertThat(events[1].postId).isEqualTo(createResult.postId)
    }

    @Test
    fun `when deletePost with non-existent postId then returns Failure with PostNotFoundException`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val nonExistentPostId = UUID.randomUUID()

        val result = postService.deletePost(nonExistentPostId, userId)

        assertThat(result).isInstanceOf(PostDeletionResult.Failure::class.java)
        val failure = result as PostDeletionResult.Failure
        assertThat(failure.exception).isInstanceOf(PostNotFoundException::class.java)
    }

    @Test
    fun `when deletePost with different user then returns Failure with PostDeletionForbiddenException`() {
        val authorId = UUID.randomUUID()
        val otherId = UUID.randomUUID()
        userRepository.save(User(authorId, Instant.now()))
        userRepository.save(User(otherId, Instant.now()))
        val content = "Test post content"
        val createResult = postService.createPost(authorId, content) as PostCreationResult.Success

        val result = postService.deletePost(createResult.postId, otherId)

        assertThat(result).isInstanceOf(PostDeletionResult.Failure::class.java)
        val failure = result as PostDeletionResult.Failure
        assertThat(failure.exception).isInstanceOf(PostDeletionForbiddenException::class.java)
        val events = postEventRepository.findByPostIdOrderByOccurredAtAsc(createResult.postId)
        assertThat(events).hasSize(1)
    }

    @Test
    fun `when deletePost with non-existent user then returns Failure with PostUserNotFoundException`() {
        val authorId = UUID.randomUUID()
        userRepository.save(User(authorId, Instant.now()))
        val content = "Test post content"
        val createResult = postService.createPost(authorId, content) as PostCreationResult.Success
        val nonExistentUserId = UUID.randomUUID()

        val result = postService.deletePost(createResult.postId, nonExistentUserId)

        assertThat(result).isInstanceOf(PostDeletionResult.Failure::class.java)
        val failure = result as PostDeletionResult.Failure
        assertThat(failure.exception).isInstanceOf(PostUserNotFoundException::class.java)
    }

    @Test
    fun `when deletePost with already deleted post then returns Failure with PostNotFoundException`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val content = "Test post content"
        val createResult = postService.createPost(userId, content) as PostCreationResult.Success
        postService.deletePost(createResult.postId, userId)

        val result = postService.deletePost(createResult.postId, userId)

        assertThat(result).isInstanceOf(PostDeletionResult.Failure::class.java)
        val failure = result as PostDeletionResult.Failure
        assertThat(failure.exception).isInstanceOf(PostNotFoundException::class.java)
    }

    @Test
    fun `getPost with liked post by current user returns likeCount and isLikedByCurrentUser true`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val content = "Test post content"
        val createResult = postService.createPost(userId, content) as PostCreationResult.Success
        val postId = createResult.postId

        val likeEvent =
            LikeEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = LikeEventType.LIKED.value,
                occurredAt = Instant.now(),
            )
        likeEventRepository.save(likeEvent)

        val result = postService.getPost(postId, userId)

        assertThat(result).isInstanceOf(PostRetrievalResult.Success::class.java)
        val success = result as PostRetrievalResult.Success
        assertThat(success.likeCount).isEqualTo(1)
        assertThat(success.isLikedByCurrentUser).isTrue()
    }

    @Test
    fun `when getPosts with replyToPostId then returns Success with replies`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val parentPost = postService.createPost(userId, "Parent post") as PostCreationResult.Success
        val replyPostId1 = UUID.randomUUID()
        val replyPostId2 = UUID.randomUUID()
        postEventRepository.save(
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = replyPostId1,
                replyToPostId = parentPost.postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = """{"userId":"$userId","content":"Reply A"}""",
                occurredAt = Instant.now(),
            ),
        )
        postEventRepository.save(
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = replyPostId2,
                replyToPostId = parentPost.postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = """{"userId":"$userId","content":"Reply B"}""",
                occurredAt = Instant.now(),
            ),
        )

        val result = postService.getPosts(null, parentPost.postId, null, 20, 0)

        assertThat(result).isInstanceOf(PostsRetrievalResult.Success::class.java)
        val success = result as PostsRetrievalResult.Success
        assertThat(success.total).isEqualTo(2)
        assertThat(success.posts).hasSize(2)
        assertThat(success.posts.map { it.postId }).containsExactlyInAnyOrder(replyPostId1, replyPostId2)
        assertThat(success.posts).allMatch { it.replyToPostId == parentPost.postId }
    }

    @Test
    fun `when getPosts with replyToPostId and no replies then returns empty Success`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val parentPost = postService.createPost(userId, "Parent post") as PostCreationResult.Success

        val result = postService.getPosts(null, parentPost.postId, null, 20, 0)

        assertThat(result).isInstanceOf(PostsRetrievalResult.Success::class.java)
        val success = result as PostsRetrievalResult.Success
        assertThat(success.total).isEqualTo(0)
        assertThat(success.posts).isEmpty()
    }

    @Test
    fun `when getPosts with replyToPostId and deleted reply then excludes deleted reply`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val parentPost = postService.createPost(userId, "Parent post") as PostCreationResult.Success
        val replyPostId = UUID.randomUUID()
        postEventRepository.save(
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = replyPostId,
                replyToPostId = parentPost.postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = """{"userId":"$userId","content":"Reply to delete"}""",
                occurredAt = Instant.now(),
            ),
        )
        postEventRepository.save(
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = replyPostId,
                eventType = PostEventType.POST_DELETED.value,
                eventData = """{"userId":"$userId"}""",
                occurredAt = Instant.now(),
            ),
        )

        val result = postService.getPosts(null, parentPost.postId, null, 20, 0)

        assertThat(result).isInstanceOf(PostsRetrievalResult.Success::class.java)
        val success = result as PostsRetrievalResult.Success
        assertThat(success.total).isEqualTo(0)
        assertThat(success.posts).isEmpty()
    }

    @Test
    fun `when getPosts with ids then returns Success with specified posts`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val post1 = postService.createPost(userId, "Post 1") as PostCreationResult.Success
        val post2 = postService.createPost(userId, "Post 2") as PostCreationResult.Success
        postService.createPost(userId, "Post 3")

        val result = postService.getPosts(listOf(post1.postId, post2.postId), null, null, 20, 0)

        assertThat(result).isInstanceOf(PostsRetrievalResult.Success::class.java)
        val success = result as PostsRetrievalResult.Success
        assertThat(success.total).isEqualTo(2)
        assertThat(success.posts.map { it.postId }).containsExactlyInAnyOrder(post1.postId, post2.postId)
    }

    @Test
    fun `when getPosts with limit and offset then returns paginated results`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val parentPostId = UUID.randomUUID()
        postEventRepository.save(
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = parentPostId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = """{"userId":"$userId","content":"Parent"}""",
                occurredAt = Instant.now(),
            ),
        )
        val replyIds = (1..5).map { i ->
            val id = UUID.randomUUID()
            postEventRepository.save(
                PostEvent(
                    eventId = UUID.randomUUID(),
                    postId = id,
                    replyToPostId = parentPostId,
                    eventType = PostEventType.POST_CREATED.value,
                    eventData = """{"userId":"$userId","content":"Reply $i"}""",
                    occurredAt = Instant.now(),
                ),
            )
            id
        }

        val result = postService.getPosts(null, parentPostId, null, 2, 1)

        assertThat(result).isInstanceOf(PostsRetrievalResult.Success::class.java)
        val success = result as PostsRetrievalResult.Success
        assertThat(success.total).isEqualTo(5)
        assertThat(success.posts).hasSize(2)
        assertThat(success.limit).isEqualTo(2)
        assertThat(success.offset).isEqualTo(1)
    }

    @Test
    fun `when getPosts with currentUserId then returns isLikedByCurrentUser`() {
        val userId = UUID.randomUUID()
        userRepository.save(User(userId, Instant.now()))
        val replyPostId = UUID.randomUUID()
        val parentPostId = UUID.randomUUID()
        postEventRepository.save(
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = parentPostId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = """{"userId":"$userId","content":"Parent"}""",
                occurredAt = Instant.now(),
            ),
        )
        postEventRepository.save(
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = replyPostId,
                replyToPostId = parentPostId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = """{"userId":"$userId","content":"Reply"}""",
                occurredAt = Instant.now(),
            ),
        )
        likeEventRepository.save(
            LikeEvent(
                eventId = UUID.randomUUID(),
                postId = replyPostId,
                userId = userId,
                eventType = LikeEventType.LIKED.value,
                occurredAt = Instant.now(),
            ),
        )

        val result = postService.getPosts(null, parentPostId, userId, 20, 0)

        assertThat(result).isInstanceOf(PostsRetrievalResult.Success::class.java)
        val success = result as PostsRetrievalResult.Success
        assertThat(success.posts).hasSize(1)
        assertThat(success.posts[0].likeCount).isEqualTo(1)
        assertThat(success.posts[0].isLikedByCurrentUser).isTrue()
    }
}
