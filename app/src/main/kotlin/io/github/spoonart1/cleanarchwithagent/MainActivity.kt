package io.github.spoonart1.cleanarchwithagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.spoonart1.cleanarchwithagent.designsystem.navigation.FeatureNavigation
import io.github.spoonart1.cleanarchwithagent.designsystem.theme.CleanArchTheme
import io.github.spoonart1.cleanarchwithagent.navigation.CleanArchNavHost
import javax.inject.Inject

/**
 * The only Activity. It applies the theme and hosts the navigation graph.
 *
 * [featureNavigations] arrives as a multibound `Set`, so this class does not
 * name a single feature and never needs editing when one is added.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var featureNavigations: Set<@JvmSuppressWildcards FeatureNavigation>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            CleanArchTheme {
                CleanArchNavHost(
                    navController = rememberNavController(),
                    featureNavigations = featureNavigations,
                )
            }
        }
    }
}
