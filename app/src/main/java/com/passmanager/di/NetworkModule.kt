package com.passmanager.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for network-related bindings.
 *
 * The [DesktopPairingClient] creates its own internal Ktor HttpClient per session
 * (ephemeral, not singleton) so no Hilt-provided client is needed. This module is
 * reserved for future network dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule
