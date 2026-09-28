package io.github.spoonart1.cleanarchwithagent.checklists.api

/**
 * The checklists feature's public surface: its routes, and nothing else.
 *
 * Anything that needs to navigate here depends on this module. Keeping it to
 * routes is what stops the api/impl split from quietly becoming a second copy
 * of the implementation.
 */
object ChecklistsRoutes {

    /** The checklist list, and the app's start destination. */
    const val LIST = "checklists"

    /** Route pattern for one checklist's detail screen. */
    const val DETAIL = "checklists/{checklistId}"

    const val ARG_CHECKLIST_ID = "checklistId"

    /** Builds a concrete detail route for [checklistId]. */
    fun detail(checklistId: String): String = "checklists/$checklistId"
}
