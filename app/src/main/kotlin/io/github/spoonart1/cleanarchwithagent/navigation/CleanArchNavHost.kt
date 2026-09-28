package io.github.spoonart1.cleanarchwithagent.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import io.github.spoonart1.cleanarchwithagent.checklists.api.ChecklistsRoutes
import io.github.spoonart1.cleanarchwithagent.designsystem.navigation.FeatureNavigation

/**
 * Builds the navigation graph from whatever features are on the classpath.
 *
 * [featureNavigations] is injected as a `Set` assembled by Hilt multibindings,
 * so this function names no individual feature. Adding a feature means adding a
 * module and a `@Binds @IntoSet` — nothing here changes.
 *
 * The start destination is the one exception: something has to be first, and
 * that route comes from `feature:checklists:api`, not from its implementation.
 */
@Composable
fun CleanArchNavHost(
    navController: NavHostController,
    featureNavigations: Set<FeatureNavigation>,
) {
    NavHost(
        navController = navController,
        startDestination = ChecklistsRoutes.LIST,
    ) {
        // `this` is the NavGraphBuilder; naming it avoids it being shadowed by
        // the lambda receiver inside forEach.
        val graphBuilder = this
        featureNavigations.forEach { feature ->
            feature.register(graphBuilder, navController)
        }
    }
}
