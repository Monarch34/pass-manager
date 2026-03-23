package com.passmanager.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Lock : Screen("lock")
    /** Bottom tabs: vault graph + desktop link (after unlock). */
    object Main : Screen("main")
    object VaultList : Screen("vault_list")
    object AddEditItem : Screen("add_edit_item?itemId={itemId}") {
        fun createRoute(itemId: String? = null): String =
            if (itemId != null) "add_edit_item?itemId=$itemId" else "add_edit_item"
    }
    object PasswordGenerator : Screen("password_generator")
    object Settings : Screen("settings")
}
