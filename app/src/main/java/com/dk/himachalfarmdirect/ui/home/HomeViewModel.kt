package com.dk.himachalfarmdirect.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dk.himachalfarmdirect.data.config.AppConfigRepository
import com.dk.himachalfarmdirect.data.location.LocationRepository
import com.dk.himachalfarmdirect.data.location.SelectedLocation
import com.dk.himachalfarmdirect.data.model.Banner
import com.dk.himachalfarmdirect.data.model.Category
import com.dk.himachalfarmdirect.data.model.Farmer
import com.dk.himachalfarmdirect.data.model.Product
import com.dk.himachalfarmdirect.data.repo.CartRepository
import com.dk.himachalfarmdirect.data.repo.ExtrasRepository
import com.dk.himachalfarmdirect.data.repo.MarketRepository
import com.dk.himachalfarmdirect.data.repo.Nearby
import com.dk.himachalfarmdirect.util.friendly
import com.dk.himachalfarmdirect.util.onlineFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val error: String? = null,
    val location: SelectedLocation? = null,
    val discoveryOn: Boolean = true,
    val radiusKm: Int = 10,
    val radii: List<Int> = listOf(5, 10, 20, 50),
    val deliveryOnly: Boolean = false,
    val banners: List<Banner> = emptyList(),
    val nearbyFarmers: List<Nearby<Farmer>> = emptyList(),
    val featuredFarmers: List<Farmer> = emptyList(),
    /** Category id → products (with distance when location is on). */
    val byCategory: Map<String, List<Pair<Product, Double?>>> = emptyMap(),
    val recent: List<Pair<Product, Double?>> = emptyList(),
    val bestSellers: List<Pair<Product, Double?>> = emptyList(),
    val offers: List<Pair<Product, Double?>> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val market: MarketRepository,
    private val location: LocationRepository,
    private val config: AppConfigRepository,
    private val cart: CartRepository,
    extras: ExtrasRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    val online = context.onlineFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val cartCount = cart.lines.map { it.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val unread = extras.notifications().map { l -> l.count { !it.read } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val wishlistIds = extras.wishlist().map { l -> l.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch { config.refresh() }
        viewModelScope.launch {
            combine(location.selected, location.discoveryEnabled, config.config) { loc, on, cfg -> Triple(loc, on, cfg.radiiKm) }
                .distinctUntilChanged()
                .collect { (loc, on, radii) ->
                    _state.update {
                        it.copy(location = loc, discoveryOn = on, radii = radii,
                            radiusKm = if (it.radiusKm in radii) it.radiusKm else radii.getOrElse(1) { radii.first() })
                    }
                    load()
                }
        }
        // Reload automatically when connectivity returns.
        viewModelScope.launch { online.collect { if (it && _state.value.error != null) load() } }
    }

    fun setRadius(km: Int) { _state.update { it.copy(radiusKm = km) }; load() }
    fun setDeliveryOnly(v: Boolean) { _state.update { it.copy(deliveryOnly = v) }; load() }
    fun addToCart(p: Product) = cart.add(p)

    fun load() = viewModelScope.launch {
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }
        // coroutineScope: a failed child query is caught below instead of crashing the app.
        runCatching { coroutineScope {
            val bannersJ = async { runCatching { market.banners() }.getOrDefault(emptyList()) }
            val featuredJ = async { runCatching { market.featuredFarmers() }.getOrDefault(emptyList()) }
            val loc = s.location
            if (s.discoveryOn && loc != null) {
                val r = s.radiusKm.toDouble()
                val farmersJ = async { market.nearbyFarmers(loc.point, r, s.deliveryOnly) }
                val productsJ = async { market.nearbyProducts(loc.point, r, null, s.deliveryOnly) }
                val prods = productsJ.await().map { it.item to it.distanceKm as Double? }
                s.copy(
                    loading = false,
                    banners = bannersJ.await(),
                    featuredFarmers = featuredJ.await(),
                    nearbyFarmers = farmersJ.await(),
                    byCategory = prods.groupBy { it.first.category },
                    recent = prods.sortedByDescending { it.first.createdAt }.take(20),
                    bestSellers = prods.sortedByDescending { it.first.soldCount }.filter { it.first.soldCount > 0 }.take(20),
                    offers = prods.filter { it.first.discountPercent > 0 }.take(20),
                )
            } else {
                // Manual browsing — whole marketplace, no distance shown.
                val cats = listOf(Category.VEGETABLES, Category.FRUITS, Category.PULSES, Category.HONEY)
                    .map { c -> c.id to async { market.productsByCategory(c.id, 20) } }
                val recentJ = async { market.recentProducts() }
                val bestJ = async { market.bestSellers() }
                val offersJ = async { market.offers() }
                s.copy(
                    loading = false,
                    banners = bannersJ.await(),
                    featuredFarmers = featuredJ.await(),
                    nearbyFarmers = emptyList(),
                    byCategory = cats.associate { (id, j) -> id to j.await().map { it to null } },
                    recent = recentJ.await().map { it to null },
                    bestSellers = bestJ.await().filter { it.soldCount > 0 }.map { it to null },
                    offers = offersJ.await().map { it to null },
                )
            }
        } }.onSuccess { new -> _state.update { new.copy(radiusKm = it.radiusKm, deliveryOnly = it.deliveryOnly) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.friendly()) } }
    }
}
