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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Free OpenStreetMap-based vector map (OpenMapTiles schema), no API key needed. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SINGLE_POSITION_ZOOM = 17.0
private const val BOUNDS_PADDING_PX = 80
private const val ROUTE_SOURCE = "route"
private const val POSITION_SOURCE = "position"
private val ROUTE_COLOR = Color.rgb(0x1E, 0x88, 0xE5)

/** REQ-7: OpenStreetMap view of the path taken so far, one line per segment so paused gaps are not drawn. */
@Composable
fun RouteMapScreen(track: List<List<LocationFix>>, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val mapView = remember { createMapView(context) }
    val currentTrack by rememberUpdatedState(track)
    var style by remember { mutableStateOf<Style?>(null) }

    // MapLibre needs the host lifecycle forwarded; the observer replays the events up to the current state.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        // Zoom to the route once; later updates only redraw it so the user can pan and zoom freely.
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { loaded ->
                addRouteLayers(loaded)
                zoomToRoute(map, currentTrack)
                style = loaded
            }
        }
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(style, track) {
        style?.let { drawRoute(it, track) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Button(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) { Text("Back") }
    }
}

private fun createMapView(context: Context): MapView {
    MapLibre.getInstance(context)
    return MapView(context)
}

private fun addRouteLayers(style: Style) {
    style.addSource(GeoJsonSource(ROUTE_SOURCE))
    style.addLayer(
        LineLayer("route-line", ROUTE_SOURCE).withProperties(
            lineColor(ROUTE_COLOR),
            lineWidth(5f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    style.addSource(GeoJsonSource(POSITION_SOURCE))
    style.addLayer(
        CircleLayer("position-marker", POSITION_SOURCE).withProperties(
            circleColor(ROUTE_COLOR),
            circleRadius(8f),
            circleStrokeColor(Color.WHITE),
            circleStrokeWidth(3f),
        )
    )
}

private fun drawRoute(style: Style, track: List<List<LocationFix>>) {
    val segments = track.filter { it.size >= 2 }.map { segment ->
        Feature.fromGeometry(LineString.fromLngLats(segment.map { it.toPoint() }))
    }
    style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(segments))
    val position = listOfNotNull(lastPosition(track)?.let { Feature.fromGeometry(it.toPoint()) })
    style.getSourceAs<GeoJsonSource>(POSITION_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(position))
}

private fun zoomToRoute(map: MapLibreMap, track: List<List<LocationFix>>) {
    val bounds = routeBounds(track) ?: return
    if (bounds.north == bounds.south && bounds.east == bounds.west) {
        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(bounds.north, bounds.east))
            .zoom(SINGLE_POSITION_ZOOM)
            .build()
    } else {
        val box = LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west)
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(box, BOUNDS_PADDING_PX))
        // A route of a few metres would otherwise zoom in far beyond street level.
        if (map.cameraPosition.zoom > SINGLE_POSITION_ZOOM) {
            map.moveCamera(CameraUpdateFactory.zoomTo(SINGLE_POSITION_ZOOM))
        }
    }
}

private fun LocationFix.toPoint() = Point.fromLngLat(longitude, latitude)
