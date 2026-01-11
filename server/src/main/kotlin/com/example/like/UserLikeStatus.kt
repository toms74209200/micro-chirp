package com.example.like

import java.util.UUID

sealed interface UserLikeStatus {
    data object Liked : UserLikeStatus

    data object NotLiked : UserLikeStatus

    companion object {
        fun fromEvents(
            likeEvents: List<LikeEvent>,
            userId: UUID,
        ): UserLikeStatus {
            val userLikeEvents = likeEvents.filter { it.userId == userId }
            val lastEventType =
                userLikeEvents.lastOrNull()?.let {
                    LikeEventType.fromString(it.eventType)
                }

            return when (lastEventType) {
                LikeEventType.LIKED -> Liked
                else -> NotLiked
            }
        }
    }
}
