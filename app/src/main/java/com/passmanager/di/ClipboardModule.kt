package com.passmanager.di

import android.content.Context
import com.passmanager.ui.util.SecureClipboard
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ClipboardModule {

    @Provides
    @Singleton
    fun provideSecureClipboard(@ApplicationContext context: Context): SecureClipboard =
        SecureClipboard(context)
}

/**
 * Used to reach [SecureClipboard] from Composables, which copy through the local context rather than
 * a ViewModel (see [com.passmanager.ui.util.rememberSecureClipboard]).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SecureClipboardEntryPoint {
    fun secureClipboard(): SecureClipboard
}
