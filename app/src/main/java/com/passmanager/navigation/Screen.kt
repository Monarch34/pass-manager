package com.passmanager.navigation

import com.passmanager.domain.model.ItemCategory

import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Lock : Screen("lock")

    /** Dead end for a vault whose device key is permanently gone: erase and start over. */
    data object VaultRecovery : Screen("vault_recovery")
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
    data object PasswordGenerator : Screen("password_generator?constraintCategory={constraintCategory}") {
        /**
         * Carries the category the generator is being opened for. Without it the generator keeps
         * its own defaults, which for BANK produces a password the item form immediately rejects.
         */
        fun createRoute(category: ItemCategory?): String =
            if (category == null) "password_generator"
            else "password_generator?constraintCategory=${category.dbKey}"
    }
    data object DesktopLink : Screen("desktop_link")

    data object DrawerGenerator : Screen("password_generator_drawer")
    data object DrawerSettings : Screen("settings_drawer")
}
