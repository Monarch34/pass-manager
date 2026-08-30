package com.passmanager.domain.password

import kotlin.math.log2
import kotlin.math.min

/**
 * How much guessing a password would take, and how little that number knows.
 *
 * ### What it measures
 *
 * The cost of a search that knows the alphabet and knows that people repeat characters and
 * run up the keyboard. The password is cut into runs — three or more of the same character,
 * or three or more consecutive code points in either direction — and a run is charged for
 * its first character and its length rather than for every character in it. Everything else
 * is charged the full width of the alphabet actually used.
 *
 * That is a small model, deliberately. It exists to stop the one lie a naive
 * `length × log2(alphabet)` tells loudly: that `aaaaaaaaaaaa` and `123456789012` are strong.
 * They score as what they are here.
 *
 * ### What it does not measure, and must be said out loud wherever it is shown
 *
 * It does not know whether a password has been breached, and that is the question that
 * actually matters. `Tr0ub4dor&3` scores well here and is in every list ever published. This
 * number is an **upper bound on the effort of an attacker who knows nothing about the
 * owner** — not a promise, and never a reason to keep a password a leak has already spent.
 *
 * Answering the real question needs a corpus this application does not carry and a lookup it
 * will not make: a local-first vault has no server to ask, and asking someone else's is the
 * one thing this design refuses. So the estimate is honest about its own ceiling instead of
 * pretending to be a verdict.
 */
class PasswordStrength private constructor(
    /** The estimate, in bits. Zero for an empty password rather than undefined. */
    val bits: Double,
    val band: Band,
) {

    /**
     * Bands, not a percentage.
     *
     * A percentage of an unbounded quantity is meaningless, and a number to one decimal place
     * invites comparing two passwords that are both fine. The thresholds are the ones that
     * correspond to something real: below 40 bits is reachable by a single machine, 60 is
     * reachable by someone who wants it, 80 is not reachable by brute force today.
     */
    enum class Band { Trivial, Weak, Reasonable, Strong }

    companion object {

        /** Every class an attacker would have to search, and how wide each one is. */
        private const val Lowercase = 26
        private const val Uppercase = 26
        private const val Digits = 10

        /** Printable ASCII that is not a letter or a digit. */
        private const val Symbols = 33

        /** Three is where a run stops being a coincidence. */
        private const val RunLength = 3

        /**
         * A run's length is worth about this much on its own.
         *
         * Charging a run for its first character plus a few bits for how long it goes is the
         * whole correction. Charging it for nothing would make `aaaa` free; charging it per
         * character is the failure this exists to fix.
         */
        private const val RunLengthBits = 4.0

        fun of(password: String): PasswordStrength {
            if (password.isEmpty()) return PasswordStrength(0.0, Band.Trivial)

            val perCharacter = log2(alphabet(password).toDouble())
            var bits = 0.0
            var at = 0
            while (at < password.length) {
                val run = runLengthAt(password, at)
                if (run >= RunLength) {
                    // The first character, then the fact that it continues. An attacker
                    // enumerating runs pays for the start and the length, not for each step.
                    bits += perCharacter + RunLengthBits
                    at += run
                } else {
                    bits += perCharacter
                    at += 1
                }
            }
            return PasswordStrength(bits, band(bits))
        }

        /**
         * The alphabet an attacker would search.
         *
         * The classes that appear, at full width — someone who knows a password has a digit
         * in it searches all ten, not the one that is there. Characters outside printable
         * ASCII are counted as themselves and no more: there is no honest way to guess how
         * much of Turkish or Greek a particular attacker is searching, and assuming a large
         * alphabet on the owner's behalf would inflate the score for exactly the users whose
         * keyboards produce those characters.
         */
        private fun alphabet(password: String): Int {
            var width = 0
            if (password.any { it in 'a'..'z' }) width += Lowercase
            if (password.any { it in 'A'..'Z' }) width += Uppercase
            if (password.any { it in '0'..'9' }) width += Digits
            if (password.any { it.isAsciiSymbol() }) width += Symbols
            width += password.filter { !it.isAscii() }.toSet().size
            // A password of one repeated non-ASCII character would otherwise have an
            // alphabet of one and an entropy of zero, which is true and useless: log2(1) is
            // zero and the whole sum collapses. Two is the floor at which the arithmetic
            // still says something.
            return maxOf(width, 2)
        }

        /** How far the run starting at [at] goes: same character, or consecutive either way. */
        private fun runLengthAt(password: String, at: Int): Int {
            if (at + 1 >= password.length) return 1
            val step = password[at + 1].code - password[at].code
            if (step !in -1..1) return 1
            var length = 2
            while (
                at + length < password.length &&
                password[at + length].code - password[at + length - 1].code == step
            ) {
                length++
            }
            return length
        }

        private fun band(bits: Double): Band = when {
            bits < 28 -> Band.Trivial
            bits < 48 -> Band.Weak
            bits < 72 -> Band.Reasonable
            else -> Band.Strong
        }

        private fun Char.isAscii() = code in 32..126

        private fun Char.isAsciiSymbol() =
            isAscii() && this !in 'a'..'z' && this !in 'A'..'Z' && this !in '0'..'9'
    }

    /**
     * A short, honest label. Never a colour name and never a score out of ten — both invite
     * treating the number as a verdict it is not entitled to give.
     */
    val summary: String
        get() = when (band) {
            Band.Trivial -> "Guessable almost immediately"
            Band.Weak -> "One machine could work through this"
            Band.Reasonable -> "Costly to guess, if it has never leaked"
            Band.Strong -> "Not reachable by guessing"
        }

    /** For a meter. Saturates at the point beyond which more bits change no decision. */
    val fraction: Double get() = min(bits / 96.0, 1.0)
}
