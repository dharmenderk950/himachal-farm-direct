package com.dk.himachalfarmdirect.ui.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dk.himachalfarmdirect.data.geo.Geo
import com.dk.himachalfarmdirect.data.geo.LatLngPoint
import com.dk.himachalfarmdirect.data.location.LocationRepository
import com.dk.himachalfarmdirect.data.model.Address
import com.dk.himachalfarmdirect.data.model.CartLine
import com.dk.himachalfarmdirect.data.model.DeliveryQuote
import com.dk.himachalfarmdirect.data.model.Farmer
import com.dk.himachalfarmdirect.data.model.FulfilmentMode
import com.dk.himachalfarmdirect.data.model.Product
import com.dk.himachalfarmdirect.data.repo.AppNotification
import com.dk.himachalfarmdirect.data.repo.CartRepository
import com.dk.himachalfarmdirect.data.repo.ExtrasRepository
import com.dk.himachalfarmdirect.data.repo.MarketRepository
import com.dk.himachalfarmdirect.data.repo.Nearby
import com.dk.himachalfarmdirect.data.repo.OrderRepository
import com.dk.himachalfarmdirect.data.repo.UserRepository
import com.dk.himachalfarmdirect.util.friendly
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListState(
    val loading: Boolean = false,
    val error: String? = null,
    val products: List<Pair<Product, Double?>> = emptyList(),
    val farmers: List<Nearby<Farmer>> = emptyList(),
)

/**
 * Backs category, search, "see all" lists and nearby-farmer lists.
 * Route arg `kind`: "cat:<id>", "search", "recent", "best", "offers", "nearbyFarmers".
 */
@HiltViewModel
class ProductListViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val market: MarketRepository,
    private val location: LocationRepository,
    private val cart: CartRepository,
) : ViewModel() {
    val kind: String = handle["kind"] ?: "recent"
    private val _s = MutableStateFlow(ListState())
    val state: StateFlow<ListState> = _s
    var radiusKm = 20.0
        private set
    var deliveryOnly = false
        private set

    init { if (kind != "search") load() }

    fun add(p: Product) = cart.add(p)
    fun setDeliveryOnly(v: Boolean) { deliveryOnly = v; load() }
    fun setRadius(km: Double) { radiusKm = km; load() }

    fun search(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        _s.update { it.copy(loading = true, error = null) }
        runCatching {
            val center = center()
            market.search(text).map { p -> p to center?.let { Geo.distanceKm(it, LatLngPoint(p.lat, p.lng)) } }
                .sortedBy { it.second ?: 0.0 }
        }.onSuccess { r -> _s.update { it.copy(loading = false, products = r) } }
            .onFailure { e -> _s.update { it.copy(loading = false, error = e.friendly()) } }
    }

    private suspend fun center(): LatLngPoint? =
        if (location.discoveryEnabled.first()) location.selected.first()?.point else null

    fun load() = viewModelScope.launch {
        _s.update { it.copy(loading = true, error = null) }
        runCatching {
            val c = center()
            when {
                kind == "nearbyFarmers" -> ListState(farmers = c?.let { market.nearbyFarmers(it, radiusKm, deliveryOnly) }.orEmpty())
                kind.startsWith("cat:") -> {
                    val cat = kind.removePrefix("cat:")
                    if (c != null) ListState(products = market.nearbyProducts(c, radiusKm, cat, deliveryOnly).map { it.item to it.distanceKm })
                    else ListState(products = market.productsByCategory(cat, 60).filter { !deliveryOnly || it.deliveryAvailable }.map { it to null })
                }
                else -> {
                    val base = if (c != null) market.nearbyProducts(c, radiusKm, null, deliveryOnly).map { it.item to it.distanceKm as Double? }
                    else when (kind) {
                        "best" -> market.bestSellers(60)
                        "offers" -> market.offers(60)
                        else -> market.recentProducts(60)
                    }.filter { !deliveryOnly || it.deliveryAvailable }.map { it to null }
                    ListState(products = when (kind) {
                        "best" -> base.sortedByDescending { it.first.soldCount }
                        "offers" -> base.filter { it.first.discountPercent > 0 }
                        else -> base.sortedByDescending { it.first.createdAt }
                    })
                }
            }
        }.onSuccess { r -> _s.value = r }
            .onFailure { e -> _s.update { it.copy(loading = false, error = e.friendly()) } }
    }
}

data class DetailState(
    val product: Product? = null,
    val farmer: Farmer? = null,
    val farmerProducts: List<Product> = emptyList(),
    val distanceKm: Double? = null,
    val error: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val market: MarketRepository,
    private val location: LocationRepository,
    private val cart: CartRepository,
    private val extras: ExtrasRepository,
) : ViewModel() {
    private val productId: String? = handle["productId"]
    private val farmerId: String? = handle["farmerId"]
    private val _s = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _s
    val wishlistIds = extras.wishlist().map { l -> l.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init { load() }

    fun load() = viewModelScope.launch {
        runCatching {
            val p = productId?.let { market.product(it) }
            val fid = farmerId ?: p?.farmerId
            val f = fid?.let { market.farmer(it) }
            val c = if (location.discoveryEnabled.first()) location.selected.first()?.point else null
            val d = c?.let { center -> (f?.let { LatLngPoint(it.lat, it.lng) } ?: p?.let { LatLngPoint(it.lat, it.lng) })?.let { Geo.distanceKm(center, it) } }
            DetailState(p, f, fid?.let { market.productsOfFarmer(it).filter { x -> x.active && x.stock > 0 } }.orEmpty(), d)
        }.onSuccess { _s.value = it }.onFailure { e -> _s.update { it.copy(error = e.friendly()) } }
    }

    fun add(p: Product, qty: Double = 1.0) = cart.add(p, qty)
    fun toggleWish(p: Product, add: Boolean) = viewModelScope.launch { runCatching { extras.toggleWishlist(p, add) } }
}

data class CheckoutState(
    val address: Address? = null,
    val mode: FulfilmentMode = FulfilmentMode.HOME_DELIVERY,
    /** One backend quote per farmer in the cart. */
    val quotes: Map<String, DeliveryQuote> = emptyMap(),
    val checking: Boolean = false,
    val placing: Boolean = false,
    val message: String? = null,
    val placedOrderIds: List<String> = emptyList(),
)

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cart: CartRepository,
    private val orders: OrderRepository,
    private val market: MarketRepository,
    users: UserRepository,
) : ViewModel() {
    val lines = cart.lines
    val farmers = MutableStateFlow<Map<String, Farmer>>(emptyMap())

    fun loadFarmers(ids: List<String>) = viewModelScope.launch {
        ids.filter { it !in farmers.value }.forEach { id ->
            runCatching { market.farmer(id) }.getOrNull()?.let { f -> farmers.update { it + (id to f) } }
        }
    }
    val addresses = users.addresses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _s = MutableStateFlow(CheckoutState())
    val state: StateFlow<CheckoutState> = _s

    init {
        viewModelScope.launch {
            addresses.collect { list ->
                if (_s.value.address == null) list.firstOrNull { it.defaultAddress }?.let(::selectAddress) ?: list.firstOrNull()?.let(::selectAddress)
            }
        }
    }

    fun setQty(id: String, q: Double) { cart.setQty(id, q) }
    fun selectAddress(a: Address) { _s.update { it.copy(address = a) }; recheck() }

    init {
        // Re-check whenever the cart's contents change.
        viewModelScope.launch { cart.lines.collect { if (_s.value.address != null) recheck() } }
    }
    fun setMode(m: FulfilmentMode) { _s.update { it.copy(mode = m) }; recheck() }

    /** Asks the backend for every farmer group: stock, service area, delivery availability, charge. */
    fun recheck() = viewModelScope.launch {
        val a = _s.value.address ?: return@launch
        val groups = cart.lines.value.groupBy { it.farmerId }
        if (groups.isEmpty()) return@launch
        _s.update { it.copy(checking = true, message = null) }
        val quotes = mutableMapOf<String, DeliveryQuote>()
        var err: String? = null
        for ((farmerId, l) in groups) {
            runCatching { orders.checkDelivery(farmerId, l, a, _s.value.mode.name) }
                .onSuccess { quotes[farmerId] = it }
                .onFailure { err = "Couldn't check delivery: " + it.friendly() }
        }
        _s.update { it.copy(checking = false, quotes = quotes, message = err) }
    }

    fun placeAll() = viewModelScope.launch {
        val a = _s.value.address ?: run { _s.update { it.copy(message = "Choose a delivery address first") }; return@launch }
        if (a.phone.filter(Char::isDigit).length < 10) {
            _s.update { it.copy(message = "Please add your mobile number to this address (tap it in Account → Delivery addresses) so the farmer can reach you.") }
            return@launch
        }
        _s.update { it.copy(placing = true, message = null) }
        val placed = mutableListOf<String>()
        val problems = mutableListOf<String>()
        for ((farmerId, l) in cart.lines.value.groupBy { it.farmerId }) {
            if (_s.value.quotes[farmerId]?.eligible != true) continue
            runCatching { orders.placeOrder(farmerId, l, a, _s.value.mode.name) }
                .onSuccess { placed += it; cart.clearFarmer(farmerId) }
                .onFailure { problems += it.friendly() }
        }
        _s.update {
            it.copy(placing = false, placedOrderIds = placed,
                message = problems.firstOrNull() ?: if (placed.isEmpty()) "None of the items can be delivered to this address." else null)
        }
    }

    fun groups(lines: List<CartLine>) = lines.groupBy { it.farmerId }
}

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val extras: ExtrasRepository,
    private val cart: CartRepository,
) : ViewModel() {
    val wishlist = extras.wishlist().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val notifications = extras.notifications().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<AppNotification>())
    fun add(p: Product) = cart.add(p)
    fun remove(p: Product) = viewModelScope.launch { runCatching { extras.toggleWishlist(p, false) } }
    fun markRead(ids: List<String>) = viewModelScope.launch { if (ids.isNotEmpty()) runCatching { extras.markAllRead(ids) } }
}
