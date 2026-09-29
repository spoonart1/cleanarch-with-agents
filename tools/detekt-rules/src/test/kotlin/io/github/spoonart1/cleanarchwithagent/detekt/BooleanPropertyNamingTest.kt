package io.github.spoonart1.cleanarchwithagent.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.Assert.assertEquals
import org.junit.Test

class BooleanPropertyNamingTest {

    @Test
    fun `test visitProperty when boolean starts with is should report nothing`() {
        // Given
        val rule = BooleanPropertyNaming(Config.empty)
        val code = "class Subject { val isLoading: Boolean = false }"

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(0, findings.size)
    }

    @Test
    fun `test visitProperty when boolean is a bare noun should report a finding`() {
        // Given
        val rule = BooleanPropertyNaming(Config.empty)
        val code = "class Subject { val loading: Boolean = false }"

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(1, findings.size)
    }

    @Test
    fun `test visitProperty when prefix is not followed by a capital should report a finding`() {
        // Given a name that merely begins with the letters "is"
        val rule = BooleanPropertyNaming(Config.empty)
        val code = "class Subject { val island: Boolean = false }"

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(1, findings.size)
    }

    @Test
    fun `test visitProperty when type is not boolean should report nothing`() {
        // Given
        val rule = BooleanPropertyNaming(Config.empty)
        val code = "class Subject { val loading: String = \"\" }"

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(0, findings.size)
    }

    @Test
    fun `test visitParameter when constructor val is a bare noun should report a finding`() {
        // Given
        val rule = BooleanPropertyNaming(Config.empty)
        val code = "class Subject(val done: Boolean)"

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(1, findings.size)
    }

    @Test
    fun `test visitParameter when plain function parameter is a bare noun should report nothing`() {
        // Given a parameter without val or var, which the rule deliberately skips
        val rule = BooleanPropertyNaming(Config.empty)
        val code = "fun render(enabled: Boolean) = enabled"

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(0, findings.size)
    }

    @Test
    fun `test visitProperty when alternative prefix has is used should report nothing`() {
        // Given
        val rule = BooleanPropertyNaming(Config.empty)
        val code = "class Subject { val hasItems: Boolean = false }"

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(0, findings.size)
    }
}
