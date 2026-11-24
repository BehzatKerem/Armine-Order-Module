package com.example.armineordermodule.rfid

import org.junit.Assert.assertEquals
import org.junit.Test

class RfidDeviceDetectorTest {

    @Test
    fun chainwayWhenKeyboardEmulatorInstalled() {
        assertEquals(
            RfidReaderType.CHAINWAY,
            RfidDeviceDetector.detect(manufacturer = "alps", brand = "alps", chainwayAppInstalled = true),
        )
    }

    @Test
    fun chainwayWhenVendorMatchesEvenWithoutApp() {
        assertEquals(
            RfidReaderType.CHAINWAY,
            RfidDeviceDetector.detect(manufacturer = "Chainway", brand = null, chainwayAppInstalled = false),
        )
    }

    @Test
    fun bldIsDefault() {
        assertEquals(
            RfidReaderType.BLD,
            RfidDeviceDetector.detect(manufacturer = "BLD", brand = "N60", chainwayAppInstalled = false),
        )
        assertEquals(
            RfidReaderType.BLD,
            RfidDeviceDetector.detect(manufacturer = null, brand = null, chainwayAppInstalled = false),
        )
    }

    @Test
    fun prefValuesRoundTrip() {
        RfidReaderType.entries.forEach {
            assertEquals(it, RfidReaderType.fromPrefValue(it.prefValue))
        }
        assertEquals(null, RfidReaderType.fromPrefValue(null))
        assertEquals(null, RfidReaderType.fromPrefValue("unknown"))
    }
}
