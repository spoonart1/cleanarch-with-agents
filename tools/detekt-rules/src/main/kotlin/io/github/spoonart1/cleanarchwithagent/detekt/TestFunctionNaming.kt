package io.github.spoonart1.cleanarchwithagent.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Requires every `@Test` function to be named
 * `test <function> when <clause> should <result>`, written as a backticked
 * name. For example:
 *
 * ```
 * @Test
 * fun `test getName when success should return true`() { }
 * ```
 *
 * The point is that a failing test in CI output names the subject, the
 * condition and the expectation without anyone opening the file.
 *
 * The rule matches on the annotation's short name (`Test`) rather than its
 * resolved type, so it works without type resolution — see the note in
 * [BooleanPropertyNaming] on why this project avoids depending on the solver.
 */
class TestFunctionNaming(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "TestFunctionNaming",
        severity = Severity.Style,
        description = "Test function names should read as " +
            "'test <function> when <clause> should <result>' so a CI failure " +
            "states what broke without anyone opening the file.",
        debt = Debt.FIVE_MINS,
    )

    /**
     * Annotation short names that mark a test. JUnit 4's `@Test` is the one
     * this project uses; the others are here so the rule keeps working if a
     * module adopts JUnit 5 or a parameterised runner.
     */
    private val testAnnotations: List<String> by config(
        listOf("Test", "ParameterizedTest", "RepeatedTest"),
    )

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!hasTestAnnotation(function)) return

        val name = function.name ?: return
        if (NAME_PATTERN.matches(name)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(function),
                message = "Test name '$name' should read " +
                    "'test <function> when <clause> should <result>', " +
                    "for example `test getName when success should return true`.",
            ),
        )
    }

    private fun hasTestAnnotation(function: KtNamedFunction): Boolean =
        function.annotationEntries.any { annotation ->
            annotation.shortName?.asString() in testAnnotations
        }

    private companion object {
        /**
         * `test <subject> when <clause> should <result>`. Each of the three
         * segments must be non-empty, which is what stops a name like
         * `test when should` from passing. The separators are matched as whole
         * words so a subject containing "when" inside an identifier is fine.
         */
        val NAME_PATTERN = Regex("""^test\s+\S.*\swhen\s+\S.*\sshould\s+\S.*$""")
    }
}
