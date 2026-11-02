package com.dk.himachalfarmdirect.ui.common

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dk.himachalfarmdirect.data.geo.Geo
import com.dk.himachalfarmdirect.data.geo.LatLngPoint
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/** Initial view only (Mandi district). Never used as a result or a saved location. */
private val MANDI = GeoPoint(31.708, 76.932)

/**
 * Free OpenStreetMap with a fixed centre pin — no API key or billing account needed.
 * The user drags the map under the pin; [onUserMoved] fires with the new centre only for
 * user drags, not when [center] is changed by GPS/search.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun PinMap(
    center: LatLngPoint?,
    onUserMoved: (LatLngPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val onMoved by rememberUpdatedState(onUserMoved)
    val touched = remember { booleanArrayOf(false) }

    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)
            isTilesScaledToDpi = true
            minZoomLevel = 5.0
            maxZoomLevel = 19.0
            controller.setZoom(if (center != null) 15.0 else 9.0)
            controller.setCenter(center?.let { GeoPoint(it.lat, it.lng) } ?: MANDI)
            // Keep the page from scrolling while the finger is on the map.
            setOnTouchListener { v, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { touched[0] = true; v.parent?.requestDisallowInterceptTouchEvent(true) }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            addMapListener(DelayedMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    if (touched[0]) {
                        touched[0] = false
                        val c = mapCenter
                        onMoved(LatLngPoint(c.latitude, c.longitude))
                    }
                    return true
                }
                override fun onZoom(event: ZoomEvent?): Boolean = false
            }, 500))
        }
    }

    // Follow GPS / search / saved-address changes.
    LaunchedEffect(center) {
        center ?: return@LaunchedEffect
        val c = map.mapCenter
        if (Geo.distanceKm(LatLngPoint(c.latitude, c.longitude), center) > 0.02) {
            if (map.zoomLevelDouble < 13.0) map.controller.setZoom(15.0)
            map.controller.animateTo(GeoPoint(center.lat, center.lng))
        }
    }

    DisposableEffect(Unit) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }

    Box(modifier) {
        AndroidView(factory = { map }, modifier = Modifier.matchParentSize())
        Icon(Icons.Default.LocationOn, "Selected point", tint = FarmColors.Danger,
            modifier = Modifier.size(44.dp).align(Alignment.Center).offset(y = (-20).dp))
    }
}
