package com.example.gpstracker

import android.content.Context
import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.gpstracker.domain.LocationFix
import com.example.gpstracker.domain.lastPosition
import com.example.gpstracker.domain.routeBounds
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val SINGLE_POSITION_ZOOM = 17.0
private const val BOUNDS_PADDING_PX = 80

/** REQ-7: OpenStreetMap view of the path taken so far, one line per segment so paused gaps are not drawn. */
@Composable
fun RouteMapScreen(track: List<List<LocationFix>>, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val mapView = remember { createMapView(context) }

    // osmdroid needs the host lifecycle to start/stop tile loading and must be detached when leaving the screen.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                // Zoom to the route once; later updates only redraw it so the user can pan and zoom freely.
                mapView.addOnFirstLayoutListener { _, _, _, _, _ -> zoomToRoute(mapView, track) }
                mapView
            },
            update = { drawRoute(it, track) },
            modifier = Modifier.fillMaxSize(),
        )
        Button(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) { Text("Back") }
    }
}

private fun createMapView(context: Context): MapView {
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        userAgentValue = context.packageName // Required by the OpenStreetMap tile usage policy.
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
    }
}

private fun drawRoute(mapView: MapView, track: List<List<LocationFix>>) {
    mapView.overlays.clear()
    track.filter { it.size >= 2 }.forEach { segment ->
        mapView.overlays += Polyline(mapView).apply {
            setPoints(segment.map { it.toGeoPoint() })
            outlinePaint.color = Color.rgb(0x1E, 0x88, 0xE5)
            outlinePaint.strokeWidth = 12f
        }
    }
    lastPosition(track)?.let { position ->
        mapView.overlays += Marker(mapView).apply {
            this.position = position.toGeoPoint()
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Current position"
        }
    }
    mapView.invalidate()
}

private fun zoomToRoute(mapView: MapView, track: List<List<LocationFix>>) {
    val bounds = routeBounds(track) ?: return
    if (bounds.north == bounds.south && bounds.east == bounds.west) {
        mapView.controller.setZoom(SINGLE_POSITION_ZOOM)
        mapView.controller.setCenter(GeoPoint(bounds.north, bounds.east))
    } else {
        val box = BoundingBox(bounds.north, bounds.east, bounds.south, bounds.west)
        mapView.zoomToBoundingBox(box, false, BOUNDS_PADDING_PX)
    }
}

private fun LocationFix.toGeoPoint() = GeoPoint(latitude, longitude)
