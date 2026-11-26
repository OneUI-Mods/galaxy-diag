package dev.ryuya.galaxydiag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSupportTest {
    @Test
    fun acceptsKnownChipsetsAndVariantsAtTheMinimumOrLater() {
        for (model in listOf("SM8650", "sm8650q-ab", "SM8750-AC", "SM8845", "SM8850-5-AC", "SM8950", "SM8975")) {
            assertTrue(model, DeviceSupport.matches(37, "Samsung", model) { 90_000 })
        }
        assertTrue(DeviceSupport.matches(38, "samsung", "SM8850") { 100_000 })
    }

    @Test
    fun rejectsOlderOrUnreadableSoftwareVersions() {
        assertFalse(DeviceSupport.matches(37, "samsung", "SM8850") { 89_999 })
        assertFalse(DeviceSupport.matches(37, "samsung", "SM8850") { null })
        assertFalse(DeviceSupport.matches(36, "samsung", "SM8850") { error("Unexpected property read") })
    }

    @Test
    fun rejectsUnknownChipsetsAndOtherManufacturersBeforeReadingTheProperty() {
        for (model in listOf("SM8550", "SM8735", "SM88501", "SM9000", "S5E9965", "")) {
            assertFalse(model, DeviceSupport.matches(37, "samsung", model) { error("Unexpected property read") })
        }
        assertFalse(DeviceSupport.matches(37, "Google", "SM8850") { error("Unexpected property read") })
    }
}
