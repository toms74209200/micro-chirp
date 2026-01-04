package com.example.post

import java.text.BreakIterator
import java.util.Locale

data class ValidatedPostContent(
    val value: String,
)

fun parsePostContent(rawContent: String): ValidatedPostContent? =
    rawContent
        .takeIf { it.isNotBlank() }
        ?.let { content ->
            BreakIterator
                .getCharacterInstance(Locale.US)
                .apply { setText(content) }
                .let { iterator ->
                    var count = 0
                    while (iterator.next() != BreakIterator.DONE) {
                        count++
                    }
                    count
                }.takeIf { it <= 280 }
                ?.let { content }
        }?.let { ValidatedPostContent(it) }
