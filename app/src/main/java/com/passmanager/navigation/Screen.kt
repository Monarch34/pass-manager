package com.passmanager.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Lock : Screen("lock")
    /** Bottom tabs: vault graph + desktop link (after unlock). */
    object Main : Screen("main")
    object VaultList : Screen("vault_list")
    object AddEditItem : Screen("add_edit_item?itemId={itemId}&initialCategory={initialCategory}") {
        /** [initialCategory] is the enum name lowercased (e.g. `login`, `card`) when adding with a vault filter. */
        fun createRoute(itemId: String? = null, initialCategory: String? = null): String {
            val id = itemId?.let { Uri.encode(it) } ?: ""
            val cat = initialCategory ?: ""
            return "add_edit_item?itemId=$id&initialCategory=$cat"
        }
    }
    object PasswordGenerator : Screen("password_generator")
    object Settings : Screen("settings")
}
