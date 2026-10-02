package com.buildorbreak.core.domain.track

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * A pasted syllabus, read back as parts.
 *
 * The parser is deliberately dumb, and these pin down the few things it
 * decides: what counts as a part, what counts as a heading, and where a
 * time on the end of a line goes.
 */
class TrackTextParserTest {

    private val parser = TrackTextParser()

    @Test
    fun `one part per line, in the order given`() {
        val parts = parser.parse("HTTP basics\nREST and JSON\nAuthentication")

        assertThat(parts.map { it.title }).containsExactly("HTTP basics", "REST and JSON", "Authentication").inOrder()
    }

    @Test
    fun `numbering and bullets are not part of the title`() {
        val parts = parser.parse("1. HTTP basics\n2) REST\n- Auth\n• Deploy\n* Done")

        assertThat(parts.map { it.title }).containsExactly("HTTP basics", "REST", "Auth", "Deploy", "Done").inOrder()
    }

    @Test
    fun `blank lines are nothing`() {
        val parts = parser.parse("\n\nHTTP basics\n   \nREST\n")

        assertThat(parts).hasSize(2)
    }

    @Test
    fun `a line ending in a colon is a heading, not a part`() {
        val parts = parser.parse("Week 1:\nHTTP basics\nWeek 2:\nREST")

        assertThat(parts.map { it.title }).containsExactly("HTTP basics", "REST").inOrder()
    }

    @Test
    fun `a time on the end becomes the estimate`() {
        val parts = parser.parse("HTTP basics · 30 min\nREST (45 min)\nAuth - 1 h\nDeploy 20m")

        assertThat(parts.map { it.title }).containsExactly("HTTP basics", "REST", "Auth", "Deploy").inOrder()
        assertThat(parts.map { it.estimateMinutes }).containsExactly(30, 45, 60, 20).inOrder()
    }

    @Test
    fun `a time in the middle of a line stays in the title`() {
        val parts = parser.parse("Read 2 hours of Kafka")

        assertThat(parts.single().title).isEqualTo("Read 2 hours of Kafka")
        assertThat(parts.single().estimateMinutes).isNull()
    }

    @Test
    fun `a day label is part of the title, because it tells the parts apart`() {
        val parts = parser.parse("Day 3: HTTP basics")

        assertThat(parts.single().title).isEqualTo("Day 3: HTTP basics")
    }

    @Test
    fun `nothing readable is an empty list, never a throw`() {
        assertThat(parser.parse("")).isEmpty()
        assertThat(parser.parse("Heading:\n\n- \n")).isEmpty()
    }
}
