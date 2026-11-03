package com.itemtracker.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private const val SHORT_LINK = "https://a.co/d/0abcDEF"

/** Tests for [Amazon] */
internal class AmazonTest {

    @Test
    fun parseShareTest() {
        val shared = Amazon.parseShare("Check out this item on Amazon: Brand Coffee Pods, 72 Count $SHORT_LINK", null)

        assertThat(shared).isEqualTo(Amazon.Shared(SHORT_LINK, "Brand Coffee Pods, 72 Count"))
    }

    @Test
    fun parseShareTestSubjectWins() {
        val shared = Amazon.parseShare(SHORT_LINK, "Brand Soap")

        assertThat(shared).isEqualTo(Amazon.Shared(SHORT_LINK, "Brand Soap"))
    }

    @Test
    fun parseShareTestOnlyText() {
        assertThat(Amazon.parseShare("  ", null)).isEqualTo(Amazon.Shared(null, null))
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        delimiter = '|',
        textBlock = """
            Brand Thing, 12 Count - Extra Words | Brand Thing
            Paper Towels (Pack of 6)            | Paper Towels
            Ab, cd                              | Ab, cd
            A very long product title that keeps on going forever | A very long product title that keeps on
        """,
    )
    fun shortNameTest(title: String, expected: String) {
        assertThat(Amazon.shortName(title)).isEqualTo(expected)
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "https://www.amazon.com/dp/B000, true",
        "https://amzn.to/abc, true",
        "$SHORT_LINK, true",
        "https://example.com/item, false",
    )
    fun looksLikeAmazonTest(url: String, expected: Boolean) {
        assertThat(Amazon.looksLikeAmazon(url)).isEqualTo(expected)
    }

    @Test
    fun looksLikeAmazonTestNull() {
        assertThat(Amazon.looksLikeAmazon(null)).isFalse()
    }
}
