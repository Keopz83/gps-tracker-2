package com.example.gpstracker

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.example.gpstracker.domain.RunRecord
import com.example.gpstracker.domain.hasRoute
import com.example.gpstracker.domain.lastPosition
import com.example.gpstracker.domain.mapSwipeStep
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

/**
 * REQ-7: OpenStreetMap view of [track], once a position has been recorded.
 * REQ-17: [run] is summarised on one line at the bottom of the map, as in the history.
 * REQ-19: a one-finger drag swipes the views: [onSwipeBy] follows the finger (pixels moved right) and [onSwipeEnd]
 * gets the number of views to move on when it is lifted.
 */
@Composable
fun RouteMapScreen(
    track: List<List<LocationFix>>,
    run: RunRecord?,
    onSwipeBy: (Float) -> Unit,
    onSwipeEnd: (Int) -> Unit,
) {
    if (!hasRoute(track) || run == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No route yet") }
        return
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // A new map for another run, so it zooms to that run.
        key(run.activityType, run.startMillis) { RouteMap(track, onSwipeBy, onSwipeEnd) }
        RunLine(
            run = run,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .screenGradientBackground()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/** REQ-7: the map itself, one line per segment so paused gaps are not drawn. */
@Composable
private fun RouteMap(track: List<List<LocationFix>>, onSwipeBy: (Float) -> Unit, onSwipeEnd: (Int) -> Unit) {
    val context = LocalContext.current
    val layout = remember { TwoFingerMapLayout(context, createMapView(context)) }
    val mapView = layout.mapView
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
            // REQ-19: a two-finger drag up or down pans the map instead of tilting it.
            map.uiSettings.isTiltGesturesEnabled = false
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

    AndroidView(factory = { layout }, modifier = Modifier.fillMaxSize()) {
        it.onSwipeBy = onSwipeBy
        it.onSwipeEnd = onSwipeEnd
    }
}

private fun createMapView(context: Context): MapView {
    MapLibre.getInstance(context)
    return MapView(context)
}

/**
 * REQ-19: hands gestures of two or more fingers to [mapView], so it pans, rotates and zooms, and turns one-finger
 * drags into view swipes. A one-finger tap still reaches the map (double tap to zoom, the compass).
 */
private class TwoFingerMapLayout(context: Context, val mapView: MapView) : FrameLayout(context) {
    var onSwipeBy: (Float) -> Unit = {}
    var onSwipeEnd: (Int) -> Unit = {}

    private enum class Gesture { TAP, SWIPE, MAP, NONE }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    private var gesture = Gesture.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var velocity: VelocityTracker? = null

    init {
        addView(mapView)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Screen coordinates: this view moves with the finger while it swipes.
        val x = event.rawX
        val y = event.rawY
        trackVelocity(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture = Gesture.TAP
                downX = x
                downY = y
                lastX = x
                mapView.dispatchTouchEvent(event)
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (gesture == Gesture.TAP || gesture == Gesture.MAP) {
                gesture = Gesture.MAP
                mapView.dispatchTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> when (gesture) {
                Gesture.TAP -> if (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop) {
                    cancelMap(event)
                    gesture = if (Math.abs(x - downX) > Math.abs(y - downY)) Gesture.SWIPE else Gesture.NONE
                    lastX = downX
                }
                Gesture.MAP -> mapView.dispatchTouchEvent(event)
                else -> Unit
            }
            MotionEvent.ACTION_POINTER_UP -> if (gesture == Gesture.MAP) {
                mapView.dispatchTouchEvent(event)
                // Down to one finger: the map is not panned with one finger.
                if (event.pointerCount <= 2) {
                    cancelMap(event)
                    gesture = Gesture.NONE
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                when (gesture) {
                    Gesture.TAP, Gesture.MAP -> mapView.dispatchTouchEvent(event)
                    Gesture.SWIPE -> {
                        val speed = velocity?.run { computeCurrentVelocity(1000); xVelocity } ?: 0f
                        val step = if (event.actionMasked == MotionEvent.ACTION_UP) {
                            mapSwipeStep(x - downX, width.toFloat(), speed, minFlingVelocity)
                        } else {
                            0
                        }
                        onSwipeEnd(step)
                    }
                    Gesture.NONE -> Unit
                }
                gesture = Gesture.NONE
                velocity?.recycle()
                velocity = null
            }
        }
        if (gesture == Gesture.SWIPE && event.actionMasked == MotionEvent.ACTION_MOVE) {
            onSwipeBy(x - lastX)
            lastX = x
        }
        return true
    }

    private fun trackVelocity(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) velocity = VelocityTracker.obtain()
        val onScreen = MotionEvent.obtain(event).apply { setLocation(event.rawX, event.rawY) }
        velocity?.addMovement(onScreen)
        onScreen.recycle()
    }

    private fun cancelMap(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        mapView.dispatchTouchEvent(cancel)
        cancel.recycle()
    }
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
