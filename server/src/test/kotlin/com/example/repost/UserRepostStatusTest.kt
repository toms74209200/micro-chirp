package com.example.repost

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

class UserRepostStatusTest :
    FunSpec({
        test("when fromEvents with empty list then returns NotReposted") {
            checkAll(Arb.uuid()) { userId ->
                val result = UserRepostStatus.fromEvents(emptyList(), userId)
                result shouldBe UserRepostStatus.NotReposted
            }
        }

        test("when fromEvents with single REPOSTED event for user then returns Reposted") {
            checkAll(arbRepostEvent(RepostEventType.REPOSTED)) { event ->
                val result = UserRepostStatus.fromEvents(listOf(event), event.userId)
                result shouldBe UserRepostStatus.Reposted
            }
        }

        test("when fromEvents with events from different user then returns NotReposted") {
            checkAll(arbDifferentUserEvent()) { (event, differentUserId) ->
                val result = UserRepostStatus.fromEvents(listOf(event), differentUserId)
                result shouldBe UserRepostStatus.NotReposted
            }
        }
    })

private fun arbRepostEvent(eventType: RepostEventType): Arb<RepostEvent> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
    ) { eventId, postId, userId, occurredAt ->
        RepostEvent(
            eventId = eventId,
            postId = postId,
            userId = userId,
            eventType = eventType.value,
            occurredAt = occurredAt,
        )
    }

private fun arbDifferentUserEvent(): Arb<Pair<RepostEvent, UUID>> =
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
                RepostEvent(
                    eventId = eventId,
                    postId = postId,
                    userId = userId1,
                    eventType = RepostEventType.REPOSTED.value,
                    occurredAt = occurredAt,
                )
            event to userId2
        }

private fun arbInstant(): Arb<Instant> = Arb.long(0..253402300799L).map { Instant.ofEpochSecond(it) }
