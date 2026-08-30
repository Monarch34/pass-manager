package com.passmanager.domain.password

import com.passmanager.crypto.Secret
import com.passmanager.crypto.random.secureRandomBytes
import com.passmanager.domain.item.SecretText

/**
 * What kind of password to draw.
 *
 * [requireEachClass] is the concession to websites, not to security: forcing one of every
 * selected class can only shrink the set of possible passwords, never grow it. It is here
 * because a generator whose output a bank's form rejects is a generator nobody uses.
 */
class PasswordRecipe(
    val length: Int = DefaultLength,
    val lowercase: Boolean = true,
    val uppercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    /**
     * Excludes the characters that are read wrong when a password is copied off a screen by
     * hand: `Il1|`, `O0`, and the brackets that look alike in some fonts.
     */
    val avoidAmbiguous: Boolean = false,
    /** Guarantees at least one character from every class turned on. */
    val requireEachClass: Boolean = true,
) {

    init {
        require(length in MinLength..MaxLength) {
            "a generated password is $MinLength to $MaxLength characters, not $length"
        }
        require(classes.isNotEmpty()) { "a password needs at least one kind of character" }
        require(!requireEachClass || length >= classes.size) {
            "a $length character password cannot hold one of each of ${classes.size} kinds"
        }
    }

    internal val classes: List<String>
        get() = buildList {
            if (lowercase) add(filtered(LowercaseSet))
            if (uppercase) add(filtered(UppercaseSet))
            if (digits) add(filtered(DigitSet))
            if (symbols) add(filtered(SymbolSet))
        }.filter { it.isNotEmpty() }

    internal val alphabet: String get() = classes.joinToString("")

    private fun filtered(set: String) =
        if (avoidAmbiguous) set.filter { it !in Ambiguous } else set

    companion object {
        /**
         * Twenty. Long enough that the alphabet stops mattering — twenty characters of
         * lower case alone is 94 bits — and short enough to retype from a screen when some
         * form refuses to be pasted into.
         */
        const val DefaultLength = 20

        /** Below eight nothing is worth generating; above this a field stops being usable. */
        const val MinLength = 8
        const val MaxLength = 128

        private const val LowercaseSet = "abcdefghijklmnopqrstuvwxyz"
        private const val UppercaseSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val DigitSet = "0123456789"

        /** Printable ASCII that is not a letter or a digit, minus space, which forms trim. */
        private const val SymbolSet = "!#$%&()*+,-./:;<=>?@[]^_{|}~"

        private const val Ambiguous = "Il1|O0[]{}"
    }
}

/**
 * Draws passwords.
 *
 * ### The password is never a String
 *
 * It is assembled as bytes and handed straight to [SecretText], which owns and can erase
 * them. Building it as a `String` first would put the generated password in an object
 * neither the JVM nor Swift can overwrite, surviving until a collector happens to run — for
 * the single value in this application most worth not leaving lying about. The alphabet is
 * ASCII, so a byte is a character and nothing is lost by working one level down.
 *
 * ### Selection is uniform, and that takes work
 *
 * `random % alphabet.length` is biased towards the front of the alphabet whenever the
 * alphabet does not divide the range evenly, which for 94 printable characters it does not.
 * Bytes that fall in the short tail are thrown away instead. The cost is a few extra draws;
 * the alternative is a generator that quietly prefers `a` to `z`.
 */
object PasswordGenerator {

    fun generate(recipe: PasswordRecipe = PasswordRecipe()): SecretText {
        val alphabet = recipe.alphabet.encodeToByteArray()
        val classes = recipe.classes.map { it.encodeToByteArray() }
        val draws = Draws()

        repeat(MaxAttempts) {
            val password = ByteArray(recipe.length) { alphabet[draws.below(alphabet.size)] }
            // Rejection, not repair. Placing a required character at a chosen position and
            // filling around it makes some passwords likelier than others; throwing away a
            // draw that misses a class leaves what remains uniform over exactly the
            // passwords the recipe allows.
            if (!recipe.requireEachClass || classes.all { set -> password.any { it in set } }) {
                return SecretText.adopt(Secret.adopt(password))
            }
            password.fill(0)
        }
        // Unreachable for any recipe the constructor accepts — the odds of missing a class
        // this many times running are far below the odds of the hardware being broken — but
        // a generator that silently returned a weaker password would be worse than one that
        // stops.
        error("could not draw a password matching this recipe")
    }

    /**
     * The bits behind a recipe, as an upper bound.
     *
     * `length × log2(alphabet)` exactly, and it is an upper bound rather than a figure
     * because [PasswordRecipe.requireEachClass] narrows the set of possible passwords.
     * The loss is under a bit at any length worth generating, which is why it is stated in
     * prose here rather than modelled.
     */
    fun bits(recipe: PasswordRecipe): Double =
        recipe.length * kotlin.math.log2(recipe.alphabet.length.toDouble())

    /** Enough that failing is impossible in practice; small enough not to hang if it is not. */
    private const val MaxAttempts = 10_000

    /**
     * Buffered entropy.
     *
     * The platform generator is asked for bytes in blocks rather than one at a time: drawing
     * singly is a syscall per character on some platforms, and a twenty character password
     * with rejection would make dozens of them.
     */
    private class Draws {
        private var buffer = secureRandomBytes(BlockSize)
        private var at = 0

        fun below(bound: Int): Int {
            // The largest multiple of the bound that fits in a byte. Anything at or above it
            // is discarded, which is what keeps the result uniform.
            val limit = 256 - (256 % bound)
            while (true) {
                val value = next()
                if (value < limit) return value % bound
            }
        }

        private fun next(): Int {
            if (at == buffer.size) {
                buffer.fill(0)
                buffer = secureRandomBytes(BlockSize)
                at = 0
            }
            return buffer[at++].toInt() and 0xff
        }

        private companion object {
            const val BlockSize = 64
        }
    }
}
