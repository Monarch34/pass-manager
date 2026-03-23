package com.passmanager.crypto.kdf

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.passmanager.crypto.model.KdfParams
import javax.inject.Inject

class Argon2KdfProvider @Inject constructor() : KdfProvider {

    private val argon2 by lazy { Argon2Kt() }

    override fun deriveKey(passphrase: ByteArray, salt: ByteArray, params: KdfParams): ByteArray {
        val result = argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = passphrase,
            salt = salt,
            tCostInIterations = params.iterations,
            mCostInKibibyte = params.memory,
            parallelism = params.parallelism,
            hashLengthInBytes = params.hashLength
        )
        return result.rawHashAsByteArray()
    }
}
