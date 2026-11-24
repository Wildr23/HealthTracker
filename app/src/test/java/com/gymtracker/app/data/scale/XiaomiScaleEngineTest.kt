package com.gymtracker.app.data.scale

import com.gymtracker.app.domain.ScaleBodyType
import com.gymtracker.app.domain.XiaomiBodyCompositionCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiScaleEngineTest {

    private fun frame181B(
        ctrl0: Int,
        ctrl1: Int,
        impedance: Int,
        rawWeight: Int
    ): ByteArray = ByteArray(13).apply {
        this[0] = ctrl0.toByte()
        this[1] = ctrl1.toByte()
        // timestamp bytes [2..8] zeroed for unit test
        this[9] = (impedance and 0xFF).toByte()
        this[10] = ((impedance shr 8) and 0xFF).toByte()
        this[11] = (rawWeight and 0xFF).toByte()
        this[12] = ((rawWeight shr 8) and 0xFF).toByte()
    }

    private fun frame181D(
        ctrl: Int,
        rawWeight: Int
    ): ByteArray = ByteArray(10).apply {
        this[0] = ctrl.toByte()
        this[1] = (rawWeight and 0xFF).toByte()
        this[2] = ((rawWeight shr 8) and 0xFF).toByte()
    }

    @Test
    fun parse181B_kgLbsJin_stabilizedImpedanceAndFlags() {
        // Unstabilized kg: 105.00 kg -> rawWeight = 21000
        val unstab = XiaomiScaleBleParser.parseServiceData(
            mapOf(XiaomiScaleBleParser.UUID_BODY_COMPOSITION to frame181B(ctrl0 = 0x02, ctrl1 = 0x00, impedance = 0, rawWeight = 21000))
        )
        assertNotNull(unstab)
        assertEquals(ScaleProtocol.BODY_COMPOSITION_181B, unstab!!.protocol)
        assertEquals(105.0, unstab.weightKg, 0.01)
        assertFalse(unstab.isStabilized)
        assertFalse(unstab.hasImpedance)
        assertEquals("kg", unstab.unitLabel)

        // Stabilized weight-only (no impedance bit 0x02): ctrl1 = 0x20
        val stabNoImp = XiaomiScaleBleParser.parseBodyComposition181B(
            frame181B(ctrl0 = 0x02, ctrl1 = 0x20, impedance = 450, rawWeight = 21080)
        )
        assertNotNull(stabNoImp)
        assertTrue(stabNoImp!!.isStabilized)
        assertFalse(stabNoImp.hasImpedance)
        assertNull(stabNoImp.impedanceOhm)
        assertEquals(105.4, stabNoImp.weightKg, 0.01)

        // Stabilized + impedance: ctrl1 = 0x22 (0x20 | 0x02), 450 Ohm, 105.40 kg
        val stabImp = XiaomiScaleBleParser.parseBodyComposition181B(
            frame181B(ctrl0 = 0x02, ctrl1 = 0x22, impedance = 450, rawWeight = 21080)
        )
        assertNotNull(stabImp)
        assertTrue(stabImp!!.isStabilized)
        assertTrue(stabImp.hasImpedance)
        assertEquals(450, stabImp.impedanceOhm)
        assertEquals(105.4, stabImp.weightKg, 0.01)

        // Lbs mode: ctrl0 bit 0x01 set, 200.00 lbs (rawWeight = 20000) -> 90.72 kg
        val lbs = XiaomiScaleBleParser.parseBodyComposition181B(
            frame181B(ctrl0 = 0x03, ctrl1 = 0x20, impedance = 0, rawWeight = 20000)
        )
        assertNotNull(lbs)
        assertEquals("lbs", lbs!!.unitLabel)
        assertEquals(90.72, lbs.weightKg, 0.02)

        // Catty/Jin mode: ctrl1 bit 0x40 set, 160.00 jin (rawWeight = 16000) -> 80.00 kg
        val jin = XiaomiScaleBleParser.parseBodyComposition181B(
            frame181B(ctrl0 = 0x00, ctrl1 = 0x60, impedance = 0, rawWeight = 16000)
        )
        assertNotNull(jin)
        assertEquals("jin", jin!!.unitLabel)
        assertEquals(80.0, jin.weightKg, 0.01)

        // Weight removed flag 0x80
        val removed = XiaomiScaleBleParser.parseBodyComposition181B(
            frame181B(ctrl0 = 0x02, ctrl1 = 0xA0, impedance = 0, rawWeight = 21000)
        )
        assertNotNull(removed)
        assertTrue(removed!!.isRemoved)

        // Invalid weight bounds (< 2 kg and > 250 kg) and truncated frames rejected
        assertNull(XiaomiScaleBleParser.parseBodyComposition181B(ByteArray(12)))
        assertNull(XiaomiScaleBleParser.parseBodyComposition181B(frame181B(0x02, 0x20, 0, 100))) // 0.5 kg
        assertNull(XiaomiScaleBleParser.parseBodyComposition181B(frame181B(0x02, 0x20, 0, 60000))) // 300 kg
    }

    @Test
    fun parse181D_fallbackScale() {
        val pkt = XiaomiScaleBleParser.parseServiceData(
            mapOf(XiaomiScaleBleParser.UUID_WEIGHT_SCALE to frame181D(ctrl = 0x20, rawWeight = 16480))
        )
        assertNotNull(pkt)
        assertEquals(ScaleProtocol.WEIGHT_SCALE_181D, pkt!!.protocol)
        assertEquals(82.4, pkt.weightKg, 0.01)
        assertTrue(pkt.isStabilized)
        assertFalse(pkt.hasImpedance)
        assertNull(XiaomiScaleBleParser.parseWeightScale181D(ByteArray(9)))
    }

    @Test
    fun bodyCompositionCalculator_maleFemaleAndContinuousVisceralFat() {
        val male = XiaomiBodyCompositionCalculator.calculate(
            weightKg = 82.0,
            heightCm = 182,
            age = 34,
            isMale = true,
            impedanceOhm = 450
        )
        assertEquals(24.8, male.bmi, 0.1)
        assertNotNull(male.fatPercent)
        assertTrue(male.fatPercent!! in 8.0..35.0)
        assertNotNull(male.muscleMassKg)
        assertTrue(male.muscleMassKg!! in 40.0..75.0)
        assertNotNull(male.waterPercent)
        assertTrue(male.waterPercent!! in 45.0..70.0)
        assertNotNull(male.boneMassKg)
        assertTrue(male.boneMassKg!! in 2.0..5.0)
        assertNotNull(male.proteinPercent)
        assertTrue(male.proteinPercent!! in 10.0..26.0)
        assertNotNull(male.metabolicAge)
        assertTrue(male.bmrKcal in 1500..2200)
        assertNotNull(male.bodyType)

        val female = XiaomiBodyCompositionCalculator.calculate(
            weightKg = 58.0,
            heightCm = 166,
            age = 32,
            isMale = false,
            impedanceOhm = 520
        )
        assertEquals(21.0, female.bmi, 0.2)
        assertTrue(female.fatPercent!! in 14.0..36.0)
        assertTrue(female.muscleMassKg!! in 28.0..50.0)
        assertTrue(female.visceralFat in 1..15)
        assertTrue(female.bodyType != null && female.bodyType != ScaleBodyType.OBESE)

        // Continuous monotonic visceral fat across 55..120 kg
        var prevVf = 0
        for (w in 55..120 step 5) {
            val comp = XiaomiBodyCompositionCalculator.calculate(
                weightKg = w.toDouble(),
                heightCm = 182,
                age = 36,
                isMale = true,
                impedanceOhm = null
            )
            assertNull(comp.fatPercent)
            assertNull(comp.muscleMassKg)
            assertTrue("Visceral fat dropped at ${w}kg: ${comp.visceralFat} < $prevVf", comp.visceralFat >= prevVf)
            prevVf = comp.visceralFat
        }
    }

    @Test
    fun familyWeightFilter_ignoresWifeAndDaughter() {
        assertTrue(XiaomiScaleBleParser.isWithinUserRange(104.6, 93.0, 117.0))
        assertTrue(XiaomiScaleBleParser.isWithinUserRange(93.0, 93.0, 117.0))
        assertFalse(XiaomiScaleBleParser.isWithinUserRange(58.2, 93.0, 117.0)) // wife
        assertFalse(XiaomiScaleBleParser.isWithinUserRange(28.4, 93.0, 117.0)) // daughter
        // Supports reversed args safely
        assertTrue(XiaomiScaleBleParser.isWithinUserRange(105.0, 117.0, 93.0))
    }
}
