package com.example.post

import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

enum class PostEventType(
    val value: String,
) {
    POST_CREATED("post_created"),
    POST_DELETED("post_deleted"),
    ;

    companion object {
        fun fromString(value: String): PostEventType? = entries.find { it.value == value }
    }
}

data class AggregatedPost(
    val userId: UUID,
    val content: String,
    val createdAt: Instant,
)

fun aggregatePostEvents(
    events: List<PostEvent>,
    objectMapper: ObjectMapper,
): AggregatedPost? {
    val applyPostCreated: (AggregatedPost?, PostEvent) -> AggregatedPost? = { currentState, event ->
        (
            try {
                objectMapper.readValue(event.eventData, Map::class.java) as? Map<*, *>
            } catch (e: Exception) {
                null
            }
        )?.takeIf { it["userId"] is String }
            ?.takeIf { it["content"] is String }
            ?.let { data ->
                (
                    try {
                        UUID.fromString(data["userId"] as String)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                )?.let { userId ->
                    AggregatedPost(
                        userId = userId,
                        content = data["content"] as String,
                        createdAt = event.occurredAt,
                    )
                }
            }
            ?: currentState
    }

    val applyPostDeleted: (AggregatedPost?) -> AggregatedPost? = { null }

    return events.fold(null as AggregatedPost?) { acc, event ->
        when (PostEventType.fromString(event.eventType)) {
            PostEventType.POST_CREATED -> applyPostCreated(acc, event)
            PostEventType.POST_DELETED -> applyPostDeleted(acc)
            null -> acc
        }
    }
}
