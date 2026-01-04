package com.example.post

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class ValidatedPostContentTest :
    FunSpec({
        test("when parsePostContent with blank string then returns null") {
            checkAll(Arb.string().filter { it.isBlank() }) { blankString ->
                parsePostContent(blankString).shouldBeNull()
            }
        }

        test("when parsePostContent with valid content then returns ValidatedPostContent") {
            checkAll(arbValidPostContent()) { validContent ->
                val result = parsePostContent(validContent)
                result.shouldNotBeNull()
                result.value shouldBe validContent
            }
        }

        test("when parsePostContent with content exceeding 280 graphemes then returns null") {
            checkAll(arbInvalidPostContent()) { invalidContent ->
                parsePostContent(invalidContent).shouldBeNull()
            }
        }
    })

private fun arbValidPostContent(): Arb<String> = Arb.string(minSize = 1, maxSize = 280).filter { it.isNotBlank() }

private fun arbInvalidPostContent(): Arb<String> = Arb.string(minSize = 281, maxSize = 500)
