package com.passmanager.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

import dagger.Provides
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
            install(ContentNegotiation) { 
                json(Json { ignoreUnknownKeys = true; isLenient = true }) 
            }
            install(WebSockets) {
                // Mirrors the desktop server cap. Without it the phone is the soft side of the
                // pair: a compromised or spoofed desktop could send one unbounded frame and OOM
                // the app that holds the vault.
                maxFrameSize = WS_MAX_FRAME_SIZE_BYTES
            }
        }
    }

    /**
     * Keep in step with `PairingServer.MAX_FRAME_SIZE_BYTES` on the desktop side. The two are
     * separate Gradle builds, so this cannot be a shared constant without moving it into
     * `:protocol`; a mismatch is not a build error, it just means one side accepts a frame the
     * other would have refused.
     */
    private const val WS_MAX_FRAME_SIZE_BYTES = 1024L * 1024
}
