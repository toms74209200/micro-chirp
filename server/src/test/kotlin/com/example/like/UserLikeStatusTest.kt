package com.example.like

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

class UserLikeStatusTest :
    FunSpec({
        test("when fromEvents with empty list then returns NotLiked") {
            checkAll(Arb.uuid()) { userId ->
                val result = UserLikeStatus.fromEvents(emptyList(), userId)
                result shouldBe UserLikeStatus.NotLiked
            }
        }

        test("when fromEvents with single LIKED event for user then returns Liked") {
            checkAll(arbLikeEvent(LikeEventType.LIKED)) { event ->
                val result = UserLikeStatus.fromEvents(listOf(event), event.userId)
                result shouldBe UserLikeStatus.Liked
            }
        }

        test("when fromEvents with single UNLIKED event for user then returns NotLiked") {
            checkAll(arbLikeEvent(LikeEventType.UNLIKED)) { event ->
                val result = UserLikeStatus.fromEvents(listOf(event), event.userId)
                result shouldBe UserLikeStatus.NotLiked
            }
        }

        test("when fromEvents with LIKED then UNLIKED events for user then returns NotLiked") {
            checkAll(arbLikedThenUnliked()) { (events, userId) ->
                val result = UserLikeStatus.fromEvents(events, userId)
                result shouldBe UserLikeStatus.NotLiked
            }
        }

        test("when fromEvents with UNLIKED then LIKED events for user then returns Liked") {
            checkAll(arbUnlikedThenLiked()) { (events, userId) ->
                val result = UserLikeStatus.fromEvents(events, userId)
                result shouldBe UserLikeStatus.Liked
            }
        }

        test("when fromEvents with events from different user then returns NotLiked") {
            checkAll(arbDifferentUserEvent()) { (event, differentUserId) ->
                val result = UserLikeStatus.fromEvents(listOf(event), differentUserId)
                result shouldBe UserLikeStatus.NotLiked
            }
        }

        test("when fromEvents with mixed users last event is LIKED for target user then returns Liked") {
            checkAll(arbMixedUsersLastLiked()) { (events, targetUserId) ->
                val result = UserLikeStatus.fromEvents(events, targetUserId)
                result shouldBe UserLikeStatus.Liked
            }
        }

        test("when fromEvents with mixed users last event is UNLIKED for target user then returns NotLiked") {
            checkAll(arbMixedUsersLastUnliked()) { (events, targetUserId) ->
                val result = UserLikeStatus.fromEvents(events, targetUserId)
                result shouldBe UserLikeStatus.NotLiked
            }
        }
    })

private fun arbLikeEvent(eventType: LikeEventType): Arb<LikeEvent> =
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
            eventType = eventType.value,
            occurredAt = occurredAt,
        )
    }

private fun arbDifferentUserEvent(): Arb<Pair<LikeEvent, UUID>> =
    Arb
        .bind(
            Arb.uuid(),
            Arb.uuid(),
            arbInstant(),
        ) { eventId, postId, occurredAt ->
            val userId1 = UUID.randomUUID()
            val userId2 =
                generateSequence { UUID.randomUUID() }
                    .first { it != userId1 }
            val event =
                LikeEvent(
                    eventId = eventId,
                    postId = postId,
                    userId = userId1,
                    eventType = LikeEventType.LIKED.value,
                    occurredAt = occurredAt,
                )
            event to userId2
        }

private fun arbLikedThenUnliked(): Arb<Pair<List<LikeEvent>, UUID>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
        arbInstant(),
    ) { postId, userId, time1, time2 ->
        val events =
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
            )
        events to userId
    }

private fun arbUnlikedThenLiked(): Arb<Pair<List<LikeEvent>, UUID>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
        arbInstant(),
    ) { postId, userId, time1, time2 ->
        val events =
            listOf(
                LikeEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    userId = userId,
                    eventType = LikeEventType.UNLIKED.value,
                    occurredAt = time1,
                ),
                LikeEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    userId = userId,
                    eventType = LikeEventType.LIKED.value,
                    occurredAt = time2,
                ),
            )
        events to userId
    }

private fun arbMixedUsersLastLiked(): Arb<Pair<List<LikeEvent>, UUID>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
        arbInstant(),
    ) { postId, targetUserId, otherUserId, time1, time2 ->
        val events =
            listOf(
                LikeEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    userId = otherUserId,
                    eventType = LikeEventType.LIKED.value,
                    occurredAt = time1,
                ),
                LikeEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    userId = targetUserId,
                    eventType = LikeEventType.LIKED.value,
                    occurredAt = time2,
                ),
            )
        events to targetUserId
    }

private fun arbMixedUsersLastUnliked(): Arb<Pair<List<LikeEvent>, UUID>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
        arbInstant(),
        arbInstant(),
    ) { postId, targetUserId, otherUserId, time1, time2, time3 ->
        val events =
            listOf(
                LikeEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    userId = targetUserId,
                    eventType = LikeEventType.LIKED.value,
                    occurredAt = time1,
                ),
                LikeEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    userId = otherUserId,
                    eventType = LikeEventType.LIKED.value,
                    occurredAt = time2,
                ),
                LikeEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    userId = targetUserId,
                    eventType = LikeEventType.UNLIKED.value,
                    occurredAt = time3,
                ),
            )
        events to targetUserId
    }

private fun arbInstant(): Arb<Instant> = Arb.long(0..253402300799L).map { Instant.ofEpochSecond(it) }
