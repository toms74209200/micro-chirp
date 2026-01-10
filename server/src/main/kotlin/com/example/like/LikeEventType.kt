package com.example.like

enum class LikeEventType(
    val value: String,
) {
    LIKED("liked"),
    UNLIKED("unliked"),
    ;

    companion object {
        fun fromString(value: String): LikeEventType? = entries.find { it.value == value }
    }
}
