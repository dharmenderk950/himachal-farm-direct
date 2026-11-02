package com.dk.himachalfarmdirect.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dk.himachalfarmdirect.data.geo.LatLngPoint
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private val Context.locationStore by preferencesDataStore("delivery_location")

/** The location the shopper is currently browsing for. Stored on-device only. */
data class SelectedLocation(
    val point: LatLngPoint,
    val label: String,          // e.g. "Richhani, Karsog" — shown in the home header
    val village: String,
    val pinCode: String,
    val source: Source,
) {
    enum class Source { GPS, MANUAL, SAVED_ADDRESS }
}

sealed interface LocationResult {
    data class Success(val point: LatLngPoint) : LocationResult
    data object PermissionDenied : LocationResult
    /** GPS is off; [resolvable] can be launched to show the system "turn on location" dialog. */
    data class GpsDisabled(val resolvable: ResolvableApiException?) : LocationResult
    data object Unavailable : LocationResult
}

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)
    private val settings = LocationServices.getSettingsClient(context)

    private object K {
        val LAT = doublePreferencesKey("lat")
        val LNG = doublePreferencesKey("lng")
        val LABEL = stringPreferencesKey("label")
        val VILLAGE = stringPreferencesKey("village")
        val PIN = stringPreferencesKey("pin")
        val SOURCE = stringPreferencesKey("source")
        val DISCOVERY = booleanPreferencesKey("discovery_enabled")
    }

    val selected: Flow<SelectedLocation?> = context.locationStore.data.map { p ->
        val lat = p[K.LAT] ?: return@map null
        val lng = p[K.LNG] ?: return@map null
        SelectedLocation(
            LatLngPoint(lat, lng),
            p[K.LABEL].orEmpty(), p[K.VILLAGE].orEmpty(), p[K.PIN].orEmpty(),
            runCatching { SelectedLocation.Source.valueOf(p[K.SOURCE].orEmpty()) }
                .getOrDefault(SelectedLocation.Source.MANUAL),
        )
    }

    /** Spec A-16: user may turn location-based discovery off and browse manually. */
    val discoveryEnabled: Flow<Boolean> = context.locationStore.data.map { it[K.DISCOVERY] ?: true }

    suspend fun setDiscoveryEnabled(enabled: Boolean) {
        context.locationStore.edit { it[K.DISCOVERY] = enabled }
    }

    suspend fun save(loc: SelectedLocation) {
        context.locationStore.edit {
            it[K.LAT] = loc.point.lat
            it[K.LNG] = loc.point.lng
            it[K.LABEL] = loc.label
            it[K.VILLAGE] = loc.village
            it[K.PIN] = loc.pinCode
            it[K.SOURCE] = loc.source.name
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isGpsOn(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Gets one fresh fix. Falls back to the last known location, and gives up after a
     * timeout so a weak signal in the hills never leaves the user stuck on a spinner.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied
        if (!isGpsOn()) {
            val req = LocationSettingsRequest.Builder()
                .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000).build())
                .build()
            val resolvable = try {
                settings.checkLocationSettings(req).await(); null
            } catch (e: ResolvableApiException) { e } catch (_: Exception) { null }
            return LocationResult.GpsDisabled(resolvable)
        }
        return try {
            val cts = CancellationTokenSource()
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(5 * 60_000)
                .build()
            val loc = withTimeoutOrNull(15_000) { fused.getCurrentLocation(request, cts.token).await() }
                ?: fused.lastLocation.await()
            cts.cancel()
            if (loc != null) LocationResult.Success(LatLngPoint(loc.latitude, loc.longitude))
            else LocationResult.Unavailable
        } catch (_: Exception) {
            LocationResult.Unavailable
        }
    }

    /** Reverse geocoding: point → village / PIN. Returns null offline (caller keeps the pin). */
    suspend fun reverseGeocode(p: LatLngPoint): Address? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale("en", "IN"))
        withTimeoutOrNull(8_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(p.lat, p.lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(list: MutableList<Address>) { cont.resume(list.firstOrNull()) }
                        override fun onError(errorMessage: String?) { cont.resume(null) }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(p.lat, p.lng, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }

    /** Forward geocoding: typed village / PIN → candidate points, restricted to India. */
    suspend fun search(query: String): List<Address> = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent() || query.isBlank()) return@withContext emptyList()
        val geocoder = Geocoder(context, Locale("en", "IN"))
        val q = if (query.contains("india", true)) query else "$query, India"
        withTimeoutOrNull(8_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<List<Address>> { cont ->
                    geocoder.getFromLocationName(q, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(list: MutableList<Address>) { cont.resume(list) }
                        override fun onError(errorMessage: String?) { cont.resume(emptyList()) }
                    })
                }
            } else {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocationName(q, 5) ?: emptyList() }.getOrDefault(emptyList())
            }
        } ?: emptyList()
    }

    fun toSelected(p: LatLngPoint, a: Address?, source: SelectedLocation.Source): SelectedLocation {
        val village = a?.subLocality ?: a?.locality ?: a?.featureName ?: ""
        val area = a?.subAdminArea ?: a?.locality ?: ""
        val label = listOf(village, area).filter { it.isNotBlank() }.distinct().joinToString(", ")
            .ifBlank { "%.4f, %.4f".format(p.lat, p.lng) }
        return SelectedLocation(p, label, village, a?.postalCode.orEmpty(), source)
    }
}
