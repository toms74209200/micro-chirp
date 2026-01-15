package com.example.repost

import java.util.UUID

data class AggregatedReposts(
    val repostCount: Int,
    val repostedUserIds: Set<UUID>,
)

fun aggregateRepostEvents(events: List<RepostEvent>): AggregatedReposts {
    val applyReposted: (Map<UUID, Boolean>, RepostEvent) -> Map<UUID, Boolean> = { currentState, event ->
        currentState + (event.userId to true)
    }

    val userRepostStatus =
        events.fold(emptyMap<UUID, Boolean>()) { acc, event ->
            when (RepostEventType.fromString(event.eventType)) {
                RepostEventType.REPOSTED -> applyReposted(acc, event)
                null -> acc
            }
        }

    val repostedUserIds = userRepostStatus.filter { it.value }.keys
    val repostCount = repostedUserIds.size

    return AggregatedReposts(repostCount, repostedUserIds)
}
