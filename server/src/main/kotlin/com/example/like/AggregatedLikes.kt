package com.example.like

import java.util.UUID

data class AggregatedLikes(
    val likeCount: Int,
    val likedUserIds: Set<UUID>,
)

fun aggregateLikeEvents(events: List<LikeEvent>): AggregatedLikes {
    val applyLiked: (Map<UUID, Boolean>, LikeEvent) -> Map<UUID, Boolean> = { currentState, event ->
        currentState + (event.userId to true)
    }

    val applyUnliked: (Map<UUID, Boolean>, LikeEvent) -> Map<UUID, Boolean> = { currentState, event ->
        currentState + (event.userId to false)
    }

    val userLikeStatus =
        events.fold(emptyMap<UUID, Boolean>()) { acc, event ->
            when (LikeEventType.fromString(event.eventType)) {
                LikeEventType.LIKED -> applyLiked(acc, event)
                LikeEventType.UNLIKED -> applyUnliked(acc, event)
                null -> acc
            }
        }

    val likedUserIds = userLikeStatus.filter { it.value }.keys
    val likeCount = likedUserIds.size

    return AggregatedLikes(likeCount, likedUserIds)
}
