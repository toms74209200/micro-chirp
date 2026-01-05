package com.example.post

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

class AggregatedPostTest :
    FunSpec({
        val objectMapper = ObjectMapper()

        test("when aggregatePostEvents with empty list then returns null") {
            checkAll(Arb.constant(emptyList<PostEvent>())) { events ->
                aggregatePostEvents(events, objectMapper).shouldBeNull()
            }
        }

        test("when aggregatePostEvents with single valid post_created then returns AggregatedPost") {
            checkAll(arbValidPostCreatedEvent(objectMapper)) { event ->
                val result = aggregatePostEvents(listOf(event), objectMapper)

                result.shouldNotBeNull()
                val eventData = objectMapper.readValue(event.eventData, Map::class.java)
                val expectedUserId = UUID.fromString(eventData["userId"] as String)
                val expectedContent = eventData["content"] as String

                result.userId shouldBe expectedUserId
                result.content shouldBe expectedContent
                result.createdAt shouldBe event.occurredAt
            }
        }

        test("when aggregatePostEvents with single post_deleted then returns null") {
            checkAll(arbPostDeletedEvent()) { event ->
                aggregatePostEvents(listOf(event), objectMapper).shouldBeNull()
            }
        }

        test("when aggregatePostEvents with single post_created with invalid userId then returns null") {
            checkAll(arbPostCreatedEventWithInvalidUserId(objectMapper)) { event ->
                aggregatePostEvents(listOf(event), objectMapper).shouldBeNull()
            }
        }

        test("when aggregatePostEvents with single post_created with missing userId then returns null") {
            checkAll(arbPostCreatedEventWithMissingUserId(objectMapper)) { event ->
                aggregatePostEvents(listOf(event), objectMapper).shouldBeNull()
            }
        }

        test("when aggregatePostEvents with single post_created with missing content then returns null") {
            checkAll(arbPostCreatedEventWithMissingContent(objectMapper)) { event ->
                aggregatePostEvents(listOf(event), objectMapper).shouldBeNull()
            }
        }

        test("when aggregatePostEvents with list ending in post_deleted after valid post_created then returns null") {
            checkAll(arbCreatedThenDeletedEvents(objectMapper)) { events ->
                aggregatePostEvents(events, objectMapper).shouldBeNull()
            }
        }

        test("when aggregatePostEvents with list ending in unknown event after valid post_created then returns AggregatedPost") {
            checkAll(arbCreatedWithUnknownEvent(objectMapper)) { events ->
                val createdEvent = events.first { it.eventType == PostEventType.POST_CREATED.value }
                val result = aggregatePostEvents(events, objectMapper)

                result.shouldNotBeNull()
                val eventData = objectMapper.readValue(createdEvent.eventData, Map::class.java)
                val expectedUserId = UUID.fromString(eventData["userId"] as String)
                val expectedContent = eventData["content"] as String

                result.userId shouldBe expectedUserId
                result.content shouldBe expectedContent
                result.createdAt shouldBe createdEvent.occurredAt
            }
        }

        test(
            "when aggregatePostEvents with list ending in valid post_created after multiple post_created then returns AggregatedPost from last",
        ) {
            checkAll(arbMultipleCreatedEvents(objectMapper)) { events ->
                val lastEvent = events.last()
                val result = aggregatePostEvents(events, objectMapper)

                result.shouldNotBeNull()
                val eventData = objectMapper.readValue(lastEvent.eventData, Map::class.java)
                val expectedUserId = UUID.fromString(eventData["userId"] as String)
                val expectedContent = eventData["content"] as String

                result.userId shouldBe expectedUserId
                result.content shouldBe expectedContent
                result.createdAt shouldBe lastEvent.occurredAt
            }
        }

        test("when aggregatePostEvents with list ending in valid post_created after post_deleted then returns AggregatedPost") {
            checkAll(arbDeletedThenCreatedEvents(objectMapper)) { events ->
                val createdEvent = events.last()
                val result = aggregatePostEvents(events, objectMapper)

                result.shouldNotBeNull()
                val eventData = objectMapper.readValue(createdEvent.eventData, Map::class.java)
                val expectedUserId = UUID.fromString(eventData["userId"] as String)
                val expectedContent = eventData["content"] as String

                result.userId shouldBe expectedUserId
                result.content shouldBe expectedContent
                result.createdAt shouldBe createdEvent.occurredAt
            }
        }

        test(
            "when aggregatePostEvents with list ending in valid post_created after post_created and post_deleted then returns AggregatedPost from last",
        ) {
            checkAll(arbCreatedDeletedCreatedEvents(objectMapper)) { events ->
                val lastCreatedEvent = events.last()
                val result = aggregatePostEvents(events, objectMapper)

                result.shouldNotBeNull()
                val eventData = objectMapper.readValue(lastCreatedEvent.eventData, Map::class.java)
                val expectedUserId = UUID.fromString(eventData["userId"] as String)
                val expectedContent = eventData["content"] as String

                result.userId shouldBe expectedUserId
                result.content shouldBe expectedContent
                result.createdAt shouldBe lastCreatedEvent.occurredAt
            }
        }

        test("when aggregatePostEvents with random list ending in post_deleted then returns null") {
            checkAll(arbPostEventListEndingWithDeleted(objectMapper)) { events ->
                aggregatePostEvents(events, objectMapper).shouldBeNull()
            }
        }

        test("when aggregatePostEvents with random list ending in valid post_created then returns AggregatedPost") {
            checkAll(arbPostEventListEndingWithValidCreated(objectMapper)) { events ->
                val lastEvent = events.last()
                val result = aggregatePostEvents(events, objectMapper)

                result.shouldNotBeNull()
                val eventData = objectMapper.readValue(lastEvent.eventData, Map::class.java)
                val expectedUserId = UUID.fromString(eventData["userId"] as String)
                val expectedContent = eventData["content"] as String

                result.userId shouldBe expectedUserId
                result.content shouldBe expectedContent
                result.createdAt shouldBe lastEvent.occurredAt
            }
        }
    })

private fun arbValidPostCreatedEvent(objectMapper: ObjectMapper): Arb<PostEvent> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
    ) { eventId, postId, userId, content, occurredAt ->
        val eventData =
            mapOf(
                "userId" to userId.toString(),
                "content" to content,
            )
        val eventDataJson = objectMapper.writeValueAsString(eventData)
        PostEvent(
            eventId = eventId,
            postId = postId,
            eventType = PostEventType.POST_CREATED.value,
            eventData = eventDataJson,
            occurredAt = occurredAt,
        )
    }

private fun arbPostDeletedEvent(): Arb<PostEvent> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
    ) { eventId, postId, occurredAt ->
        PostEvent(
            eventId = eventId,
            postId = postId,
            eventType = PostEventType.POST_DELETED.value,
            eventData = "{}",
            occurredAt = occurredAt,
        )
    }

private fun arbCreatedThenDeletedEvents(objectMapper: ObjectMapper): Arb<List<PostEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
        arbInstant(),
    ) { postId, userId, content, createdTime, deletedTime ->
        val eventData =
            mapOf(
                "userId" to userId.toString(),
                "content" to content,
            )
        val eventDataJson = objectMapper.writeValueAsString(eventData)
        listOf(
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = eventDataJson,
                occurredAt = createdTime,
            ),
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_DELETED.value,
                eventData = "{}",
                occurredAt = deletedTime,
            ),
        )
    }

private fun arbCreatedWithUnknownEvent(objectMapper: ObjectMapper): Arb<List<PostEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        Arb.string(1..20).filter { it !in listOf("post_created", "post_deleted") },
        arbInstant(),
        arbInstant(),
    ) { postId, userId, content, unknownEventType, createdTime, unknownTime ->
        val eventData =
            mapOf(
                "userId" to userId.toString(),
                "content" to content,
            )
        val eventDataJson = objectMapper.writeValueAsString(eventData)
        listOf(
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = eventDataJson,
                occurredAt = createdTime,
            ),
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = unknownEventType,
                eventData = "{}",
                occurredAt = unknownTime,
            ),
        )
    }

private fun arbPostCreatedEventWithInvalidUserId(objectMapper: ObjectMapper): Arb<PostEvent> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..20).filter { runCatching { UUID.fromString(it) }.isFailure },
        Arb.string(1..100),
        arbInstant(),
    ) { eventId, postId, invalidUserId, content, occurredAt ->
        val eventData =
            mapOf(
                "userId" to invalidUserId,
                "content" to content,
            )
        val eventDataJson = objectMapper.writeValueAsString(eventData)
        PostEvent(
            eventId = eventId,
            postId = postId,
            eventType = PostEventType.POST_CREATED.value,
            eventData = eventDataJson,
            occurredAt = occurredAt,
        )
    }

private fun arbPostCreatedEventWithMissingUserId(objectMapper: ObjectMapper): Arb<PostEvent> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
    ) { eventId, postId, content, occurredAt ->
        val eventData =
            mapOf(
                "content" to content,
            )
        val eventDataJson = objectMapper.writeValueAsString(eventData)
        PostEvent(
            eventId = eventId,
            postId = postId,
            eventType = PostEventType.POST_CREATED.value,
            eventData = eventDataJson,
            occurredAt = occurredAt,
        )
    }

private fun arbPostCreatedEventWithMissingContent(objectMapper: ObjectMapper): Arb<PostEvent> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
    ) { eventId, postId, userId, occurredAt ->
        val eventData =
            mapOf(
                "userId" to userId.toString(),
            )
        val eventDataJson = objectMapper.writeValueAsString(eventData)
        PostEvent(
            eventId = eventId,
            postId = postId,
            eventType = PostEventType.POST_CREATED.value,
            eventData = eventDataJson,
            occurredAt = occurredAt,
        )
    }

private fun arbMultipleCreatedEvents(objectMapper: ObjectMapper): Arb<List<PostEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.list(
            Arb.bind(
                Arb.uuid(),
                Arb.string(1..100),
                arbInstant(),
            ) { userId, content, occurredAt ->
                Triple(userId, content, occurredAt)
            },
            2..5,
        ),
    ) { postId, eventDataList ->
        eventDataList.map { (userId, content, occurredAt) ->
            val eventData =
                mapOf(
                    "userId" to userId.toString(),
                    "content" to content,
                )
            val eventDataJson = objectMapper.writeValueAsString(eventData)
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = eventDataJson,
                occurredAt = occurredAt,
            )
        }
    }

private fun arbDeletedThenCreatedEvents(objectMapper: ObjectMapper): Arb<List<PostEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
        arbInstant(),
    ) { postId, userId, content, deletedTime, createdTime ->
        val eventData =
            mapOf(
                "userId" to userId.toString(),
                "content" to content,
            )
        val eventDataJson = objectMapper.writeValueAsString(eventData)
        listOf(
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_DELETED.value,
                eventData = "{}",
                occurredAt = deletedTime,
            ),
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = eventDataJson,
                occurredAt = createdTime,
            ),
        )
    }

private fun arbCreatedDeletedCreatedEvents(objectMapper: ObjectMapper): Arb<List<PostEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.string(1..100),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
        arbInstant(),
        arbInstant(),
    ) { postId, userId1, content1, userId2, content2, createdTime1, deletedTime, createdTime2 ->
        val eventData1 =
            mapOf(
                "userId" to userId1.toString(),
                "content" to content1,
            )
        val eventDataJson1 = objectMapper.writeValueAsString(eventData1)
        val eventData2 =
            mapOf(
                "userId" to userId2.toString(),
                "content" to content2,
            )
        val eventDataJson2 = objectMapper.writeValueAsString(eventData2)
        listOf(
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = eventDataJson1,
                occurredAt = createdTime1,
            ),
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_DELETED.value,
                eventData = "{}",
                occurredAt = deletedTime,
            ),
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = eventDataJson2,
                occurredAt = createdTime2,
            ),
        )
    }

private fun arbPostEventListEndingWithDeleted(objectMapper: ObjectMapper): Arb<List<PostEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.list(
            Arb.choice(
                arbValidPostCreatedEventForList(objectMapper),
                arbPostDeletedEventForList(),
            ),
            0..10,
        ),
        arbInstant(),
    ) { postId, eventGenerators, deletedTime ->
        eventGenerators.map { it(postId) } +
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_DELETED.value,
                eventData = "{}",
                occurredAt = deletedTime,
            )
    }

private fun arbPostEventListEndingWithValidCreated(objectMapper: ObjectMapper): Arb<List<PostEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.list(
            Arb.choice(
                arbValidPostCreatedEventForList(objectMapper),
                arbPostDeletedEventForList(),
            ),
            0..10,
        ),
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
    ) { postId, eventGenerators, userId, content, createdTime ->
        val eventData =
            mapOf(
                "userId" to userId.toString(),
                "content" to content,
            )
        val eventDataJson = objectMapper.writeValueAsString(eventData)
        eventGenerators.map { it(postId) } +
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = eventDataJson,
                occurredAt = createdTime,
            )
    }

private fun arbValidPostCreatedEventForList(objectMapper: ObjectMapper): Arb<(UUID) -> PostEvent> =
    Arb.bind(
        Arb.uuid(),
        Arb.string(1..100),
        arbInstant(),
    ) { userId, content, occurredAt ->
        { postId: UUID ->
            val eventData =
                mapOf(
                    "userId" to userId.toString(),
                    "content" to content,
                )
            val eventDataJson = objectMapper.writeValueAsString(eventData)
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = eventDataJson,
                occurredAt = occurredAt,
            )
        }
    }

private fun arbPostDeletedEventForList(): Arb<(UUID) -> PostEvent> =
    arbInstant().map { occurredAt: Instant ->
        { postId: UUID ->
            PostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                eventType = PostEventType.POST_DELETED.value,
                eventData = "{}",
                occurredAt = occurredAt,
            )
        }
    }

private fun arbInstant(): Arb<Instant> = Arb.long(0..253402300799L).map { Instant.ofEpochSecond(it) }
