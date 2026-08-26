package com.passmanager.data.file

import android.content.Context
import android.net.Uri
import com.passmanager.domain.port.VaultFilePort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentResolverVaultFilePort @Inject constructor(
    @ApplicationContext private val context: Context
) : VaultFilePort {

    override suspend fun read(uri: String): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
            ?: throw FileNotFoundException("Could not open $uri for reading")
    }

    override suspend fun write(uri: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        // "wt" truncates. Overwriting an existing backup without it would leave the tail of a
        // longer previous export appended to the new one, and that trailing garbage fails the
        // container's own length checks on the way back in.
        context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use { stream ->
            stream.write(bytes)
            stream.flush()
        } ?: throw FileNotFoundException("Could not open $uri for writing")
    }
}
