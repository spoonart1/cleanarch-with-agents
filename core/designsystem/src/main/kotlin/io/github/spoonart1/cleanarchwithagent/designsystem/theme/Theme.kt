package io.github.spoonart1.cleanarchwithagent.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Green40,
    secondary = GreenGrey40,
    tertiary = Sand40,
    error = Red40,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    secondary = GreenGrey80,
    tertiary = Sand80,
    error = Red80,
)

/**
 * Colours for sync status, alongside the Material scheme.
 *
 * These live in their own composition local rather than being squeezed into
 * Material's colour roles: "pending" is not a Material concept, and mapping it
 * onto e.g. `tertiary` would make it drift if the theme changed.
 */
@Immutable
data class SyncColors(
    val pending: Color,
    val synced: Color,
    val failed: Color,
    val conflict: Color,
)

val LocalSyncColors = staticCompositionLocalOf {
    SyncColors(
        pending = PendingLight,
        synced = SyncedLight,
        failed = Red40,
        conflict = ConflictLight,
    )
}

@Composable
fun CleanArchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val syncColors = if (darkTheme) {
        SyncColors(
            pending = PendingDark,
            synced = SyncedDark,
            failed = Red80,
            conflict = ConflictDark,
        )
    } else {
        SyncColors(
            pending = PendingLight,
            synced = SyncedLight,
            failed = Red40,
            conflict = ConflictLight,
        )
    }

    CompositionLocalProvider(LocalSyncColors provides syncColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content,
        )
    }
}
