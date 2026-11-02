package com.dk.himachalfarmdirect.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dk.himachalfarmdirect.data.location.LocationRepository
import com.dk.himachalfarmdirect.data.model.Category
import com.dk.himachalfarmdirect.data.model.Order
import com.dk.himachalfarmdirect.data.model.SellerStatus
import com.dk.himachalfarmdirect.data.repo.AppMode
import com.dk.himachalfarmdirect.data.repo.OrderRepository
import com.dk.himachalfarmdirect.data.repo.UserRepository
import com.dk.himachalfarmdirect.ui.common.EmptyState
import com.dk.himachalfarmdirect.ui.common.rupees
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import com.dk.himachalfarmdirect.util.friendly
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val users: UserRepository,
    private val location: LocationRepository,
) : ViewModel() {
    val profile = users.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val mode = users.mode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppMode.BUYER)
    val sellerStatus = users.sellerStatus.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SellerStatus.NONE)
    val discovery = location.discoveryEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val addresses = users.addresses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMode(m: AppMode) = viewModelScope.launch { users.setMode(m) }
    fun deleteAddress(id: String) = viewModelScope.launch { runCatching { users.deleteAddress(id) } }

    fun setDiscovery(on: Boolean) = viewModelScope.launch {
        location.setDiscoveryEnabled(on)
        val p = profile.value
        runCatching { users.updateDiscovery(on, p?.notifyNewSellers ?: true, location.selected.first()?.point) }
    }

    fun setNotifyNewSellers(on: Boolean) = viewModelScope.launch {
        runCatching { users.updateDiscovery(discovery.value, on, location.selected.first()?.point) }
    }

    fun signOut() = users.signOut()
}

data class AccountNav(
    val openLocation: () -> Unit,
    val openWishlist: () -> Unit,
    val openNotifications: () -> Unit,
    val openHelp: () -> Unit,
    val openTickets: () -> Unit,
    val openFarmProfile: () -> Unit,
)

@Composable
fun AccountScreen(nav: AccountNav) {
    val vm: AccountViewModel = hiltViewModel()
    val p by vm.profile.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val discovery by vm.discovery.collectAsStateWithLifecycle()
    val addresses by vm.addresses.collectAsStateWithLifecycle()
    val status by vm.sellerStatus.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(p?.name?.ifBlank { null } ?: "My account", style = MaterialTheme.typography.headlineSmall)
                Text(p?.phone?.ifBlank { null } ?: p?.email.orEmpty(), color = FarmColors.Muted)
            }
        }
        // Spec C: Buyer / Seller switch, same account, same APK.
        item {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FarmColors.Mint).padding(16.dp)) {
                when (status) {
                    SellerStatus.APPROVED -> {
                        Text("App mode", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf(AppMode.BUYER to "Buyer", AppMode.SELLER to "Seller").forEachIndexed { i, (m, label) ->
                                SegmentedButton(mode == m, { vm.setMode(m) }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                            }
                        }
                        Text("In Buyer mode you can buy from other farmers with this same account.",
                            color = FarmColors.Muted, modifier = Modifier.padding(top = 6.dp))
                    }
                    SellerStatus.PENDING -> {
                        Text("Seller verification in progress", style = MaterialTheme.typography.titleMedium)
                        Text("We're checking your farm details. You'll get a notification when approved.", color = FarmColors.Muted)
                        OutlinedButton(onClick = nav.openFarmProfile, modifier = Modifier.padding(top = 8.dp)) { Text("Edit farm details") }
                    }
                    SellerStatus.REJECTED -> {
                        Text("Seller application not approved", style = MaterialTheme.typography.titleMedium)
                        Text("Please update your details or contact support from the Help Centre.", color = FarmColors.Muted)
                        OutlinedButton(onClick = nav.openFarmProfile, modifier = Modifier.padding(top = 8.dp)) { Text("Update farm details") }
                    }
                    SellerStatus.NONE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Agriculture, null, tint = FarmColors.Forest)
                            Spacer(Modifier.width(8.dp))
                            Text("Are you a farmer?", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("Register your farm to sell directly to buyers near you.", color = FarmColors.Muted)
                        OutlinedButton(onClick = nav.openFarmProfile, modifier = Modifier.padding(top = 8.dp)) { Text("Register as seller") }
                    }
                }
            }
        }
        item {
            Text("Location", Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium)
            ToggleRow("Show products near me", "Off = browse all areas manually", discovery, vm::setDiscovery)
            ToggleRow("Tell me when new farmers join nearby", "Uses only your approximate area", p?.notifyNewSellers ?: true, vm::setNotifyNewSellers)
            LinkRow(Icons.Default.LocationOn, "Change delivery location", nav.openLocation)
        }
        item { Text("Saved addresses", Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.titleMedium) }
        if (addresses.isEmpty()) item { Text("None yet — add one from \"Change delivery location\".", Modifier.padding(horizontal = 16.dp), color = FarmColors.Muted) }
        items(addresses, key = { it.id }) { a ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, null, tint = FarmColors.Leaf)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label + if (a.defaultAddress) " (default)" else "", style = MaterialTheme.typography.titleMedium)
                    Text("${a.village} ${a.pinCode}", color = FarmColors.Muted)
                }
                IconButton(onClick = { vm.deleteAddress(a.id) }) { Icon(Icons.Default.Delete, "Delete address") }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            LinkRow(Icons.Default.FavoriteBorder, "Wishlist", nav.openWishlist)
            LinkRow(Icons.Default.Notifications, "Notifications", nav.openNotifications)
            LinkRow(Icons.Default.HelpOutline, "Help Centre", nav.openHelp)
            LinkRow(Icons.Default.SupportAgent, "My support tickets", nav.openTickets)
            LinkRow(Icons.AutoMirrored.Filled.Logout, "Sign out", vm::signOut)
            Text("App by DK · dharmenderk950@gmail.com", Modifier.padding(16.dp), color = FarmColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title); Text(sub, color = FarmColors.Muted, style = MaterialTheme.typography.bodyMedium) }
        Switch(checked, onChange)
    }
}

@Composable
fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = FarmColors.Forest)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Categories tab (buyer). Large tiles for easy tapping. */
@Composable
fun CategoriesScreen(openCategory: (String) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Categories", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
        LazyVerticalGrid(
            GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Category.entries) { c ->
                Column(
                    Modifier.aspectRatio(1.2f).clip(RoundedCornerShape(18.dp)).background(FarmColors.Mint)
                        .clickable { openCategory(c.id) }.padding(16.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(c.emoji, style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.6f))
                    Spacer(Modifier.height(8.dp))
                    Text(c.label, style = MaterialTheme.typography.titleMedium, color = FarmColors.Forest)
                }
            }
        }
    }
}

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orders: OrderRepository,
    users: UserRepository,
) : ViewModel() {
    val mode = users.mode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppMode.BUYER)
    val buyerOrders = orders.myOrders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val farmOrders = orders.farmOrders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow<String?>(null)

    fun setStatus(id: String, status: String) = viewModelScope.launch {
        runCatching { orders.updateOrderStatus(id, status) }.onFailure { message.value = it.friendly() }
    }
}

private val statusLabels = mapOf(
    "PLACED" to "Placed", "ACCEPTED" to "Accepted by farmer", "PACKED" to "Packed",
    "OUT_FOR_DELIVERY" to "Out for delivery", "READY_FOR_PICKUP" to "Ready for pickup",
    "DELIVERED" to "Delivered", "CANCELLED" to "Cancelled", "REJECTED" to "Not accepted",
)

@Composable
fun OrdersScreen() {
    val vm: OrdersViewModel = hiltViewModel()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val buyer by vm.buyerOrders.collectAsStateWithLifecycle()
    val farm by vm.farmOrders.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val seller = mode == AppMode.SELLER
    val list = if (seller) farm else buyer

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(if (seller) "Orders to fulfil" else "My orders", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
        msg?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = FarmColors.Danger) }
        if (list.isEmpty()) EmptyState("📦", "No orders yet", if (seller) "New orders from buyers will appear here." else "Your orders will appear here.")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { o -> OrderCard(o, seller, vm::setStatus) }
        }
    }
}

@Composable
private fun OrderCard(o: Order, seller: Boolean, setStatus: (String, String) -> Unit) {
    val fmt = SimpleDateFormat("d MMM, h:mm a", Locale("en", "IN"))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, FarmColors.Line, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Row {
            Text(if (seller) "Order #${o.id.takeLast(6).uppercase()}" else o.farmerName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(statusLabels[o.status] ?: o.status, color = if (o.status == "CANCELLED" || o.status == "REJECTED") FarmColors.Danger else FarmColors.Leaf, fontWeight = FontWeight.SemiBold)
        }
        o.createdAt?.let { Text(fmt.format(it.toDate()), color = FarmColors.Muted) }
        Text(o.lines.joinToString { "${it.name} × ${it.qty.toInt()} ${it.unit}" }, modifier = Modifier.padding(vertical = 4.dp))
        Text("Total ${rupees(o.grandTotal)} (delivery ${rupees(o.deliveryCharge)}) · ${o.fulfilment.replace('_', ' ').lowercase()}", color = FarmColors.Muted)
        if (seller) Text("To: ${o.deliveryVillage} ${o.deliveryPin}", color = FarmColors.Muted)

        val actions: List<Pair<String, String>> = if (seller) when (o.status) {
            "PLACED" -> listOf("ACCEPTED" to "Accept", "REJECTED" to "Reject")
            "ACCEPTED" -> listOf("PACKED" to "Mark packed")
            "PACKED" -> if (o.fulfilment == "HOME_DELIVERY") listOf("OUT_FOR_DELIVERY" to "Out for delivery") else listOf("READY_FOR_PICKUP" to "Ready for pickup")
            "OUT_FOR_DELIVERY", "READY_FOR_PICKUP" -> listOf("DELIVERED" to "Mark delivered")
            else -> emptyList()
        } else if (o.status == "PLACED") listOf("CANCELLED" to "Cancel order") else emptyList()
        if (actions.isNotEmpty()) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEach { (st, label) -> OutlinedButton(onClick = { setStatus(o.id, st) }) { Text(label) } }
        }
    }
}
