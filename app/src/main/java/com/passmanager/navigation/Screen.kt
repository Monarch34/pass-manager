package com.passmanager.navigation

import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Lock : Screen("lock")
    data object Main : Screen("main")
    data object VaultList : Screen("vault_list")
    data object AddEditItem : Screen("add_edit_item/{itemId}?initialCategory={initialCategory}") {
        /** Path segment when adding an item — not a vault row id. */
        const val NEW_ITEM_ROUTE_ID = "new"

        fun createRoute(itemId: String? = null, initialCategory: String? = null): String {
            val id = itemId?.let { URLEncoder.encode(it, "UTF-8") } ?: NEW_ITEM_ROUTE_ID
            val cat = initialCategory ?: ""
            return "add_edit_item/$id?initialCategory=$cat"
        }
    }
    data object PasswordGenerator : Screen("password_generator")
    data object DesktopLink : Screen("desktop_link")

    data object DrawerGenerator : Screen("password_generator_drawer")
    data object DrawerSettings : Screen("settings_drawer")
}
