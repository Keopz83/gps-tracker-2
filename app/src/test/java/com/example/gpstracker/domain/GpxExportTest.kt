package com.example.gpstracker.domain

import java.time.ZoneId
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class GpxExportTest {
    private val clock = FakeClock()

    @Test
    fun `REQ-8 a run without recorded positions is not exported`() {
        val session = TrackingSession(ActivityType.JOGGING, clock::now).apply { start() }
        session.pause()
        session.resume()
        session.stop()

        assertNull(toGpx(ActivityType.JOGGING, session.stats().track))
    }

    @Test
    fun `REQ-8 a run is exported as a GPX 1_1 track with every recorded position and its UTC time`() {
        val track = listOf(listOf(LocationFix(52.0, 4.0, 5f, 1_793_000_000_000), LocationFix(52.0001234, 4.0005678, 5f, 1_793_000_005_000)))

        val gpx = parse(toGpx(ActivityType.JOGGING, track)!!)

        val root = gpx.documentElement
        assertEquals("gpx", root.localName)
        assertEquals("http://www.topografix.com/GPX/1/1", root.namespaceURI)
        assertEquals("1.1", root.getAttribute("version"))
        val points = elements(gpx, "trkpt")
        assertEquals(2, points.size)
        assertEquals(52.0001234, points[1].getAttribute("lat").toDouble(), 1e-9)
        assertEquals(4.0005678, points[1].getAttribute("lon").toDouble(), 1e-9)
        assertEquals("2026-10-26T07:33:25Z", points[1].getElementsByTagNameNS("*", "time").item(0).textContent)
        assertEquals("2026-10-26T07:33:20Z", elements(gpx, "metadata")[0].textContent)
    }

    @Test
    fun `REQ-8 the activity type is exported so other apps recognise jogging and cycling`() {
        val track = listOf(listOf(fixAt(0.0, 0)))

        assertEquals("running", elements(parse(toGpx(ActivityType.JOGGING, track)!!), "type")[0].textContent)
        assertEquals("cycling", elements(parse(toGpx(ActivityType.CYCLING, track)!!), "type")[0].textContent)
    }

    @Test
    fun `REQ-8 each segment between pauses is exported as its own track segment`() {
        val session = TrackingSession(ActivityType.CYCLING, clock::now).apply { start() }
        session.onLocation(fixAt(0.0, 0))
        session.onLocation(fixAt(100.0, 10))
        session.pause()
        session.resume()
        session.onLocation(fixAt(1_000.0, 200))
        session.stop()

        val segments = elements(parse(toGpx(ActivityType.CYCLING, session.stats().track)!!), "trkseg")

        assertEquals(listOf(2, 1), segments.map { it.getElementsByTagNameNS("*", "trkpt").length })
    }

    @Test
    fun `REQ-8 a segment left empty by a pause is not exported`() {
        val track = listOf(listOf(fixAt(0.0, 0)), emptyList(), listOf(fixAt(100.0, 60)))

        assertEquals(2, elements(parse(toGpx(ActivityType.JOGGING, track)!!), "trkseg").size)
    }

    @Test
    fun `REQ-8 the file name is sortable by start time and names the activity`() {
        assertEquals(
            "2026-10-26_0833_jogging.gpx",
            gpxFileName(ActivityType.JOGGING, 1_793_000_000_000, ZoneId.of("Europe/Amsterdam")),
        )
    }

    private fun parse(xml: String): Document = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(xml.byteInputStream())

    private fun elements(document: Document, name: String): List<Element> {
        val nodes = document.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
}
