package com.example.repost

import java.util.UUID

sealed interface UserRepostStatus {
    data object Reposted : UserRepostStatus

    data object NotReposted : UserRepostStatus

    companion object {
        fun fromEvents(
            repostEvents: List<RepostEvent>,
            userId: UUID,
        ): UserRepostStatus {
            val userRepostEvents = repostEvents.filter { it.userId == userId }
            val lastEventType =
                userRepostEvents.lastOrNull()?.let {
                    RepostEventType.fromString(it.eventType)
                }

            return when (lastEventType) {
                RepostEventType.REPOSTED -> Reposted
                else -> NotReposted
            }
        }
    }
}
