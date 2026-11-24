package com.example.armineordermodule.rfid

import com.example.armineordermodule.rfid.chainway.ChainwayBroadcastParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ornek degerler docs/chainway-rfid.md Bolum 3'teki gercek cihaz okumalarindan alindi. */
class ChainwayBroadcastParserTest {

    @Test
    fun parsesEpcAndTurkishLocaleRssi() {
        val tag = ChainwayBroadcastParser.parse(
            epc = "868472656594800000000400",
            data = "868472656594800000000400",
            rssi = "-50,40",
        )!!
        assertEquals("868472656594800000000400", tag.epcId)
        assertEquals(-50, tag.rssi)
        assertEquals(1, tag.antId)
    }

    @Test
    fun parsesDotDecimalRssi() {
        assertEquals(-62, ChainwayBroadcastParser.parseRssi("-61.6"))
    }

    @Test
    fun unparsableRssiIsNull() {
        assertNull(ChainwayBroadcastParser.parseRssi(""))
        assertNull(ChainwayBroadcastParser.parseRssi("abc"))
        assertNull(ChainwayBroadcastParser.parseRssi(null))
    }

    @Test
    fun trimsAndUppercasesEpc() {
        val tag = ChainwayBroadcastParser.parse(epc = "  e2801170000002 ", data = null, rssi = null)!!
        assertEquals("E2801170000002", tag.epcId)
        assertNull(tag.rssi)
    }

    @Test
    fun fallsBackToDataWhenEpcMissing() {
        val tag = ChainwayBroadcastParser.parse(epc = " ", data = "868472656594800000000401", rssi = null)!!
        assertEquals("868472656594800000000401", tag.epcId)
    }

    @Test
    fun emptyBroadcastIsIgnored() {
        assertNull(ChainwayBroadcastParser.parse(epc = null, data = "", rssi = "-50,40"))
    }
}
