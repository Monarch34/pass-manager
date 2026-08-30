package com.passmanager.domain.password

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PasswordGeneratorTest {

    private fun generate(recipe: PasswordRecipe) =
        PasswordGenerator.generate(recipe).reveal { it }

    @Test
    fun `a password is the length that was asked for`() {
        for (length in listOf(8, 20, 64, 128)) {
            assertEquals(length, generate(PasswordRecipe(length = length)).length)
        }
    }

    @Test
    fun `only the selected kinds appear`() {
        val digitsOnly = generate(
            PasswordRecipe(length = 64, lowercase = false, uppercase = false, symbols = false)
        )
        assertTrue(digitsOnly.all { it in '0'..'9' }, digitsOnly)

        val letters = generate(PasswordRecipe(length = 64, digits = false, symbols = false))
        assertTrue(letters.all { it in 'a'..'z' || it in 'A'..'Z' }, letters)
    }

    @Test
    fun `every selected kind is present when it is required`() {
        // Short, because that is where the rule is hard to satisfy and where a generator
        // that merely hoped would fail.
        repeat(200) {
            val password = generate(PasswordRecipe(length = 8))
            assertTrue(password.any { it in 'a'..'z' }, password)
            assertTrue(password.any { it in 'A'..'Z' }, password)
            assertTrue(password.any { it in '0'..'9' }, password)
            assertTrue(password.any { !it.isLetterOrDigit() }, password)
        }
    }

    @Test
    fun `without the requirement a class may be missing`() {
        // Proves the requirement is doing something, rather than being satisfied by luck.
        val missing = (1..400).map {
            generate(PasswordRecipe(length = 8, requireEachClass = false))
        }.count { password -> password.none { it in '0'..'9' } }
        assertTrue(missing > 0, "no draw of 400 was missing a digit; the rule is not optional")
    }

    @Test
    fun `ambiguous characters can be excluded`() {
        val password = generate(PasswordRecipe(length = 128, avoidAmbiguous = true))
        for (character in "Il1|O0[]{}") {
            assertTrue(character !in password, "$character survived: $password")
        }
    }

    @Test
    fun `two draws are not the same password`() {
        val drawn = (1..100).map { generate(PasswordRecipe()) }.toSet()
        assertEquals(100, drawn.size)
    }

    @Test
    fun `the whole alphabet is reachable`() {
        // The regression this guards is a selection that never reaches the end of the
        // alphabet — an off-by-one in the bound, or a rejection limit set one too low.
        val seen = (1..400).flatMap { generate(PasswordRecipe(length = 64)).toList() }.toSet()
        val alphabet = PasswordRecipe(length = 64).let { recipe ->
            recipe.alphabetForTest().toSet()
        }
        assertEquals(emptySet(), alphabet - seen, "some characters were never drawn")
    }

    @Test
    fun `a recipe that cannot be satisfied is refused`() {
        // Four kinds required and three characters to hold them.
        assertFailsWith<IllegalArgumentException> { PasswordRecipe(length = 3) }
        assertFailsWith<IllegalArgumentException> {
            PasswordRecipe(lowercase = false, uppercase = false, digits = false, symbols = false)
        }
        assertFailsWith<IllegalArgumentException> { PasswordRecipe(length = 500) }
    }

    @Test
    fun `the advertised bits match the alphabet`() {
        // 26 lower-case letters, twenty characters: 20 * log2(26) = 94.0
        val lower = PasswordRecipe(
            length = 20, uppercase = false, digits = false, symbols = false,
        )
        assertTrue(PasswordGenerator.bits(lower) in 93.9..94.1, "${PasswordGenerator.bits(lower)}")
    }
}

class PasswordStrengthTest {

    private fun bits(password: String) = PasswordStrength.of(password).bits

    @Test
    fun `an empty password is zero rather than undefined`() {
        assertEquals(0.0, bits(""))
        assertEquals(PasswordStrength.Band.Trivial, PasswordStrength.of("").band)
    }

    @Test
    fun `a repeated character does not get stronger by being longer`() {
        // The lie a naive length times log2(alphabet) tells loudest.
        assertTrue(bits("aaaaaaaaaaaaaaaaaaaa") < 12, "${bits("aaaaaaaaaaaaaaaaaaaa")}")
        assertEquals(PasswordStrength.Band.Trivial, PasswordStrength.of("aaaaaaaaaaaaaaaaaaaa").band)
    }

    @Test
    fun `a counted sequence is not strong`() {
        // Measured against what a naive length times log2(alphabet) would have said, because
        // that is the estimate this model exists to correct — not against a round number.
        for (sequence in listOf("123456789012", "abcdefghijkl", "zyxwvutsrqpo")) {
            val naive = sequence.length * kotlin.math.log2(if (sequence[0].isDigit()) 10.0 else 26.0)
            assertTrue(bits(sequence) < naive / 2, "$sequence scored ${bits(sequence)} of $naive")
            assertEquals(PasswordStrength.Band.Trivial, PasswordStrength.of(sequence).band, sequence)
        }
        // 123456789012 is two runs, not one: 9 to 0 is not a step of one, so the model
        // charges for starting again. That is the correct answer and it is still trivial.
        assertTrue(bits("123456789012") > bits("123456789"))
    }

    @Test
    fun `a run inside a password only discounts the run`() {
        val withRun = bits("Kq7aaaaaaaa#Zt")
        val without = bits("Kq7#Zt")
        // The eight a's are worth barely more than one character, so the two are close.
        assertTrue(withRun - without < 12, "$withRun vs $without")
        assertTrue(withRun > without, "a run must still cost something")
    }

    @Test
    fun `a drawn password lands in the strong band`() {
        val drawn = PasswordGenerator.generate(PasswordRecipe(length = 20)).reveal { it }
        val strength = PasswordStrength.of(drawn)
        assertEquals(PasswordStrength.Band.Strong, strength.band, "$drawn scored ${strength.bits}")
    }

    @Test
    fun `a longer password of the same alphabet scores higher`() {
        assertTrue(bits("k7#Ztq") < bits("k7#Ztqm2!Xr"))
    }

    @Test
    fun `a wider alphabet scores higher at the same length`() {
        assertTrue(bits("kqmzrtvw") < bits("kQ7#zRtv"))
    }

    @Test
    fun `a Turkish password is not credited with an alphabet nobody searched`() {
        // ş, ğ and ı are counted as themselves, not as a pretend Unicode-sized alphabet.
        // Overstating this would inflate the score for exactly the people whose keyboards
        // produce these characters.
        val turkish = PasswordStrength.of("şifreğüı")
        assertTrue(turkish.bits < PasswordStrength.of("kQ7#zRtv").bits, "${turkish.bits}")
        assertTrue(turkish.bits > 0)
    }

    @Test
    fun `one repeated non-ASCII character does not divide by a one character alphabet`() {
        // log2(1) is zero and would collapse the whole sum to nothing.
        assertTrue(PasswordStrength.of("şşşşşşşş").bits > 0)
    }

    @Test
    fun `the bands are ordered`() {
        val ascending = listOf("aaaa", "hunter2", "Tr0ub4dor", "kQ7#zRtvm2!XrpL9&Wd")
        val bands = ascending.map { PasswordStrength.of(it).band.ordinal }
        assertEquals(bands.sorted(), bands, "$bands")
    }

    @Test
    fun `the meter saturates rather than exceeding one`() {
        val long = PasswordGenerator.generate(PasswordRecipe(length = 128)).reveal { it }
        assertTrue(PasswordStrength.of(long).fraction <= 1.0)
    }
}

/** Reaches the recipe's alphabet for the coverage test without publishing it. */
private fun PasswordRecipe.alphabetForTest(): String =
    (if (lowercase) "abcdefghijklmnopqrstuvwxyz" else "") +
        (if (uppercase) "ABCDEFGHIJKLMNOPQRSTUVWXYZ" else "") +
        (if (digits) "0123456789" else "") +
        (if (symbols) "!#$%&()*+,-./:;<=>?@[]^_{|}~" else "")
