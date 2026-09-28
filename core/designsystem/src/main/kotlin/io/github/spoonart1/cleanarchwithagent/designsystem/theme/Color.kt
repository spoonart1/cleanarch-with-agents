package io.github.spoonart1.cleanarchwithagent.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * A small, deliberate palette.
 *
 * Green reads as "field work" without being a default purple, and the sync
 * status colours below are chosen so pending/failed/conflict stay
 * distinguishable in both light and dark schemes.
 */
internal val Green40 = Color(0xFF1B6C4A)
internal val GreenGrey40 = Color(0xFF4F6354)
internal val Sand40 = Color(0xFF7A5900)

internal val Green80 = Color(0xFF8FD9B0)
internal val GreenGrey80 = Color(0xFFB6CCBB)
internal val Sand80 = Color(0xFFF2C14E)

internal val Red40 = Color(0xFFB3261E)
internal val Red80 = Color(0xFFF2B8B5)

/**
 * Sync status colours, resolved per scheme.
 *
 * Kept out of the Material colour roles because they carry a specific meaning
 * that should not drift when the theme changes.
 */
internal val PendingLight = Color(0xFF7A5900)
internal val PendingDark = Color(0xFFF2C14E)
internal val SyncedLight = Color(0xFF1B6C4A)
internal val SyncedDark = Color(0xFF8FD9B0)
internal val ConflictLight = Color(0xFF8A5000)
internal val ConflictDark = Color(0xFFFFB77C)
