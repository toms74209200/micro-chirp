package com.example.repost

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class RepostEventTypeTest :
    FunSpec({
        test("when fromString with 'reposted' then returns REPOSTED") {
            val result = RepostEventType.fromString("reposted")
            result shouldBe RepostEventType.REPOSTED
        }

        test("when fromString with 'unreposted' then returns UNREPOSTED") {
            val result = RepostEventType.fromString("unreposted")
            result shouldBe RepostEventType.UNREPOSTED
        }

        test("when fromString with arbitrary strings then returns null except exact match") {
            checkAll(Arb.string()) { value ->
                val result = RepostEventType.fromString(value)
                when (value) {
                    "reposted" -> result shouldBe RepostEventType.REPOSTED
                    "unreposted" -> result shouldBe RepostEventType.UNREPOSTED
                    else -> result shouldBe null
                }
            }
        }

        test("when fromString with strings containing 'reposted' as substring then returns null") {
            checkAll(Arb.string(), Arb.string()) { prefix, suffix ->
                val value = prefix + "reposted" + suffix
                val result = RepostEventType.fromString(value)
                when (value) {
                    "reposted" -> result shouldBe RepostEventType.REPOSTED
                    "unreposted" -> result shouldBe RepostEventType.UNREPOSTED
                    else -> result shouldBe null
                }
            }
        }
    })
