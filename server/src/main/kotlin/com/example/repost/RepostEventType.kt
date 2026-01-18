package com.example.repost

enum class RepostEventType(
    val value: String,
) {
    REPOSTED("reposted"),
    UNREPOSTED("unreposted"),
    ;

    companion object {
        fun fromString(value: String): RepostEventType? = entries.find { it.value == value }
    }
}
