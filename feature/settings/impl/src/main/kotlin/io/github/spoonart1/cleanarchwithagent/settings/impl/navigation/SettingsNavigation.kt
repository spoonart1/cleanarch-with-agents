package io.github.spoonart1.cleanarchwithagent.settings.impl.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.spoonart1.cleanarchwithagent.checklists.api.ChecklistsRoutes
import io.github.spoonart1.cleanarchwithagent.designsystem.navigation.FeatureNavigation
import io.github.spoonart1.cleanarchwithagent.settings.api.SettingsRoutes
import io.github.spoonart1.cleanarchwithagent.settings.impl.SettingsScreen
import javax.inject.Inject

class SettingsNavigation @Inject constructor() : FeatureNavigation {

    override fun register(builder: NavGraphBuilder, navController: NavHostController) {
        builder.composable(SettingsRoutes.SETTINGS) {
            SettingsScreen(
                onBack = {
                    // Pop if there is somewhere to go back to; otherwise fall
                    // back to the checklists route from its *api* module. This
                    // module cannot see :feature:checklists:impl.
                    if (!navController.popBackStack()) {
                        navController.navigate(ChecklistsRoutes.LIST)
                    }
                },
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsNavigationModule {

    @Binds
    @IntoSet
    abstract fun bindsSettingsNavigation(impl: SettingsNavigation): FeatureNavigation
}
