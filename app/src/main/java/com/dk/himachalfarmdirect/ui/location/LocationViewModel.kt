package com.dk.himachalfarmdirect.ui.location

import android.location.Address as GeoAddress
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dk.himachalfarmdirect.data.geo.LatLngPoint
import com.dk.himachalfarmdirect.data.location.LocationRepository
import com.dk.himachalfarmdirect.data.location.LocationResult
import com.dk.himachalfarmdirect.data.location.SelectedLocation
import com.dk.himachalfarmdirect.data.model.Address
import com.dk.himachalfarmdirect.data.repo.UserRepository
import com.google.android.gms.common.api.ResolvableApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationUiState(
    val busy: Boolean = false,
    val message: String? = null,
    /** Where the map pin currently sits (null until GPS/search/saved address gives one). */
    val pin: LatLngPoint? = null,
    val pinLabel: String = "",
    val pinVillage: String = "",
    val pinPin: String = "",
    val pinSource: SelectedLocation.Source = SelectedLocation.Source.MANUAL,
    val searchResults: List<GeoAddress> = emptyList(),
    val needsGpsResolution: ResolvableApiException? = null,
    val permissionPermanentlyDenied: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val loc: LocationRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LocationUiState())
    val ui: StateFlow<LocationUiState> = _ui

    val addresses = users.addresses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            loc.selected.first()?.let { s ->
                _ui.update { it.copy(pin = s.point, pinLabel = s.label, pinVillage = s.village, pinPin = s.pinCode) }
            }
        }
    }

    fun hasPermission() = loc.hasPermission()

    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        if (granted) useGps()
        else _ui.update {
            it.copy(
                permissionPermanentlyDenied = !shouldShowRationale,
                message = "Location permission was not given. You can still choose your village on the map or search by name/PIN.",
            )
        }
    }

    fun useGps() = viewModelScope.launch {
        _ui.update { it.copy(busy = true, message = "Finding your location…", needsGpsResolution = null) }
        when (val r = loc.currentLocation()) {
            is LocationResult.Success -> movePin(r.point, SelectedLocation.Source.GPS)
            LocationResult.PermissionDenied -> _ui.update { it.copy(busy = false, message = null) }
            is LocationResult.GpsDisabled -> _ui.update {
                it.copy(busy = false, needsGpsResolution = r.resolvable,
                    message = "Your phone's location (GPS) is turned off. Turn it on, or pick your place manually.")
            }
            LocationResult.Unavailable -> _ui.update {
                it.copy(busy = false, message = "Couldn't get a GPS signal. Move to an open area or choose your place on the map.")
            }
        }
    }

    fun gpsResolutionHandled() = _ui.update { it.copy(needsGpsResolution = null) }

    /** Called when the pin moves (GPS fix, map drag, search pick). Reverse-geocodes when online. */
    fun movePin(p: LatLngPoint, source: SelectedLocation.Source = SelectedLocation.Source.MANUAL) = viewModelScope.launch {
        _ui.update { it.copy(pin = p, busy = true, pinSource = source) }
        val a = loc.reverseGeocode(p)
        val s = loc.toSelected(p, a, SelectedLocation.Source.MANUAL)
        _ui.update { it.copy(busy = false, message = null, pinLabel = s.label, pinVillage = s.village, pinPin = s.pinCode) }
    }

    fun search(q: String) = viewModelScope.launch {
        _ui.update { it.copy(busy = true) }
        val res = loc.search(q)
        _ui.update {
            it.copy(busy = false, searchResults = res,
                message = if (res.isEmpty()) "No place found. Try the village name with district, or a 6-digit PIN." else null)
        }
    }

    fun pickSearchResult(a: GeoAddress) {
        _ui.update { it.copy(searchResults = emptyList()) }
        movePin(LatLngPoint(a.latitude, a.longitude))
    }

    fun pickSaved(a: Address) = viewModelScope.launch {
        loc.save(SelectedLocation(LatLngPoint(a.lat, a.lng), "${a.label} · ${a.village}", a.village, a.pinCode, SelectedLocation.Source.SAVED_ADDRESS))
        loc.setDiscoveryEnabled(true)
        syncProfile(LatLngPoint(a.lat, a.lng))
        _ui.update { it.copy(done = true) }
    }

    /** Confirms the pin as the browsing location, optionally saving it as a delivery address. */
    fun confirm(saveAs: Address?) = viewModelScope.launch {
        val s = _ui.value
        val p = s.pin ?: return@launch
        loc.save(SelectedLocation(p, s.pinLabel, s.pinVillage, s.pinPin, s.pinSource))
        loc.setDiscoveryEnabled(true)
        if (saveAs != null) runCatching {
            users.saveAddress(saveAs.copy(lat = p.lat, lng = p.lng,
                village = saveAs.village.ifBlank { s.pinVillage }, pinCode = saveAs.pinCode.ifBlank { s.pinPin }))
        }
        syncProfile(p)
        _ui.update { it.copy(done = true) }
    }

    /** Spec A-16: browse manually with no location at all. */
    fun browseWithoutLocation() = viewModelScope.launch {
        loc.setDiscoveryEnabled(false)
        runCatching { users.updateDiscovery(enabled = false, notifyNewSellers = false, around = null) }
        _ui.update { it.copy(done = true) }
    }

    private suspend fun syncProfile(p: LatLngPoint) {
        val notify = users.profile.first()?.notifyNewSellers ?: true
        runCatching { users.updateDiscovery(enabled = true, notifyNewSellers = notify, around = p) }
    }
}
