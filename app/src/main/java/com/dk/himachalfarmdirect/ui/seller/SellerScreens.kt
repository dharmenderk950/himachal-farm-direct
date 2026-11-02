package com.dk.himachalfarmdirect.ui.seller

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
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
import com.dk.himachalfarmdirect.data.model.Category
import com.dk.himachalfarmdirect.data.model.CollectionCentre
import com.dk.himachalfarmdirect.data.model.Farmer
import com.dk.himachalfarmdirect.data.model.Product
import com.dk.himachalfarmdirect.data.repo.MarketRepository
import com.dk.himachalfarmdirect.data.repo.Nearby
import com.dk.himachalfarmdirect.data.repo.OrderRepository
import com.dk.himachalfarmdirect.data.repo.SellerRepository
import com.dk.himachalfarmdirect.data.repo.UserRepository
import com.dk.himachalfarmdirect.ui.common.BackBar
import com.dk.himachalfarmdirect.ui.common.EmptyState
import com.dk.himachalfarmdirect.ui.common.rupees
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import com.dk.himachalfarmdirect.util.friendly
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ------------------------------------------------------------------ Seller dashboard

@HiltViewModel
class SellerViewModel @Inject constructor(
    private val seller: SellerRepository,
    orders: OrderRepository,
) : ViewModel() {
    val products = seller.myProducts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val orders = orders.farmOrders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow<String?>(null)
    fun setStock(p: Product, stock: Double) = viewModelScope.launch {
        runCatching { seller.updateStock(p.id, stock.coerceAtLeast(0.0)) }.onFailure { message.value = it.friendly() }
    }
}

@Composable
fun SellerHomeScreen(openProducts: () -> Unit, openOrders: () -> Unit, openFarmProfile: () -> Unit, openHelp: () -> Unit) {
    val vm: SellerViewModel = hiltViewModel()
    val products by vm.products.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val pending = orders.count { it.status in setOf("PLACED", "ACCEPTED", "PACKED", "OUT_FOR_DELIVERY", "READY_FOR_PICKUP") }
    val delivered = orders.filter { it.status == "DELIVERED" }
    val lowStock = products.filter { it.active && it.stock in 0.0..2.0 }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Seller dashboard", style = MaterialTheme.typography.headlineSmall)
        Text("You're in Seller mode. Switch to Buyer mode from Account to shop.", color = FarmColors.Muted)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Orders to fulfil", "$pending", Modifier.weight(1f), openOrders)
            Stat("Active products", "${products.count { it.active }}", Modifier.weight(1f), openProducts)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Delivered orders", "${delivered.size}", Modifier.weight(1f), openOrders)
            Stat("Sales (gross)", rupees(delivered.sumOf { it.itemsTotal }), Modifier.weight(1f), openHelp)
        }
        Text("Commission and settlement details are in Help Centre → Seller help.", color = FarmColors.Muted, modifier = Modifier.padding(top = 8.dp))
        if (lowStock.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Low stock", style = MaterialTheme.typography.titleMedium, color = FarmColors.Danger)
            lowStock.forEach { Text("• ${it.name}: ${it.stock} ${it.unit} left") }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = openProducts, Modifier.fillMaxWidth().height(52.dp)) { Text("Manage products & stock") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = openFarmProfile, Modifier.fillMaxWidth().height(52.dp)) { Text("Farm location & delivery areas") }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(FarmColors.Mint).clickable(onClick = onClick).padding(16.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = FarmColors.Forest)
        Text(label, color = FarmColors.Muted)
    }
}

@Composable
fun MyProductsScreen(openEdit: (String?) -> Unit) {
    val vm: SellerViewModel = hiltViewModel()
    val products by vm.products.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { openEdit(null) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add product") })
        },
    ) { pad ->
        Column(Modifier.padding(pad).statusBarsPadding()) {
            Text("My products", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
            msg?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = FarmColors.Danger) }
            if (products.isEmpty()) EmptyState("🥕", "No products yet", "Add what you grow — buyers nearby will see it.")
            LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(products, key = { it.id }) { p ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, FarmColors.Line, RoundedCornerShape(14.dp)).clickable { openEdit(p.id) }.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${Category.from(p.category).emoji} ${p.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(if (p.active) "Live" else "Hidden", color = if (p.active) FarmColors.Leaf else FarmColors.Muted)
                        }
                        Text("${rupees(p.price)}/${p.unit} · ${if (p.deliveryAvailable) "delivery" else "pickup only"}", color = FarmColors.Muted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Stock: ${p.stock} ${p.unit}", Modifier.weight(1f))
                            TextButton(onClick = { vm.setStock(p, p.stock - 1) }) { Text("−1") }
                            TextButton(onClick = { vm.setStock(p, p.stock + 1) }) { Text("+1") }
                            TextButton(onClick = { vm.setStock(p, p.stock + 10) }) { Text("+10") }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Add / edit product

@HiltViewModel
class ProductEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val seller: SellerRepository,
    private val market: MarketRepository,
) : ViewModel() {
    private val id: String? = handle.get<String>("productId")?.takeIf { it != "new" }
    val product = MutableStateFlow(Product())
    val saving = MutableStateFlow(false)
    val result = MutableStateFlow<String?>(null)
    val saved = MutableStateFlow(false)

    init { id?.let { viewModelScope.launch { market.product(it)?.let { p -> product.value = p } } } }

    fun update(f: (Product) -> Product) = product.update(f)

    fun save() = viewModelScope.launch {
        val p = product.value
        when {
            p.name.isBlank() -> { result.value = "Enter the product name"; return@launch }
            p.price <= 0 -> { result.value = "Enter a price"; return@launch }
        }
        saving.value = true
        runCatching { seller.saveProduct(p) }
            .onSuccess { saved.value = true }
            .onFailure { result.value = it.friendly() }
        saving.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductEditScreen(onBack: () -> Unit) {
    val vm: ProductEditViewModel = hiltViewModel()
    val p by vm.product.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val msg by vm.result.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var catOpen by remember { mutableStateOf(false) }
    var priceText by remember(p.id) { mutableStateOf(if (p.price > 0) p.price.toString() else "") }
    var mrpText by remember(p.id) { mutableStateOf(if (p.mrp > 0) p.mrp.toString() else "") }
    var stockText by remember(p.id) { mutableStateOf(if (p.stock > 0) p.stock.toString() else "") }
    LaunchedEffect(saved) { if (saved) onBack() }

    Scaffold(topBar = { BackBar(if (p.id.isBlank()) "Add product" else "Edit product", onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(p.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Product name (English)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(p.nameHindi, { v -> vm.update { it.copy(nameHindi = v) } }, label = { Text("Name in Hindi (optional)") }, modifier = Modifier.fillMaxWidth())
            ExposedDropdownMenuBox(catOpen, { catOpen = it }) {
                OutlinedTextField(
                    Category.from(p.category).label, {}, readOnly = true, label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(catOpen, { catOpen = false }) {
                    Category.entries.forEach { c ->
                        DropdownMenuItem(text = { Text("${c.emoji} ${c.label}") }, onClick = { vm.update { it.copy(category = c.id) }; catOpen = false })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("kg", "dozen", "piece", "litre", "500 g").forEach { u ->
                    androidx.compose.material3.FilterChip(p.unit == u, { vm.update { it.copy(unit = u) } }, label = { Text(u) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(priceText, { priceText = it; vm.update { x -> x.copy(price = it.toDoubleOrNull() ?: 0.0) } },
                    label = { Text("Your price ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(mrpText, { mrpText = it; vm.update { x -> x.copy(mrp = it.toDoubleOrNull() ?: 0.0) } },
                    label = { Text("Market price ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            OutlinedTextField(stockText, { stockText = it; vm.update { x -> x.copy(stock = it.toDoubleOrNull() ?: 0.0) } },
                label = { Text("Stock available (${p.unit})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(p.description, { v -> vm.update { it.copy(description = v) } }, label = { Text("Description (variety, how grown…)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            SwitchRow("I can deliver this product", p.deliveryAvailable) { v -> vm.update { it.copy(deliveryAvailable = v) } }
            SwitchRow("Seasonal product", p.seasonal) { v -> vm.update { it.copy(seasonal = v) } }
            SwitchRow("Show to buyers (live)", p.active) { v -> vm.update { it.copy(active = v) } }
            Text("Photos: add a photo URL from the admin panel for now; in-app upload uses Firebase Storage (see README).",
                color = FarmColors.Muted, style = MaterialTheme.typography.bodyMedium)
            msg?.let { Text(it, color = FarmColors.Danger) }
            Button(onClick = vm::save, enabled = !saving, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (saving) "Saving…" else "Save product") }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChange)
    }
}

// ------------------------------------------------------------------ Farm registration & service areas

@HiltViewModel
class FarmProfileViewModel @Inject constructor(
    private val users: UserRepository,
    private val loc: LocationRepository,
    private val market: MarketRepository,
) : ViewModel() {
    val farm = MutableStateFlow(Farmer())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val saved = MutableStateFlow(false)
    val centres = MutableStateFlow<List<Nearby<CollectionCentre>>>(emptyList())
    val profileName = users.profile.map { it?.name.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        viewModelScope.launch {
            users.myFarm()?.let { farm.value = it }
            if (farm.value.lat != 0.0) loadCentres()
        }
    }

    fun update(f: (Farmer) -> Farmer) = farm.update(f)

    fun useGps() = viewModelScope.launch {
        busy.value = true
        when (val r = loc.currentLocation()) {
            is LocationResult.Success -> setPoint(r.point)
            is LocationResult.GpsDisabled -> message.value = "Turn on your phone's location (GPS), or move the map to your farm."
            LocationResult.PermissionDenied -> message.value = "Location permission not given — move the map to your farm instead."
            LocationResult.Unavailable -> message.value = "No GPS signal. Move the map so the pin is on your farm."
        }
        busy.value = false
    }

    fun setPoint(p: LatLngPoint) = viewModelScope.launch {
        farm.update { it.copy(lat = p.lat, lng = p.lng) }
        loc.reverseGeocode(p)?.let { a ->
            farm.update {
                it.copy(
                    village = it.village.ifBlank { a.subLocality ?: a.locality ?: "" },
                    district = it.district.ifBlank { a.subAdminArea ?: "" },
                    pinCode = it.pinCode.ifBlank { a.postalCode ?: "" },
                )
            }
        }
        loadCentres()
    }

    private suspend fun loadCentres() {
        val f = farm.value
        centres.value = runCatching { market.collectionCentres(LatLngPoint(f.lat, f.lng), 40.0) }.getOrDefault(emptyList())
    }

    fun save() = viewModelScope.launch {
        val f = farm.value
        message.value = when {
            f.lat == 0.0 -> "Set your farm location on the map"
            f.farmName.isBlank() && f.name.isBlank() -> "Enter your name or farm name"
            f.village.isBlank() -> "Enter your village"
            f.pinCode.length != 6 -> "Enter the 6-digit PIN code of your farm"
            f.deliveryAvailable && f.deliveryRadiusKm <= 0 -> "Enter how far you can deliver (km)"
            else -> null
        }
        if (message.value != null) return@launch
        busy.value = true
        runCatching { users.saveFarmProfile(f.copy(name = f.name.ifBlank { profileName.value })) }
            .onSuccess { saved.value = true }
            .onFailure { message.value = it.friendly() }
        busy.value = false
    }
}

@Composable
fun FarmProfileScreen(onBack: () -> Unit) {
    val vm: FarmProfileViewModel = hiltViewModel()
    val f by vm.farm.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val centres by vm.centres.collectAsStateWithLifecycle()
    var pins by remember(f.id) { mutableStateOf(f.servicePinCodes.joinToString(", ")) }
    var villages by remember(f.id) { mutableStateOf(f.serviceVillages.joinToString(", ")) }
    var radiusText by remember(f.id) { mutableStateOf(if (f.deliveryRadiusKm > 0) f.deliveryRadiusKm.toInt().toString() else "") }
    LaunchedEffect(saved) { if (saved) onBack() }

    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(31.708, 76.932), 9f) }
    LaunchedEffect(f.lat, f.lng) {
        if (f.lat != 0.0 && camera.cameraMoveStartedReason != CameraMoveStartedReason.GESTURE)
            camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(f.lat, f.lng), 15f))
    }
    LaunchedEffect(camera.isMoving) {
        if (!camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE)
            vm.setPoint(LatLngPoint(camera.position.target.latitude, camera.position.target.longitude))
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.useGps() }

    Scaffold(topBar = { BackBar("Farm details", onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Buyers see your farm's village and approximate distance. Your phone number stays private.", color = FarmColors.Muted)
            OutlinedTextField(f.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.farmName, { v -> vm.update { it.copy(farmName = v) } }, label = { Text("Farm name") }, modifier = Modifier.fillMaxWidth())

            Text("Farm location", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = {
                perm.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.MyLocation, null); Spacer(Modifier.width(8.dp)); Text("I'm at my farm — use GPS")
            }
            Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp))) {
                GoogleMap(Modifier.fillMaxSize(), cameraPositionState = camera,
                    uiSettings = MapUiSettings(zoomControlsEnabled = true, mapToolbarEnabled = false))
                Icon(Icons.Default.LocationOn, "Farm", tint = FarmColors.Danger, modifier = Modifier.size(44.dp).align(Alignment.Center).offset(y = (-20).dp))
            }
            Text("Or drag the map so the pin is on your farm.", color = FarmColors.Muted, style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(f.village, { v -> vm.update { it.copy(village = v) } }, label = { Text("Village") }, modifier = Modifier.weight(1f))
                OutlinedTextField(f.pinCode, { v -> vm.update { it.copy(pinCode = v.filter(Char::isDigit).take(6)) } }, label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(0.7f))
            }
            OutlinedTextField(f.district, { v -> vm.update { it.copy(district = v) } }, label = { Text("District") }, modifier = Modifier.fillMaxWidth())

            Text("Delivery & service areas", style = MaterialTheme.typography.titleMedium)
            SwitchRow("I can deliver to buyers", f.deliveryAvailable) { v -> vm.update { it.copy(deliveryAvailable = v) } }
            if (f.deliveryAvailable) {
                OutlinedTextField(radiusText, { radiusText = it; vm.update { x -> x.copy(deliveryRadiusKm = it.toDoubleOrNull() ?: 0.0) } },
                    label = { Text("Maximum delivery distance (km)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pins, { pins = it; vm.update { x -> x.copy(servicePinCodes = it.split(",", " ").map(String::trim).filter { p -> p.length == 6 }) } },
                    label = { Text("PIN codes you deliver to (comma separated)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(villages, { villages = it; vm.update { x -> x.copy(serviceVillages = it.split(",").map(String::trim).filter(String::isNotBlank)) } },
                    label = { Text("Villages you deliver to (comma separated)") }, modifier = Modifier.fillMaxWidth())
                Text("A buyer must be inside BOTH your distance AND one of your PIN codes or villages. Leave PIN and villages empty to use distance only.",
                    color = FarmColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            if (centres.isNotEmpty()) {
                Text("Collection centres you'll drop orders at", style = MaterialTheme.typography.titleMedium)
                centres.forEach { (c, d) ->
                    Row(Modifier.fillMaxWidth().clickable {
                        vm.update { it.copy(collectionCentreIds = if (c.id in it.collectionCentreIds) it.collectionCentreIds - c.id else it.collectionCentreIds + c.id) }
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(c.id in f.collectionCentreIds, null)
                        Text("${c.name}, ${c.village} · ${"%.0f".format(d)} km")
                    }
                }
            }
            msg?.let { Text(it, color = FarmColors.Danger) }
            Button(onClick = vm::save, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (f.approved) "Save changes" else "Submit for verification")
            }
        }
    }
}
