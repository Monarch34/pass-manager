package com.passmanager.di

import com.passmanager.data.preferences.AppPreferences
import com.passmanager.domain.port.AppSettingsPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesModule {

    @Binds
    @Singleton
    abstract fun bindAppSettingsPort(impl: AppPreferences): AppSettingsPort
}
