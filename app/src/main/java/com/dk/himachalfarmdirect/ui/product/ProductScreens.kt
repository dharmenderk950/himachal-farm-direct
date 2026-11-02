package com.dk.himachalfarmdirect.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dk.himachalfarmdirect.data.geo.Geo
import com.dk.himachalfarmdirect.data.model.Category
import com.dk.himachalfarmdirect.data.model.FulfilmentMode
import com.dk.himachalfarmdirect.ui.common.BackBar
import com.dk.himachalfarmdirect.ui.common.EmptyState
import com.dk.himachalfarmdirect.ui.common.FarmerCard
import com.dk.himachalfarmdirect.ui.common.ProductCard
import com.dk.himachalfarmdirect.ui.common.SectionHeader
import com.dk.himachalfarmdirect.ui.common.Thumb
import com.dk.himachalfarmdirect.ui.common.rupees
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import kotlinx.coroutines.launch

private fun titleFor(kind: String) = when {
    kind.startsWith("cat:") -> Category.from(kind.removePrefix("cat:")).label
    kind == "search" -> "Search"
    kind == "best" -> "Best sellers"
    kind == "offers" -> "Offers & discounts"
    kind == "nearbyFarmers" -> "Farmers near you"
    else -> "Recently added"
}

@Composable
fun ProductListScreen(onBack: () -> Unit, openProduct: (String) -> Unit, openFarmer: (String) -> Unit) {
    val vm: ProductListViewModel = hiltViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var deliveryOnly by remember { mutableStateOf(vm.deliveryOnly) }
    var radius by remember { mutableStateOf(vm.radiusKm) }
    val focus = remember { FocusRequester() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (vm.kind == "search") runCatching { focus.requestFocus() } }

    Scaffold(topBar = { BackBar(titleFor(vm.kind), onBack) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.kind == "search") OutlinedTextField(
                query, { query = it }, placeholder = { Text("Product name, e.g. apple, rajma") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
            ) else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(5.0, 10.0, 20.0, 50.0)) { r ->
                        FilterChip(radius == r, { radius = r; vm.setRadius(r) }, label = { Text("${r.toInt()} km") })
                    }
                    item { FilterChip(deliveryOnly, { deliveryOnly = !deliveryOnly; vm.setDeliveryOnly(deliveryOnly) }, label = { Text("Delivery only") }) }
                }
            }
            if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let { EmptyState("📶", "Couldn't load", it, "Try again") { if (vm.kind == "search") vm.search(query) else vm.load() } }

            if (vm.kind == "nearbyFarmers") {
                if (!s.loading && s.farmers.isEmpty() && s.error == null)
                    EmptyState("🌱", "No farmers in this distance", "Try a larger distance, or turn off \"Delivery only\".")
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(s.farmers, key = { it.item.id }) { n -> FarmerCard(n.item, n.distanceKm) { openFarmer(n.item.id) } }
                }
            } else {
                if (!s.loading && s.products.isEmpty() && s.error == null && (vm.kind != "search" || query.isNotBlank()))
                    EmptyState("🧺", "Nothing here yet", "No products match right now. Try a bigger distance or another category.")
                LazyVerticalGrid(
                    GridCells.Adaptive(158.dp), contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(s.products, key = { it.first.id }) { (p, d) ->
                        ProductCard(p, d, onOpen = { openProduct(p.id) }, onAdd = {
                            vm.add(p); scope.launch { snack.showSnackbar("${p.name} added to cart") }
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun ProductDetailScreen(onBack: () -> Unit, openFarmer: (String) -> Unit, openProduct: (String) -> Unit, openCart: () -> Unit, openCompare: (String) -> Unit) {
    val vm: DetailViewModel = hiltViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val wish by vm.wishlistIds.collectAsStateWithLifecycle()
    var qty by remember { mutableStateOf(1.0) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val p = s.product

    Scaffold(
        topBar = {
            BackBar(p?.name ?: "Product", onBack) {
                if (p != null) {
                    val inWish = p.id in wish
                    IconButton(onClick = { vm.toggleWish(p, !inWish) }) {
                        Icon(if (inWish) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Wishlist", tint = FarmColors.Danger)
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        if (p == null) {
            Column(Modifier.padding(pad)) {
                s.error?.let { EmptyState("📶", "Couldn't load product", it, "Try again") { vm.load() } } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad)) {
            item { Thumb(p.imageUrl.ifBlank { p.thumbUrl }, Category.from(p.category).emoji, Modifier.fillMaxWidth().aspectRatio(1.4f)) }
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(p.name, style = MaterialTheme.typography.headlineSmall)
                    if (p.nameHindi.isNotBlank()) Text(p.nameHindi, color = FarmColors.Muted)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${rupees(p.price)} / ${p.unit}", style = MaterialTheme.typography.titleLarge, color = FarmColors.Forest)
                        if (p.discountPercent > 0) Text("  ${p.discountPercent}% off MRP ${rupees(p.mrp)}", color = FarmColors.Muted)
                    }
                    Text(
                        listOfNotNull(
                            "In stock: ${if (p.stock % 1.0 == 0.0) p.stock.toLong() else p.stock} ${p.unit}",
                            s.distanceKm?.let { "About ${Geo.formatKm(it)} away" },
                            if (p.deliveryAvailable) "Home delivery available" else "Pickup / collection centre",
                        ).joinToString(" · "),
                        color = FarmColors.Muted, modifier = Modifier.padding(top = 4.dp),
                    )
                    Text("Final delivery is confirmed at checkout for your address.", style = MaterialTheme.typography.bodyMedium, color = FarmColors.Muted)
                    if (p.description.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text(p.description) }
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { openCompare(p.name.split(" ").firstOrNull { it.length >= 2 } ?: p.name) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("⚖️  Compare price with other farmers") }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (qty > 1) qty -= 1 }) { Icon(Icons.Default.Remove, "Less") }
                        Text("${qty.toInt()} ${p.unit}", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { if (qty < p.stock) qty += 1 }) { Icon(Icons.Default.Add, "More") }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = {
                            vm.add(p, qty)
                            scope.launch { if (snack.showSnackbar("Added to cart", "View cart") == androidx.compose.material3.SnackbarResult.ActionPerformed) openCart() }
                        }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Add to cart · ${rupees(p.price * qty)}") }
                    }
                }
            }
            s.farmer?.let { f ->
                item { SectionHeader("Sold by") }
                item { Column(Modifier.padding(horizontal = 16.dp)) { FarmerCard(f, s.distanceKm) { openFarmer(f.id) } } }
            }
            val more = s.farmerProducts.filter { it.id != p.id }
            if (more.isNotEmpty()) {
                item { SectionHeader("More from this farmer") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(more, key = { it.id }) { m -> ProductCard(m, null, { openProduct(m.id) }, { vm.add(m) }) }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun FarmerScreen(onBack: () -> Unit, openProduct: (String) -> Unit) {
    val vm: DetailViewModel = hiltViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val f = s.farmer
    Scaffold(topBar = { BackBar(f?.farmName?.ifBlank { f.name } ?: "Farmer", onBack) }) { pad ->
        if (f == null) {
            Column(Modifier.padding(pad)) { s.error?.let { EmptyState("📶", "Couldn't load", it, "Try again") { vm.load() } } ?: LinearProgressIndicator(Modifier.fillMaxWidth()) }
            return@Scaffold
        }
        LazyVerticalGrid(
            GridCells.Adaptive(158.dp), Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(f.farmName.ifBlank { f.name }, style = MaterialTheme.typography.headlineSmall)
                    Text("${f.name} · ${f.village}, ${f.district}", color = FarmColors.Muted)
                    s.distanceKm?.let { Text("About ${Geo.formatKm(it)} from you", color = FarmColors.Leaf) }
                    Text(
                        if (f.deliveryAvailable) "Delivers up to ${f.deliveryRadiusKm.toInt()} km" +
                            (if (f.servicePinCodes.isNotEmpty()) " · PIN ${f.servicePinCodes.joinToString()}" else "")
                        else "Pickup from farm / collection centre",
                        color = FarmColors.Muted,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Products", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(s.farmerProducts, key = { it.id }) { p -> ProductCard(p, s.distanceKm, { openProduct(p.id) }, { vm.add(p) }) }
        }
    }
}

@Composable
fun CartScreen(onBack: () -> Unit, openLocation: () -> Unit, addAddress: () -> Unit, openOrders: () -> Unit) {
    val vm: CartViewModel = hiltViewModel()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val farmers by vm.farmers.collectAsStateWithLifecycle()
    val s by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(lines.map { it.farmerId }.distinct()) { vm.loadFarmers(lines.map { it.farmerId }.distinct()) }

    Scaffold(topBar = { BackBar("My cart", onBack) }) { pad ->
        if (lines.isEmpty()) {
            Column(Modifier.padding(pad)) {
                if (s.placedOrderIds.isNotEmpty()) EmptyState("✅", "Order placed!",
                    "The farmer will confirm it. You can call the seller from the Orders page.", "View my orders", openOrders)
                else EmptyState("🛒", "Your cart is empty", "Add fresh produce from farmers near you.")
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // 1) Address first — the easiest thing to get wrong.
            item {
                SectionHeader("1. Deliver to", if (addresses.isEmpty()) null else "+ New", addAddress)
                if (addresses.isEmpty()) {
                    Button(onClick = addAddress, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(54.dp)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add delivery address")
                    }
                }
                addresses.forEach { a ->
                    Row(Modifier.fillMaxWidth().clickable { vm.selectAddress(a) }.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.address?.id == a.id, { vm.selectAddress(a) })
                        Column(Modifier.weight(1f)) {
                            Text("${a.label}${if (a.name.isNotBlank()) " · " + a.name else ""}", style = MaterialTheme.typography.titleMedium)
                            Text(listOf(a.line, a.village, a.pinCode).filter { it.isNotBlank() }.joinToString(", "), color = FarmColors.Muted)
                            if (a.phone.isBlank()) Text("Add mobile number — tap to edit", color = FarmColors.Danger,
                                modifier = Modifier.clickable(onClick = addAddress))
                        }
                    }
                }
                SectionHeader("2. How do you want it?")
                listOf(
                    FulfilmentMode.HOME_DELIVERY to "🚚  Home delivery",
                    FulfilmentMode.FARM_PICKUP to "🧺  I'll pick up from the farm",
                    FulfilmentMode.COLLECTION_CENTRE to "🏪  Collection centre",
                ).forEach { (m, label) ->
                    Row(Modifier.fillMaxWidth().clickable { vm.setMode(m) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(s.mode == m, { vm.setMode(m) }); Text(label)
                    }
                }
                SectionHeader("3. Items")
            }
            // 2) One box per farmer, with the farmer's name and village.
            vm.groups(lines).forEach { (farmerId, group) ->
                val q = s.quotes[farmerId]
                val f = farmers[farmerId]
                item(key = "g_$farmerId") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .border(1.dp, FarmColors.Line, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Text("👨‍🌾 " + (f?.farmName?.ifBlank { f.name } ?: "Farmer") + (f?.village?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.titleMedium, color = FarmColors.Forest)
                        group.forEach { l ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${rupees(l.price)} / ${l.unit}  =  ${rupees(l.price * l.qty)}", color = FarmColors.Muted)
                                }
                                IconButton(onClick = { vm.setQty(l.productId, l.qty - 1) }) { Icon(if (l.qty <= 1) Icons.Default.Delete else Icons.Default.Remove, "Less") }
                                Text("${l.qty.toInt()}", style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { vm.setQty(l.productId, l.qty + 1) }) { Icon(Icons.Default.Add, "More") }
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        when {
                            s.checking -> Text("Checking delivery…", color = FarmColors.Muted)
                            q == null -> Text(if (addresses.isEmpty()) "Add an address above to check delivery" else "Choose an address above", color = FarmColors.Muted)
                            q.eligible -> Text(
                                "✓ Available" + (if (q.distanceKm > 0) " · ${Geo.formatKm(q.distanceKm)}" else "") +
                                    " · " + when { s.mode == FulfilmentMode.FARM_PICKUP -> "pickup, no charge"; q.deliveryCharge == 0.0 -> "free delivery"; else -> "delivery ${rupees(q.deliveryCharge)}" },
                                color = FarmColors.Leaf, fontWeight = FontWeight.SemiBold,
                            )
                            else -> Text("✗ " + q.reasons.joinToString("\n✗ ").ifBlank { "Not available for this address" }, color = FarmColors.Danger)
                        }
                    }
                }
            }
            // 3) Total + order.
            item {
                val eligibleTotal = vm.groups(lines).filterKeys { s.quotes[it]?.eligible == true }
                    .values.sumOf { g -> g.sumOf { it.price * it.qty } } +
                    s.quotes.values.filter { it.eligible }.sumOf { it.deliveryCharge }
                Column(Modifier.padding(16.dp).background(FarmColors.Mint, RoundedCornerShape(14.dp)).padding(16.dp)) {
                    Text("To pay: ${rupees(eligibleTotal)}", style = MaterialTheme.typography.titleLarge, color = FarmColors.Forest)
                    Text("Pay the farmer by cash or UPI when you receive it. After ordering you'll see the seller's phone number.", color = FarmColors.Muted)
                    s.message?.let { Text(it, color = FarmColors.Danger, modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = vm::placeAll,
                        enabled = !s.placing && !s.checking && s.quotes.values.any { it.eligible },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(if (s.placing) "Placing order…" else "Place order", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}

@Composable
fun WishlistScreen(onBack: () -> Unit, openProduct: (String) -> Unit) {
    val vm: InboxViewModel = hiltViewModel()
    val list by vm.wishlist.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackBar("Wishlist", onBack) }) { pad ->
        if (list.isEmpty()) Column(Modifier.padding(pad)) { EmptyState("💚", "No saved items", "Tap the heart on a product to save it here.") }
        else LazyColumn(Modifier.padding(pad)) {
            items(list, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().clickable { openProduct(p.id) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumb(p.thumbUrl, Category.from(p.category).emoji, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(p.name, style = MaterialTheme.typography.titleMedium); Text("${rupees(p.price)}/${p.unit} · ${p.farmerName}", color = FarmColors.Muted) }
                    TextButton(onClick = { vm.add(p) }) { Text("Add") }
                    IconButton(onClick = { vm.remove(p) }) { Icon(Icons.Default.Delete, "Remove") }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val vm: InboxViewModel = hiltViewModel()
    val list by vm.notifications.collectAsStateWithLifecycle()
    LaunchedEffect(list) { vm.markRead(list.filter { !it.read }.map { it.id }) }
    Scaffold(topBar = { BackBar("Notifications", onBack) }) { pad ->
        if (list.isEmpty()) Column(Modifier.padding(pad)) { EmptyState("🔔", "No notifications", "Order updates, support replies and new farmers near you appear here.") }
        else LazyColumn(Modifier.padding(pad)) {
            items(list, key = { it.id }) { n ->
                Column(
                    Modifier.fillMaxWidth().background(if (n.read) MaterialTheme.colorScheme.surface else FarmColors.Mint)
                        .clickable { if (n.open.isNotBlank()) open(n.open) }.padding(16.dp),
                ) {
                    Text(n.title, style = MaterialTheme.typography.titleMedium)
                    Text(n.body, color = FarmColors.Muted)
                }
                HorizontalDivider()
            }
        }
    }
}
