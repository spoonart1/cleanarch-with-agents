package io.github.spoonart1.cleanarchwithagent.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * These tests follow the naming and Given/When/Then structure that the rule
 * under test enforces, so the file doubles as the worked example of the
 * convention.
 */
class TestFunctionNamingTest {

    @Test
    fun `test visitNamedFunction when name follows the convention should report nothing`() {
        // Given
        val rule = TestFunctionNaming(Config.empty)
        val code = """
            class Subject {
                @Test
                fun `test getName when success should return true`() { }
            }
        """.trimIndent()

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(0, findings.size)
    }

    @Test
    fun `test visitNamedFunction when name omits the when clause should report a finding`() {
        // Given
        val rule = TestFunctionNaming(Config.empty)
        val code = """
            class Subject {
                @Test
                fun `test getName should return true`() { }
            }
        """.trimIndent()

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(1, findings.size)
    }

    @Test
    fun `test visitNamedFunction when name omits the should clause should report a finding`() {
        // Given
        val rule = TestFunctionNaming(Config.empty)
        val code = """
            class Subject {
                @Test
                fun `test getName when success`() { }
            }
        """.trimIndent()

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(1, findings.size)
    }

    @Test
    fun `test visitNamedFunction when function is not annotated should report nothing`() {
        // Given a helper with a name that would fail the pattern, but no @Test
        val rule = TestFunctionNaming(Config.empty)
        val code = """
            class Subject {
                fun buildFixture() { }
            }
        """.trimIndent()

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(0, findings.size)
    }

    @Test
    fun `test visitNamedFunction when clauses are empty should report a finding`() {
        // Given a name that contains both keywords but no actual clauses
        val rule = TestFunctionNaming(Config.empty)
        val code = """
            class Subject {
                @Test
                fun `test when should`() { }
            }
        """.trimIndent()

        // When
        val findings = rule.compileAndLint(code)

        // Then
        assertEquals(1, findings.size)
    }
}
