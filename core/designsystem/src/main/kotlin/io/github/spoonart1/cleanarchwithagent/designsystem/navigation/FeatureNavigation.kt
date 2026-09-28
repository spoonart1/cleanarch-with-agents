package io.github.spoonart1.cleanarchwithagent.designsystem.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

/**
 * One feature's contribution to the navigation graph.
 *
 * Features implement this and bind it `@IntoSet`, so `app` assembles the graph
 * from whatever is on its classpath. There is no central list of routes to
 * forget to update — adding a feature means adding a module, nothing else.
 *
 * This lives in `core:designsystem` rather than in `app` because every feature
 * needs it and no feature may depend on `app`.
 */
interface FeatureNavigation {

    /**
     * Adds this feature's destinations to the graph.
     *
     * [navController] is passed so a destination can navigate onward — always
     * to a route from another feature's `api` module, never by reaching into
     * its implementation.
     */
    fun register(builder: NavGraphBuilder, navController: NavHostController)
}
