package com.dk.himachalfarmdirect.ui.location

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dk.himachalfarmdirect.data.geo.LatLngPoint
import com.dk.himachalfarmdirect.data.model.Address
import com.dk.himachalfarmdirect.ui.common.BackBar
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState

/** Initial map view only (Mandi district). Never used as a result or saved location. */
private val MANDI = LatLng(31.708, 76.932)

@Composable
fun LocationScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val vm: LocationViewModel = hiltViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val saved by vm.addresses.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as Activity

    var showRationale by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var saveAddress by remember { mutableStateOf(false) }
    var addrLabel by remember { mutableStateOf("Home") }
    var addrLine by remember { mutableStateOf("") }

    LaunchedEffect(ui.done) { if (ui.done) onDone() }

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(ui.pin?.let { LatLng(it.lat, it.lng) } ?: MANDI, if (ui.pin != null) 14f else 9f)
    }
    // Follow the pin when GPS / search / saved address moves it.
    LaunchedEffect(ui.pin) {
        ui.pin?.let { p ->
            val target = LatLng(p.lat, p.lng)
            if (camera.position.target != target) camera.animate(CameraUpdateFactory.newLatLngZoom(target, 14f))
        }
    }
    // When the user drags the map, the centre becomes the new pin.
    LaunchedEffect(camera.isMoving) {
        if (!camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            vm.movePin(LatLngPoint(camera.position.target.latitude, camera.position.target.longitude))
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val granted = res.values.any { it }
        vm.onPermissionResult(granted, ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val gpsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.gpsResolutionHandled()
        if (r.resultCode == Activity.RESULT_OK) vm.useGps()
    }
    LaunchedEffect(ui.needsGpsResolution) {
        ui.needsGpsResolution?.let { gpsLauncher.launch(IntentSenderRequest.Builder(it.resolution).build()) }
    }

    // Spec A-1/A-2: permission is requested only on tap, after a plain-language explanation.
    if (showRationale) AlertDialog(
        onDismissRequest = { showRationale = false },
        icon = { Icon(Icons.Default.LocationOn, null, tint = FarmColors.Leaf) },
        title = { Text("Why we need your location") },
        text = {
            Text("We use your location only to show farmers and fresh produce near you and to check if they can deliver to you.\n\n" +
                "Your exact location is never shown to farmers or other users. You can also skip this and choose your village yourself.")
        },
        confirmButton = {
            Button(onClick = {
                showRationale = false
                permLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }) { Text("Allow location") }
        },
        dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Not now") } },
    )

    Scaffold(topBar = { BackBar("Choose delivery location", onBack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Column(Modifier.padding(16.dp)) {
                    Button(
                        onClick = { if (vm.hasPermission()) vm.useGps() else showRationale = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(8.dp)); Text("Use my current location")
                    }
                    if (ui.permissionPermanentlyDenied) {
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        }) { Text("Open app settings to allow location") }
                    }
                    ui.message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = FarmColors.Soil, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        label = { Text("Search village, town or PIN code") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(ui.searchResults) { a ->
                Row(Modifier.fillMaxWidth().clickable { vm.pickSearchResult(a) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Icon(Icons.Default.LocationOn, null, tint = FarmColors.Leaf)
                    Spacer(Modifier.width(8.dp))
                    Text(a.getAddressLine(0) ?: listOfNotNull(a.locality, a.postalCode).joinToString(", "))
                }
                HorizontalDivider()
            }
            item {
                Text("Or move the map so the pin is on your place", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium, color = FarmColors.Muted)
                Box(Modifier.fillMaxWidth().height(280.dp).padding(horizontal = 16.dp)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = camera,
                        properties = MapProperties(isMyLocationEnabled = vm.hasPermission()),
                        uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = false, mapToolbarEnabled = false),
                    )
                    Icon(Icons.Default.LocationOn, "Selected point", tint = FarmColors.Danger,
                        modifier = Modifier.size(44.dp).align(Alignment.Center).offset(y = (-20).dp))
                }
                if (ui.pin != null) Column(Modifier.padding(16.dp)) {
                    Text(ui.pinLabel.ifBlank { "Selected point" }, style = MaterialTheme.typography.titleMedium)
                    if (ui.pinPin.isNotBlank()) Text("PIN ${ui.pinPin}", color = FarmColors.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(saveAddress, { saveAddress = it })
                        Text("Save as a delivery address")
                    }
                    if (saveAddress) {
                        OutlinedTextField(addrLabel, { addrLabel = it }, label = { Text("Name (Home, Shop…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(addrLine, { addrLine = it }, label = { Text("House / landmark (private)") }, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            vm.confirm(if (saveAddress) Address(label = addrLabel, line = addrLine, defaultAddress = saved.isEmpty()) else null)
                        },
                        enabled = !ui.busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Confirm location") }
                }
            }
            if (saved.isNotEmpty()) {
                item { Text("Saved addresses", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium) }
                items(saved) { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.pickSaved(a) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Home, null, tint = FarmColors.Leaf)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(a.label, style = MaterialTheme.typography.titleMedium)
                            Text(listOf(a.village, a.pinCode).filter { it.isNotBlank() }.joinToString(" · "), color = FarmColors.Muted)
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = vm::browseWithoutLocation, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Browse without location")
                    }
                    Text("You'll see all products by category. You can turn location on later from Account.",
                        style = MaterialTheme.typography.bodyMedium, color = FarmColors.Muted,
                        modifier = Modifier.padding(top = 6.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}
