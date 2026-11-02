package com.example.gpstracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerTest {

    @Test
    fun `REQ-28 riding on the flat takes power for rolling and air drag`() {
        // 30 km/h, 85 kg: 34.7 W rolling + 113.3 W drag, through the drivetrain.
        assertEquals(151.7, ridingWatts(30 / 3.6, 0.0, 85.0), 0.5)
    }

    @Test
    fun `REQ-28 climbing adds the power to lift rider and bike`() {
        // 18 km/h up a 6 % slope (0.3 m/s up), 85 kg: 20.8 W rolling + 24.5 W drag + 250.2 W climbing.
        assertEquals(302.8, ridingWatts(5.0, 0.3, 85.0), 0.5)
        // A heavier rider needs more for the same climb.
        assertEquals(335.4, ridingWatts(5.0, 0.3, 95.0), 0.5)
    }

    @Test
    fun `REQ-28 coasting downhill takes no power`() {
        assertEquals(0.0, ridingWatts(10.0, -1.0, 85.0), 0.0)
    }

    @Test
    fun `REQ-28 each power bar holds the estimated power of its minute, from its speed and climb`() {
        fun fix(north: Double, seconds: Long, altitude: Double) = fixAt(north, seconds, accuracy = 0f).copy(altitudeMeters = altitude)
        val track = listOf(
            listOf(
                // 500 m on the flat in the first minute, 300 m up 18 m (6 %) in the second, then standing still.
                fix(0.0, 0, 100.0), fix(500.0, 60, 100.0), fix(800.0, 120, 118.0), fix(800.0, 180, 118.0),
            ),
        )

        val bars = powerBars(track, 85.0)

        assertEquals(listOf(0L, 60_000L, 120_000L), bars.map { it.startMillis })
        assertEquals(ridingWatts(500 / 60.0, 0.0, 85.0), bars[0].watts!!, 1.0)
        assertEquals(302.8, bars[1].watts!!, 1.0)
        assertNull(bars[2].watts)
    }

    @Test
    fun `REQ-28 a run without altitudes is estimated as flat`() {
        val track = listOf(listOf(fixAt(0.0, 0, accuracy = 0f), fixAt(500.0, 60, accuracy = 0f)))

        assertEquals(ridingWatts(500 / 60.0, 0.0, 85.0), powerBars(track, 85.0).single().watts!!, 1.0)
    }

    @Test
    fun `REQ-28 the power axis runs from 0 up to the highest power rounded up to 100 W`() {
        fun bar(watts: Double?) = PowerBar(0, 60_000, watts)
        assertEquals(PowerAxis(300), powerAxis(listOf(bar(151.7), bar(302.8 - 10), bar(null))))
        assertEquals(PowerAxis(400), powerAxis(listOf(bar(302.8))))
        assertEquals(PowerAxis(100), powerAxis(listOf(bar(0.0))))
        assertNull(powerAxis(listOf(bar(null))))
    }

    @Test
    fun `REQ-28 power is estimated for a 75 kg rider on a 10 kg bike until the user enters their weights`() {
        assertEquals(75, Settings().riderKg)
        assertEquals(10, Settings().bikeKg)
        assertEquals(85.0, Settings().cyclingKg, 0.0)
        assertEquals(92.0, Settings(riderKg = 80, bikeKg = 12).cyclingKg, 0.0)
    }

    @Test
    fun `REQ-28 only a whole number of kg within the range is taken as a weight`() {
        assertEquals(82, parseWeightKg(" 82 ", RIDER_KG_RANGE))
        assertNull(parseWeightKg("", RIDER_KG_RANGE))
        assertNull(parseWeightKg("8", RIDER_KG_RANGE))
        assertNull(parseWeightKg("82.5", RIDER_KG_RANGE))
        assertNull(parseWeightKg("250", RIDER_KG_RANGE))
        assertEquals(8, parseWeightKg("8", BIKE_KG_RANGE))
    }
}
