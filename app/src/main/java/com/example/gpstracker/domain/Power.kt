package com.example.gpstracker.domain

/** REQ-28: one bar of the power chart: the active time it covers (pauses left out) and the estimated power, if moving. */
data class PowerBar(val startMillis: Long, val endMillis: Long, val watts: Double?)

private const val GRAVITY = 9.81
private const val AIR_DENSITY = 1.225 // kg/m³, at sea level and 15 °C
private const val DRAG_AREA = 0.32 // CdA in m², a road bike ridden on the hoods
private const val ROLLING_RESISTANCE = 0.005 // Crr, road tyres on asphalt
private const val DRIVETRAIN_EFFICIENCY = 0.976

/**
 * REQ-28: the power needed to ride at [speedMps] while climbing [riseMps] metres per second with a [massKg] rider and
 * bike: rolling resistance, air drag (in still air) and climbing, divided by the drivetrain's efficiency. Riding
 * downhill can need less than nothing; that is coasting, so 0 W.
 */
fun ridingWatts(speedMps: Double, riseMps: Double, massKg: Double): Double {
    val rolling = ROLLING_RESISTANCE * massKg * GRAVITY * speedMps
    val drag = 0.5 * AIR_DENSITY * DRAG_AREA * speedMps * speedMps * speedMps
    val climbing = massKg * GRAVITY * riseMps
    return maxOf(0.0, (rolling + drag + climbing) / DRIVETRAIN_EFFICIENCY)
}

/**
 * REQ-28: the estimated power of each bar of a cycling run's [track] (the same bars as the speed chart, REQ-23), from
 * its average speed and climb with a rider and bike of [massKg]. A bar covering less than 10 m has no power.
 */
fun powerBars(track: List<List<LocationFix>>, massKg: Double): List<PowerBar> = stretches(track).map { stretch ->
    val watts = if (stretch.meters >= MIN_BAR_METERS && stretch.movingMillis > 0) {
        val seconds = stretch.movingMillis / 1000.0
        ridingWatts(stretch.meters / seconds, stretch.riseMeters / seconds, massKg)
    } else {
        null
    }
    PowerBar(stretch.startMillis, stretch.endMillis, watts)
}

/** REQ-28: the power chart's y axis, from 0 W at the bottom to [topWatts] at the top. */
data class PowerAxis(val topWatts: Int)

/** REQ-28: the power chart's y axis: its top is the highest power rounded up to 100 W. Null without any power. */
fun powerAxis(bars: List<PowerBar>): PowerAxis? {
    val highest = bars.mapNotNull { it.watts }.maxOrNull() ?: return null
    return PowerAxis(maxOf(100, (Math.ceil(highest / 100) * 100).toInt()))
}
