package com.passmanager.tools.v1import

import com.passmanager.crypto.Secret
import com.passmanager.format.PmVault
import com.passmanager.format.VaultContents
import java.io.File
import kotlin.system.exitProcess

/**
 * Carries one version 1 vault forward into version 2, once.
 *
 * **This is not a feature of the application.** Nobody using version 2 has ever seen version
 * 1 or knows it existed, so there is no import screen, no menu entry, and no version 1
 * vocabulary anywhere a user can reach. It lives in its own module that nothing on either
 * application's dependency path can see, and when it has done its job it is deleted by
 * removing one line from `settings.gradle.kts` and one directory.
 *
 * That is also why it is a command-line program on the JVM rather than something
 * multiplatform: it runs on a desk, once, on a file the person running it already has.
 *
 * ```
 * ./gradlew :tools:v1-import:run --args="old.pmvault new.pmvault"
 * ```
 *
 * Passphrases are read from the environment rather than from the command line, because a
 * command line ends up in the shell's history file and in the process list, where every
 * other process on the machine can read it.
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println(
            """
            usage: v1-import <v1-vault> <v2-vault>

            Reads the passphrases from the environment, not from arguments:
              PM_V1_PASSPHRASE   the export passphrase of the v1 file
              PM_V2_PASSPHRASE   the passphrase to protect the new file with
            """.trimIndent()
        )
        exitProcess(2)
    }

    val source = File(args[0])
    val destination = File(args[1])

    if (!source.isFile) {
        System.err.println("no such file: ${source.path}")
        exitProcess(1)
    }
    // Refusing to overwrite is not politeness. The thing being overwritten would be the only
    // copy of a vault, and an interrupted write leaves nothing behind to recover from.
    if (destination.exists()) {
        System.err.println("refusing to overwrite ${destination.path}")
        exitProcess(1)
    }

    val v1Passphrase = System.getenv("PM_V1_PASSPHRASE")
    val v2Passphrase = System.getenv("PM_V2_PASSPHRASE")
    if (v1Passphrase.isNullOrEmpty() || v2Passphrase.isNullOrEmpty()) {
        System.err.println("set PM_V1_PASSPHRASE and PM_V2_PASSPHRASE")
        exitProcess(2)
    }

    val items = Secret.copyOfUtf8(v1Passphrase).use { V1Vault.read(source.readBytes(), it) }
        .getOrElse {
            System.err.println("could not read ${source.path}: ${it.message}")
            exitProcess(1)
        }

    val written = Secret.copyOfUtf8(v2Passphrase).use {
        PmVault.create(VaultContents(items = items), it)
    }
    destination.writeBytes(written)

    // A count and a breakdown, and nothing that names an entry: this prints to a terminal
    // that keeps scrollback.
    println("imported ${items.size} items into ${destination.path}")
    items.groupingBy { it.category.key }.eachCount().toSortedMap().forEach { (category, count) ->
        println("  $category: $count")
    }
}
