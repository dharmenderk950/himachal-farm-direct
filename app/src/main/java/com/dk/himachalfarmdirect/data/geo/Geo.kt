package com.dk.himachalfarmdirect.data.geo

import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import kotlin.math.roundToInt

/** Simple lat/lng holder used across the app (never persisted for customers in public docs). */
data class LatLngPoint(val lat: Double, val lng: Double)

object Geo {
    /** Radii offered to the buyer (spec A-7). Overridable through Remote Config. */
    val DEFAULT_RADII_KM = listOf(5, 10, 20, 50)

    fun hash(p: LatLngPoint, precision: Int = 10): String =
        GeoFireUtils.getGeoHashForLocation(GeoLocation(p.lat, p.lng), precision)

    /** ~5 km cell — the only location detail ever stored about a customer outside their private docs. */
    fun coarseHash(p: LatLngPoint): String = hash(p, 5)

    fun distanceKm(a: LatLngPoint, b: LatLngPoint): Double =
        GeoFireUtils.getDistanceBetween(GeoLocation(a.lat, a.lng), GeoLocation(b.lat, b.lng)) / 1000.0

    /** Geohash range bounds covering a circle; each becomes one Firestore range query. */
    fun queryBounds(center: LatLngPoint, radiusKm: Double) =
        GeoFireUtils.getGeoHashQueryBounds(GeoLocation(center.lat, center.lng), radiusKm * 1000.0)

    fun formatKm(km: Double): String = when {
        km < 1 -> "${(km * 1000).roundToInt()} m"
        km < 10 -> "%.1f km".format(km)
        else -> "${km.roundToInt()} km"
    }
}
