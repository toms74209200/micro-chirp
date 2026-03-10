package com.example.post

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

class CountActiveRepliesTest :
    FunSpec({
        val objectMapper = ObjectMapper()

        test("when countActiveReplies with empty map then returns 0") {
            countActiveReplies(emptyMap(), objectMapper) shouldBe 0
        }

        test("when countActiveReplies with single active reply then returns 1") {
            checkAll(arbActiveReplyEvents(objectMapper)) { (replyPostId, events) ->
                val result = countActiveReplies(mapOf(replyPostId to events), objectMapper)
                result shouldBe 1
            }
        }

        test("when countActiveReplies with single deleted reply then returns 0") {
            checkAll(arbDeletedReplyEvents(objectMapper)) { (replyPostId, events) ->
                val result = countActiveReplies(mapOf(replyPostId to events), objectMapper)
                result shouldBe 0
            }
        }

        test("when countActiveReplies with multiple active replies then returns count") {
            checkAll(Arb.list(arbActiveReplyEvents(objectMapper), 2..5)) { replies ->
                val eventsByPostId = replies.associate { (id, events) -> id to events }
                val result = countActiveReplies(eventsByPostId, objectMapper)
                result shouldBe eventsByPostId.size
            }
        }

        test("when countActiveReplies with mixed active and deleted replies then returns only active count") {
            checkAll(
                Arb.list(arbActiveReplyEvents(objectMapper), 1..3),
                Arb.list(arbDeletedReplyEvents(objectMapper), 1..3),
            ) { active, deleted ->
                val eventsByPostId = (active + deleted).associate { (id, events) -> id to events }
                val activeIds = active.map { it.first }.toSet()
                val deletedIds = deleted.map { it.first }.toSet()
                val expectedCount = (activeIds - deletedIds).size
                val result = countActiveReplies(eventsByPostId, objectMapper)
                result shouldBe expectedCount
            }
        }
    })

private fun arbInstant(): Arb<Instant> = Arb.long(0..253402300799L).map { Instant.ofEpochSecond(it) }

private fun arbActiveReplyEvents(objectMapper: ObjectMapper): Arb<Pair<UUID, List<PostEvent>>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
    ) { replyPostId, userId, content, occurredAt ->
        val eventData = mapOf("userId" to userId.toString(), "content" to content)
        val event =
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = replyPostId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = objectMapper.writeValueAsString(eventData),
                occurredAt = occurredAt,
            )
        replyPostId to listOf(event)
    }

private fun arbDeletedReplyEvents(objectMapper: ObjectMapper): Arb<Pair<UUID, List<PostEvent>>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
        arbInstant(),
    ) { replyPostId, userId, content, createdAt, deletedAt ->
        val eventData = mapOf("userId" to userId.toString(), "content" to content)
        val events =
            listOf(
                PostEvent(
                    eventId = UUID.randomUUID(),
                    postId = replyPostId,
                    eventType = PostEventType.POST_CREATED.value,
                    eventData = objectMapper.writeValueAsString(eventData),
                    occurredAt = createdAt,
                ),
                PostEvent(
                    eventId = UUID.randomUUID(),
                    postId = replyPostId,
                    eventType = PostEventType.POST_DELETED.value,
                    eventData = "{}",
                    occurredAt = deletedAt,
                ),
            )
        replyPostId to events
    }
