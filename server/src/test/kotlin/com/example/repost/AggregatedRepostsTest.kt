package com.example.repost

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

class AggregatedRepostsTest :
    FunSpec({
        test("when aggregateRepostEvents with empty list then returns zero reposts") {
            checkAll(Arb.constant(emptyList<RepostEvent>())) { events ->
                val result = aggregateRepostEvents(events)
                result.repostCount shouldBe 0
                result.repostedUserIds shouldBe emptySet()
            }
        }

        test("when aggregateRepostEvents with single REPOSTED event then returns 1 repost") {
            checkAll(arbRepostedEvent()) { event ->
                val result = aggregateRepostEvents(listOf(event))
                result.repostCount shouldBe 1
                result.repostedUserIds shouldBe setOf(event.userId)
            }
        }

        test("when aggregateRepostEvents with multiple users each REPOSTED then returns count") {
            checkAll(arbMultipleUsersRepostedEvents(2..5)) { events ->
                val uniqueUserIds = events.map { it.userId }.toSet()
                val result = aggregateRepostEvents(events)
                result.repostCount shouldBe uniqueUserIds.size
                result.repostedUserIds shouldBe uniqueUserIds
            }
        }

        test("when aggregateRepostEvents with same user multiple REPOSTED events then returns 1 repost") {
            checkAll(arbSameUserMultipleReposts()) { events ->
                val userId = events.first().userId
                val result = aggregateRepostEvents(events)
                result.repostCount shouldBe 1
                result.repostedUserIds shouldBe setOf(userId)
            }
        }

        test("when aggregateRepostEvents with REPOSTED then UNREPOSTED then returns 0 reposts") {
            checkAll(arbRepostThenUnrepostEvents()) { events ->
                val result = aggregateRepostEvents(events)
                result.repostCount shouldBe 0
                result.repostedUserIds shouldBe emptySet()
            }
        }

        test("when aggregateRepostEvents with multiple users one UNREPOSTED then returns correct count") {
            checkAll(arbMultipleUsersOneUnreposted()) { events ->
                val unrepostedUserId = events.find { it.eventType == RepostEventType.UNREPOSTED.value }?.userId
                val expectedUserIds =
                    events
                        .filter { it.eventType == RepostEventType.REPOSTED.value }
                        .map { it.userId }
                        .toSet() - setOfNotNull(unrepostedUserId)
                val result = aggregateRepostEvents(events)
                result.repostCount shouldBe expectedUserIds.size
                result.repostedUserIds shouldBe expectedUserIds
            }
        }
    })

private fun arbRepostedEvent(): Arb<RepostEvent> =
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
            eventType = RepostEventType.REPOSTED.value,
            occurredAt = occurredAt,
        )
    }

private fun arbMultipleUsersRepostedEvents(userCountRange: IntRange): Arb<List<RepostEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.list(Arb.uuid(), userCountRange),
    ) { postId, userIds ->
        userIds.mapIndexed { index, userId ->
            RepostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = RepostEventType.REPOSTED.value,
                occurredAt = Instant.ofEpochSecond(index.toLong()),
            )
        }
    }

private fun arbSameUserMultipleReposts(): Arb<List<RepostEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        arbInstant(),
        arbInstant(),
    ) { postId, userId, time1, time2 ->
        listOf(
            RepostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = RepostEventType.REPOSTED.value,
                occurredAt = time1,
            ),
            RepostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = RepostEventType.REPOSTED.value,
                occurredAt = time2,
            ),
        )
    }

private fun arbRepostThenUnrepostEvents(): Arb<List<RepostEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
    ) { postId, userId ->
        listOf(
            RepostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = RepostEventType.REPOSTED.value,
                occurredAt = Instant.ofEpochSecond(0),
            ),
            RepostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = RepostEventType.UNREPOSTED.value,
                occurredAt = Instant.ofEpochSecond(1),
            ),
        )
    }

private fun arbMultipleUsersOneUnreposted(): Arb<List<RepostEvent>> =
    Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid(),
    ) { postId, userId1, userId2 ->
        listOf(
            RepostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId1,
                eventType = RepostEventType.REPOSTED.value,
                occurredAt = Instant.ofEpochSecond(0),
            ),
            RepostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId2,
                eventType = RepostEventType.REPOSTED.value,
                occurredAt = Instant.ofEpochSecond(1),
            ),
            RepostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId1,
                eventType = RepostEventType.UNREPOSTED.value,
                occurredAt = Instant.ofEpochSecond(2),
            ),
        )
    }

private fun arbInstant(): Arb<Instant> = Arb.long(0..253402300799L).map { Instant.ofEpochSecond(it) }
