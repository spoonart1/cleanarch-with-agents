package io.github.spoonart1.cleanarchwithagent.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.config
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Requires boolean properties to read as a question: `isLoading`, `hasItems`,
 * `canRetry` — never a bare noun like `loading` or `error`.
 *
 * This rule works without type resolution, so it matches on the *declared*
 * type only. `val done: Boolean` is flagged; `val done = true` is not, because
 * seeing that it is a boolean would need the type solver. Declaring the type
 * explicitly on a boolean is cheap, so this trade is deliberate: the rule
 * never guesses and so never produces a false positive.
 */
class BooleanPropertyNaming(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "BooleanPropertyNaming",
        severity = Severity.Style,
        description = "Boolean properties should start with a question-like prefix " +
            "such as 'is', 'has', 'can' or 'should', so reading the name tells you " +
            "it holds a yes/no answer.",
        debt = Debt.FIVE_MINS,
    )

    /**
     * Allowed prefixes. `is` is the house default the team agreed on; the
     * others are here because forcing `isHasItems` would be worse than the
     * problem the rule solves.
     */
    private val allowedPrefixes: List<String> by config(
        listOf("is", "has", "can", "should", "are", "was", "were", "will", "does", "did"),
    )

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        checkName(property.name, property.typeReference?.text, property)
    }

    override fun visitParameter(parameter: KtParameter) {
        super.visitParameter(parameter)
        // Only constructor properties (`val`/`var` in a constructor) are
        // checked. A plain function parameter named `enabled` reads fine at
        // a call site, where the parameter name is usually visible.
        if (!parameter.hasValOrVar()) return
        checkName(parameter.name, parameter.typeReference?.text, parameter)
    }

    private fun checkName(name: String?, declaredType: String?, element: KtElement) {
        if (name == null || !isBooleanType(declaredType)) return
        if (hasAllowedPrefix(name)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(element),
                message = "Boolean property '$name' should start with one of " +
                    "${allowedPrefixes.joinToString(", ")} — for example 'is${name.replaceFirstChar(Char::uppercase)}'.",
            ),
        )
    }

    private fun isBooleanType(declaredType: String?): Boolean =
        declaredType == "Boolean"

    /**
     * A prefix only counts when the next character starts a new word, so
     * `isLoading` passes but `island` does not get a free pass from `is`.
     */
    private fun hasAllowedPrefix(name: String): Boolean =
        allowedPrefixes.any { prefix ->
            name.length > prefix.length &&
                name.startsWith(prefix) &&
                name[prefix.length].isUpperCase()
        }
}
