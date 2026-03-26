package com.example.timeline

import com.example.post.PostEvent
import com.example.post.PostEventType
import com.example.post.aggregatePostEvents
import tools.jackson.databind.ObjectMapper
import java.util.UUID

data class TimelineDelta(
    val activePosts: List<TimelinePostRow>,
    val deletedIds: Set<UUID>,
    val deletedFromMvCount: Int,
)

fun buildTimelineDelta(
    deltaByPostId: Map<UUID, List<PostEvent>>,
    userFilter: (UUID) -> Boolean,
    objectMapper: ObjectMapper,
): TimelineDelta {
    val activePosts =
        deltaByPostId
            .mapNotNull { (postId, events) ->
                val agg = aggregatePostEvents(events, objectMapper) ?: return@mapNotNull null
                if (!userFilter(agg.userId)) return@mapNotNull null
                TimelinePostRow(postId, agg.userId, agg.content, agg.createdAt)
            }.sortedByDescending { it.createdAt }

    val deletedIds =
        deltaByPostId
            .filter { (_, events) -> events.any { it.eventType == PostEventType.POST_DELETED.value } }
            .keys
            .toSet()

    val deletedFromMvCount =
        deltaByPostId
            .filter { (_, events) ->
                val hasDelete = events.any { it.eventType == PostEventType.POST_DELETED.value }
                val hasCreate = events.any { it.eventType == PostEventType.POST_CREATED.value }
                hasDelete && !hasCreate
            }.count { (_, events) ->
                val deleteEvent =
                    events.firstOrNull { it.eventType == PostEventType.POST_DELETED.value }
                        ?: return@count false
                try {
                    val data =
                        objectMapper.readValue(deleteEvent.eventData, Map::class.java) as? Map<*, *>
                            ?: return@count false
                    val userIdStr = data["userId"] as? String ?: return@count false
                    val userId = UUID.fromString(userIdStr)
                    userFilter(userId)
                } catch (e: Exception) {
                    false
                }
            }

    return TimelineDelta(activePosts, deletedIds, deletedFromMvCount)
}
