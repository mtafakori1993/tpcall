package ir.tafakoritech.tpcall

import ir.tafakoritech.tpcall.util.ExtensionValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionValidatorTest {

    @Test
    fun `valid three digit extensions pass`() {
        assertTrue(ExtensionValidator.isValidExtension("100"))
        assertTrue(ExtensionValidator.isValidExtension("203"))
        assertTrue(ExtensionValidator.isValidExtension("999"))
    }

    @Test
    fun `invalid lengths fail`() {
        assertFalse(ExtensionValidator.isValidExtension(""))
        assertFalse(ExtensionValidator.isValidExtension("20"))
        assertFalse(ExtensionValidator.isValidExtension("2030"))
    }

    @Test
    fun `non digit and out of range fail`() {
        assertFalse(ExtensionValidator.isValidExtension("20a"))
        assertFalse(ExtensionValidator.isValidExtension("099"))
        assertFalse(ExtensionValidator.isValidExtension("12 "))
    }

    @Test
    fun `sanitize strips non digits and limits to three`() {
        assertEquals("203", ExtensionValidator.sanitize("20a3b"))
        assertEquals("123", ExtensionValidator.sanitize("123456"))
        assertEquals("", ExtensionValidator.sanitize("abc"))
    }

    @Test
    fun `password and name rules`() {
        assertFalse(ExtensionValidator.isPasswordValid("12345"))
        assertTrue(ExtensionValidator.isPasswordValid("123456"))
        assertFalse(ExtensionValidator.isNameValid("   "))
        assertTrue(ExtensionValidator.isNameValid("محمد تفکری"))
    }
}
