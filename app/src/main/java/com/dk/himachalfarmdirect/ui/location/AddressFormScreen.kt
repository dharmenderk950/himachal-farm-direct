package com.dk.himachalfarmdirect.ui.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dk.himachalfarmdirect.data.geo.LatLngPoint
import com.dk.himachalfarmdirect.data.location.LocationRepository
import com.dk.himachalfarmdirect.data.location.LocationResult
import com.dk.himachalfarmdirect.data.location.SelectedLocation
import com.dk.himachalfarmdirect.data.model.Address
import com.dk.himachalfarmdirect.data.repo.UserRepository
import com.dk.himachalfarmdirect.ui.common.BackBar
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import com.dk.himachalfarmdirect.util.friendly
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddressFormViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val users: UserRepository,
    private val loc: LocationRepository,
) : ViewModel() {
    private val editId: String? = handle.get<String>("addressId")?.takeIf { it != "new" }
    val form = MutableStateFlow(Address())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val saved = MutableStateFlow(false)
    val makeDefault = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            runCatching {
                val list = users.addresses().first()
                val existing = editId?.let { id -> list.firstOrNull { it.id == id } }
                val profile = users.profile.first()
                form.value = existing ?: Address(
                    label = "Home",
                    name = profile?.name.orEmpty(),
                    phone = profile?.phone.orEmpty().removePrefix("+91"),
                )
                makeDefault.value = existing?.defaultAddress ?: list.isEmpty()
            }
        }
    }

    fun update(f: (Address) -> Address) = form.update(f)

    /** Fills village / district / PIN from GPS so the buyer types less. */
    fun fillFromGps() = viewModelScope.launch {
        busy.value = true; message.value = "Finding your location…"
        when (val r = loc.currentLocation()) {
            is LocationResult.Success -> {
                val a = runCatching { loc.reverseGeocode(r.point) }.getOrNull()
                form.update {
                    it.copy(
                        lat = r.point.lat, lng = r.point.lng,
                        village = a?.subLocality ?: a?.locality ?: it.village,
                        district = a?.subAdminArea ?: it.district,
                        pinCode = a?.postalCode ?: it.pinCode,
                    )
                }
                message.value = if (a == null) "Location found. Please type your village and PIN." else null
            }
            is LocationResult.GpsDisabled -> message.value = "Turn on your phone's location (GPS), or just type the address."
            LocationResult.PermissionDenied -> message.value = "Location permission not given — just type the address."
            LocationResult.Unavailable -> message.value = "No GPS signal — just type the address."
        }
        busy.value = false
    }

    fun save() = viewModelScope.launch {
        val a = form.value
        message.value = when {
            a.name.isBlank() -> "Enter the receiver's name"
            a.phone.filter(Char::isDigit).length != 10 -> "Enter a 10-digit mobile number"
            a.line.isBlank() -> "Enter house / street"
            a.village.isBlank() -> "Enter village or town"
            a.pinCode.length != 6 -> "Enter the 6-digit PIN code"
            else -> null
        }
        if (message.value != null) return@launch
        busy.value = true
        // No GPS point? Look the village/PIN up so distance can be worked out (best effort).
        var withPoint = a
        if (a.lat == 0.0) {
            val g = loc.search("${a.village}, ${a.district}, ${a.pinCode}").firstOrNull()
                ?: loc.search(a.pinCode).firstOrNull()
            if (g != null) withPoint = a.copy(lat = g.latitude, lng = g.longitude)
        }
        runCatching {
            users.saveAddress(withPoint.copy(phone = a.phone.filter(Char::isDigit).takeLast(10), defaultAddress = makeDefault.value))
            if (makeDefault.value && withPoint.lat != 0.0) {
                loc.save(SelectedLocation(LatLngPoint(withPoint.lat, withPoint.lng), "${withPoint.label} · ${withPoint.village}",
                    withPoint.village, withPoint.pinCode, SelectedLocation.Source.SAVED_ADDRESS))
            }
        }.onSuccess { saved.value = true }.onFailure { message.value = it.friendly() }
        busy.value = false
    }
}

/** A plain form — no map needed. Big fields, number keyboards where it helps. */
@Composable
fun AddressFormScreen(onBack: () -> Unit) {
    val vm: AddressFormViewModel = hiltViewModel()
    val a by vm.form.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val makeDefault by vm.makeDefault.collectAsStateWithLifecycle()
    LaunchedEffect(saved) { if (saved) onBack() }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.fillFromGps() }

    Scaffold(topBar = { BackBar(if (a.id.isBlank()) "New delivery address" else "Edit address", onBack) }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = { perm.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(8.dp)); Text("Fill village & PIN from my location") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Home", "Shop", "Other").forEach { l ->
                    FilterChip(a.label == l, { vm.update { it.copy(label = l) } }, label = { Text(l) })
                }
            }
            Field("Receiver's name", a.name, { v -> vm.update { it.copy(name = v) } }, cap = KeyboardCapitalization.Words)
            Field("Mobile number", a.phone, { v -> vm.update { it.copy(phone = v.filter(Char::isDigit).take(10)) } },
                type = KeyboardType.Phone, prefix = "+91 ")
            Field("House no. / street / mohalla", a.line, { v -> vm.update { it.copy(line = v) } }, cap = KeyboardCapitalization.Sentences)
            Field("Landmark (optional)", a.landmark, { v -> vm.update { it.copy(landmark = v) } }, cap = KeyboardCapitalization.Sentences)
            Field("Village / town", a.village, { v -> vm.update { it.copy(village = v) } }, cap = KeyboardCapitalization.Words)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("PIN code", a.pinCode, { v -> vm.update { it.copy(pinCode = v.filter(Char::isDigit).take(6)) } },
                    type = KeyboardType.Number, modifier = Modifier.weight(0.45f))
                Field("District", a.district, { v -> vm.update { it.copy(district = v) } },
                    cap = KeyboardCapitalization.Words, modifier = Modifier.weight(0.55f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(makeDefault, { vm.makeDefault.value = it })
                Text("Use as my main delivery address")
            }
            Text("Your phone and house details are shared only with the farmer of an order you place.",
                color = FarmColors.Muted, style = MaterialTheme.typography.bodyMedium)
            msg?.let { Text(it, color = FarmColors.Danger) }
            Button(onClick = vm::save, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Save address") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    type: KeyboardType = KeyboardType.Text,
    cap: KeyboardCapitalization = KeyboardCapitalization.None,
    prefix: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        prefix = prefix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = type, capitalization = cap, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}
