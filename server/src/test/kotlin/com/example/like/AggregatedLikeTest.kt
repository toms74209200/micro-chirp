package com.example.like

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

class AggregatedLikeTest :
    FunSpec({
        test("when aggregateLikeEvents with empty list then returns zero likes") {
            checkAll(Arb.constant(emptyList<LikeEvent>())) { events ->
                val result = aggregateLikeEvents(events)
                result.likeCount shouldBe 0
                result.likedUserIds shouldBe emptySet()
            }
        }

        test("when aggregateLikeEvents with single LIKED event then returns 1 like") {
            checkAll(arbLikedEvent()) { event ->
                val result = aggregateLikeEvents(listOf(event))
                result.likeCount shouldBe 1
                result.likedUserIds shouldBe setOf(event.userId)
            }
        }

        test("when aggregateLikeEvents with single UNLIKED event then returns 0 likes") {
            checkAll(arbUnlikedEvent()) { event ->
                val result = aggregateLikeEvents(listOf(event))
                result.likeCount shouldBe 0
                result.likedUserIds shouldBe emptySet()
            }
        }

        test("when aggregateLikeEvents with LIKED then UNLIKED events then returns 0 likes") {
            checkAll(arbLikedThenUnlikedEvents()) { events ->
                val result = aggregateLikeEvents(events)
                result.likeCount shouldBe 0
                result.likedUserIds shouldBe emptySet()
            }
        }

        test("when aggregateLikeEvents with LIKED then UNLIKED then LIKED events then returns 1 like") {
            checkAll(arbLikedUnlikedLikedEvents()) { events ->
                val userId = events.first().userId
                val result = aggregateLikeEvents(events)
                result.likeCount shouldBe 1
                result.likedUserIds shouldBe setOf(userId)
            }
        }

        test("when aggregateLikeEvents with multiple users each LIKED then returns count") {
            checkAll(arbMultipleUsersLikedEvents(2..5)) { events ->
                val uniqueUserIds = events.map { it.userId }.toSet()
                val result = aggregateLikeEvents(events)
                result.likeCount shouldBe uniqueUserIds.size
                result.likedUserIds shouldBe uniqueUserIds
            }
        }

        test("when aggregateLikeEvents with multiple users mixed events then returns correct count") {
            checkAll(arbMultipleUsersMixedEvents()) { (events, expectedLikedCount) ->
                val result = aggregateLikeEvents(events)
                result.likeCount shouldBe expectedLikedCount
            }
        }
    })

private fun arbLikedEvent(): Arb<LikeEvent> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
    ) { eventId, postId, userId, occurredAt ->
        LikeEvent(
            eventId = eventId,
            postId = postId,
            userId = userId,
            eventType = LikeEventType.LIKED.value,
            occurredAt = occurredAt,
        )
    }

private fun arbUnlikedEvent(): Arb<LikeEvent> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
    ) { eventId, postId, userId, occurredAt ->
        LikeEvent(
            eventId = eventId,
            postId = postId,
            userId = userId,
            eventType = LikeEventType.UNLIKED.value,
            occurredAt = occurredAt,
        )
    }

private fun arbLikedThenUnlikedEvents(): Arb<List<LikeEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
        arbInstant(),
    ) { postId, userId, likedTime, unlikedTime ->
        listOf(
            LikeEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = LikeEventType.LIKED.value,
                occurredAt = likedTime,
            ),
            LikeEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = LikeEventType.UNLIKED.value,
                occurredAt = unlikedTime,
            ),
        )
    }

private fun arbLikedUnlikedLikedEvents(): Arb<List<LikeEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
        arbInstant(),
        arbInstant(),
    ) { postId, userId, time1, time2, time3 ->
        listOf(
            LikeEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = LikeEventType.LIKED.value,
                occurredAt = time1,
            ),
            LikeEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = LikeEventType.UNLIKED.value,
                occurredAt = time2,
            ),
            LikeEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = LikeEventType.LIKED.value,
                occurredAt = time3,
            ),
        )
    }

private fun arbMultipleUsersLikedEvents(userCountRange: IntRange): Arb<List<LikeEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.list(Arb.uuid(), userCountRange),
    ) { postId, userIds ->
        userIds.mapIndexed { index, userId ->
            LikeEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = LikeEventType.LIKED.value,
                occurredAt = Instant.ofEpochSecond(index.toLong()),
            )
        }
    }

private fun arbMultipleUsersMixedEvents(): Arb<Pair<List<LikeEvent>, Int>> =
    Arb.bind(
        Arb.uuid(),
        Arb.list(Arb.uuid(), 3..5).map { it.distinct() },
    ) { postId, userIds ->
        val events = mutableListOf<LikeEvent>()
        var timestamp = 0L
        var likedCount = 0

        userIds.forEachIndexed { index, userId ->
            val shouldEndWithLiked = index % 2 == 0
            if (shouldEndWithLiked) {
                events.add(
                    LikeEvent(
                        eventId = UUID.randomUUID(),
                        postId = postId,
                        userId = userId,
                        eventType = LikeEventType.LIKED.value,
                        occurredAt = Instant.ofEpochSecond(timestamp++),
                    ),
                )
                likedCount++
            } else {
                events.add(
                    LikeEvent(
                        eventId = UUID.randomUUID(),
                        postId = postId,
                        userId = userId,
                        eventType = LikeEventType.LIKED.value,
                        occurredAt = Instant.ofEpochSecond(timestamp++),
                    ),
                )
                events.add(
                    LikeEvent(
                        eventId = UUID.randomUUID(),
                        postId = postId,
                        userId = userId,
                        eventType = LikeEventType.UNLIKED.value,
                        occurredAt = Instant.ofEpochSecond(timestamp++),
                    ),
                )
            }
        }

        events to likedCount
    }

private fun arbInstant(): Arb<Instant> = Arb.long(0..253402300799L).map { Instant.ofEpochSecond(it) }
