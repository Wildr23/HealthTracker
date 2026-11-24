package com.gymtracker.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateCalculatorTest {

    @Test
    fun `plate calculator breaks down 105kg 82_5kg and 20kg bar`() {
        val p105 = PlateCalculator.calculate(105.0)
        assertEquals(105.0, p105.achievableWeightKg, 0.01)
        assertEquals(listOf(PlateCount(25.0, 1), PlateCount(15.0, 1), PlateCount(2.5, 1)), p105.platesPerSide)

        val p825 = PlateCalculator.calculate(82.5)
        assertEquals(82.5, p825.achievableWeightKg, 0.01)
        assertEquals(listOf(PlateCount(25.0, 1), PlateCount(5.0, 1), PlateCount(1.25, 1)), p825.platesPerSide)

        val p20 = PlateCalculator.calculate(20.0)
        assertTrue(p20.platesPerSide.isEmpty())
    }
}
