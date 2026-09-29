package io.github.spoonart1.cleanarchwithagent.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Registers this project's custom rules under the `cleanarch` rule set, which
 * is how they are addressed in `config/detekt/detekt.yml`.
 *
 * Detekt discovers this class through
 * `META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`. Without
 * that file the jar loads and nothing runs — no error, just silence.
 */
class CleanArchRuleSetProvider : RuleSetProvider {

    override val ruleSetId: String = "cleanarch"

    override fun instance(config: Config): RuleSet = RuleSet(
        id = ruleSetId,
        rules = listOf(
            TestFunctionNaming(config),
            BooleanPropertyNaming(config),
        ),
    )
}
