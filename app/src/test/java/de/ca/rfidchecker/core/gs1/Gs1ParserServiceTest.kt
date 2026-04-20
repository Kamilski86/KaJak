package de.ca.rfidchecker.core.gs1

import org.junit.Assert.assertEquals
import org.junit.Test

class Gs1ParserServiceTest {
    private val parser = Gs1ParserService()

    @Test
    fun parseQr_extractsGtinAndSerial() {
        val result = parser.parseQr("http://qr.c-a.com/01/04065546927682/21/12884901894")
        assertEquals("04065546927682", result.gtin)
        assertEquals("12884901894", result.serial)
    }
}
