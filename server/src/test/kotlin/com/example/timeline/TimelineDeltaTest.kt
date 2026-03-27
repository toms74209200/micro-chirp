package com.example.timeline

import com.example.post.PostEvent
import com.example.post.PostEventType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeSortedDescendingBy
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

class TimelineDeltaTest :
    FunSpec({
        val objectMapper = ObjectMapper()

        test("when buildTimelineDelta with active post matching user filter then returns post in activePosts") {
            checkAll(arbPostCreatedByUser(objectMapper)) { (postId, userId, event) ->
                val delta = buildTimelineDelta(mapOf(postId to listOf(event)), { id -> id == userId }, objectMapper)

                delta.activePosts.map { it.postId } shouldContain postId
            }
        }

        test("when buildTimelineDelta with active post not matching user filter then returns empty activePosts") {
            checkAll(arbPostCreatedByUser(objectMapper)) { (postId, _, event) ->
                val delta = buildTimelineDelta(mapOf(postId to listOf(event)), { _ -> false }, objectMapper)

                delta.activePosts shouldBe emptyList()
            }
        }

        test("when buildTimelineDelta with created then deleted post in delta then excludes from activePosts") {
            checkAll(arbCreatedThenDeletedEvents(objectMapper)) { (postId, events) ->
                val delta = buildTimelineDelta(mapOf(postId to events), { _ -> true }, objectMapper)

                delta.activePosts.map { it.postId } shouldNotContain postId
            }
        }

        test("when buildTimelineDelta with created then deleted post in delta then includes in deletedIds") {
            checkAll(arbCreatedThenDeletedEvents(objectMapper)) { (postId, events) ->
                val delta = buildTimelineDelta(mapOf(postId to events), { _ -> true }, objectMapper)

                delta.deletedIds shouldContain postId
            }
        }

        test("when buildTimelineDelta with created then deleted post in delta then returns zero deletedFromMvCount") {
            checkAll(arbCreatedThenDeletedWithUserId(objectMapper)) { (postId, userId, events) ->
                val delta = buildTimelineDelta(mapOf(postId to events), { id -> id == userId }, objectMapper)

                delta.deletedFromMvCount shouldBe 0
            }
        }

        test("when buildTimelineDelta with only delete event matching user filter then returns one deletedFromMvCount") {
            checkAll(arbDeleteOnlyEventWithUserId(objectMapper)) { (postId, userId, event) ->
                val delta = buildTimelineDelta(mapOf(postId to listOf(event)), { id -> id == userId }, objectMapper)

                delta.deletedFromMvCount shouldBe 1
            }
        }

        test("when buildTimelineDelta with only delete event not matching user filter then returns zero deletedFromMvCount") {
            checkAll(arbDeleteOnlyEventWithUserId(objectMapper)) { (postId, _, event) ->
                val delta = buildTimelineDelta(mapOf(postId to listOf(event)), { _ -> false }, objectMapper)

                delta.deletedFromMvCount shouldBe 0
            }
        }

        test("when buildTimelineDelta with multiple active posts then returns activePosts sorted by createdAt desc") {
            checkAll(arbMultiplePostCreatedEvents(objectMapper)) { posts ->
                val deltaByPostId = posts.associate { (postId, _, event) -> postId to listOf(event) }
                val delta = buildTimelineDelta(deltaByPostId, { _ -> true }, objectMapper)

                delta.activePosts.shouldBeSortedDescendingBy { it.createdAt }
            }
        }
    })

private data class PostWithUser(
    val postId: UUID,
    val userId: UUID,
    val event: PostEvent,
)

private data class PostWithEvents(
    val postId: UUID,
    val events: List<PostEvent>,
)

private data class PostWithUserAndEvents(
    val postId: UUID,
    val userId: UUID,
    val events: List<PostEvent>,
)

private fun arbPostCreatedByUser(objectMapper: ObjectMapper): Arb<PostWithUser> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
    ) { postId, userId, content, occurredAt ->
        val eventData = objectMapper.writeValueAsString(mapOf("userId" to userId.toString(), "content" to content))
        val event =
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = eventData,
                occurredAt = occurredAt,
            )
        PostWithUser(postId, userId, event)
    }

private fun arbCreatedThenDeletedEvents(objectMapper: ObjectMapper): Arb<PostWithEvents> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
        arbInstant(),
    ) { postId, userId, content, createdAt, deletedAt ->
        val createData = objectMapper.writeValueAsString(mapOf("userId" to userId.toString(), "content" to content))
        val deleteData = objectMapper.writeValueAsString(mapOf("userId" to userId.toString()))
        val events =
            listOf(
                PostEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    eventType = PostEventType.POST_CREATED.value,
                    eventData = createData,
                    occurredAt = createdAt,
                ),
                PostEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    eventType = PostEventType.POST_DELETED.value,
                    eventData = deleteData,
                    occurredAt = deletedAt,
                ),
            )
        PostWithEvents(postId, events)
    }

private fun arbCreatedThenDeletedWithUserId(objectMapper: ObjectMapper): Arb<PostWithUserAndEvents> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
        arbInstant(),
    ) { postId, userId, content, createdAt, deletedAt ->
        val createData = objectMapper.writeValueAsString(mapOf("userId" to userId.toString(), "content" to content))
        val deleteData = objectMapper.writeValueAsString(mapOf("userId" to userId.toString()))
        val events =
            listOf(
                PostEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    eventType = PostEventType.POST_CREATED.value,
                    eventData = createData,
                    occurredAt = createdAt,
                ),
                PostEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    eventType = PostEventType.POST_DELETED.value,
                    eventData = deleteData,
                    occurredAt = deletedAt,
                ),
            )
        PostWithUserAndEvents(postId, userId, events)
    }

private fun arbDeleteOnlyEventWithUserId(objectMapper: ObjectMapper): Arb<PostWithUser> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
    ) { postId, userId, occurredAt ->
        val deleteData = objectMapper.writeValueAsString(mapOf("userId" to userId.toString()))
        val event =
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_DELETED.value,
                eventData = deleteData,
                occurredAt = occurredAt,
            )
        PostWithUser(postId, userId, event)
    }

private fun arbMultiplePostCreatedEvents(objectMapper: ObjectMapper): Arb<List<PostWithUser>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
    ) { postId1, userId1, content1, occurredAt1, postId2, userId2, content2, occurredAt2 ->
        listOf(
            PostWithUser(
                postId1,
                userId1,
                PostEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId1,
                    eventType = PostEventType.POST_CREATED.value,
                    eventData = objectMapper.writeValueAsString(mapOf("userId" to userId1.toString(), "content" to content1)),
                    occurredAt = occurredAt1,
                ),
            ),
            PostWithUser(
                postId2,
                userId2,
                PostEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId2,
                    eventType = PostEventType.POST_CREATED.value,
                    eventData = objectMapper.writeValueAsString(mapOf("userId" to userId2.toString(), "content" to content2)),
                    occurredAt = occurredAt2,
                ),
            ),
        )
    }

private fun arbInstant(): Arb<Instant> = Arb.long(0..253402300799L).map { Instant.ofEpochSecond(it) }
