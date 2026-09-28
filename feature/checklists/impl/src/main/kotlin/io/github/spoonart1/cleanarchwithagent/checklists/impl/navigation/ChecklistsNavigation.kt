package io.github.spoonart1.cleanarchwithagent.checklists.impl.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.spoonart1.cleanarchwithagent.checklists.api.ChecklistsRoutes
import io.github.spoonart1.cleanarchwithagent.checklists.impl.detail.ChecklistDetailScreen
import io.github.spoonart1.cleanarchwithagent.checklists.impl.list.ChecklistListScreen
import io.github.spoonart1.cleanarchwithagent.designsystem.navigation.FeatureNavigation
import io.github.spoonart1.cleanarchwithagent.settings.api.SettingsRoutes
import javax.inject.Inject

/**
 * Adds the checklists screens to the graph.
 *
 * Note the navigation to settings: it uses [SettingsRoutes] from
 * `feature:settings:api`. This module has no dependency on
 * `feature:settings:impl` and cannot see the settings screen itself.
 */
class ChecklistsNavigation @Inject constructor() : FeatureNavigation {

    override fun register(builder: NavGraphBuilder, navController: NavHostController) {
        builder.composable(ChecklistsRoutes.LIST) {
            ChecklistListScreen(
                onChecklistClick = { checklistId ->
                    navController.navigate(ChecklistsRoutes.detail(checklistId))
                },
                onSettingsClick = { navController.navigate(SettingsRoutes.SETTINGS) },
            )
        }

        builder.composable(
            route = ChecklistsRoutes.DETAIL,
            arguments = listOf(
                navArgument(ChecklistsRoutes.ARG_CHECKLIST_ID) { type = NavType.StringType },
            ),
        ) {
            // The id is read from SavedStateHandle by the ViewModel, so it is
            // not passed down here.
            ChecklistDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}

/**
 * Contributes this feature's navigation into the set that `app` assembles.
 *
 * `@IntoSet` is what makes the graph self-assembling: `app` injects
 * `Set<FeatureNavigation>` and never names an individual feature.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChecklistsNavigationModule {

    @Binds
    @IntoSet
    abstract fun bindsChecklistsNavigation(impl: ChecklistsNavigation): FeatureNavigation
}
