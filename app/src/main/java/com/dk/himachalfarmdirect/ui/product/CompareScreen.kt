package com.dk.himachalfarmdirect.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dk.himachalfarmdirect.data.geo.Geo
import com.dk.himachalfarmdirect.data.geo.LatLngPoint
import com.dk.himachalfarmdirect.data.location.LocationRepository
import com.dk.himachalfarmdirect.data.model.Product
import com.dk.himachalfarmdirect.data.repo.CartRepository
import com.dk.himachalfarmdirect.data.repo.MarketRepository
import com.dk.himachalfarmdirect.ui.common.BackBar
import com.dk.himachalfarmdirect.ui.common.EmptyState
import com.dk.himachalfarmdirect.ui.common.rupees
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import com.dk.himachalfarmdirect.util.friendly
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One item ("apple") offered by several farmers, cheapest first. */
data class CompareGroup(val title: String, val offers: List<Pair<Product, Double?>>)

data class CompareState(val loading: Boolean = false, val error: String? = null, val searched: Boolean = false, val groups: List<CompareGroup> = emptyList())

@HiltViewModel
class CompareViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val market: MarketRepository,
    private val location: LocationRepository,
    private val cart: CartRepository,
) : ViewModel() {
    val initial: String = handle["q"] ?: ""
    val state = MutableStateFlow(CompareState())

    init { if (initial.isNotBlank()) search(initial) }

    fun add(p: Product) = cart.add(p)

    fun search(text: String) = viewModelScope.launch {
        val q = text.trim()
        if (q.length < 2) return@launch
        state.value = CompareState(loading = true)
        runCatching {
            val center = if (location.discoveryEnabled.first()) location.selected.first()?.point else null
            market.search(q, 100)
                .map { p -> p to center?.let { Geo.distanceKm(it, LatLngPoint(p.lat, p.lng)) } }
                // Group by the word that matched the search ("Kinnaur Apple" and "Apple Royal" → "apple").
                .groupBy { (p, _) -> groupKey(p.name, q) }
                .map { (key, offers) ->
                    CompareGroup(key.replaceFirstChar { it.uppercase() }, offers.sortedBy { it.first.price })
                }
                .sortedByDescending { it.offers.size }
        }.onSuccess { state.value = CompareState(searched = true, groups = it) }
            .onFailure { state.value = CompareState(searched = true, error = it.friendly()) }
    }

    private fun groupKey(name: String, query: String): String {
        val words = name.lowercase().split(Regex("[\\s,/()-]+")).filter { it.length >= 2 }
        val qw = query.lowercase().split(" ").first()
        return words.firstOrNull { it.startsWith(qw) } ?: words.firstOrNull() ?: name.lowercase()
    }
}

private val QUICK = listOf("Apple", "Potato", "Tomato", "Peas", "Rajma", "Honey", "Ghee", "Milk", "Ginger", "Garlic")

@Composable
fun CompareScreen(onBack: () -> Unit, openProduct: (String) -> Unit, openCart: () -> Unit) {
    val vm: CompareViewModel = hiltViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf(vm.initial) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { BackBar("Compare prices", onBack) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Which item? e.g. apple, rajma") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QUICK) { q -> AssistChip(onClick = { query = q; vm.search(q) }, label = { Text(q) }) }
                }
                if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                s.error?.let { EmptyState("📶", "Couldn't search", it, "Try again") { vm.search(query) } }
                if (s.searched && s.groups.isEmpty() && s.error == null)
                    EmptyState("🔍", "No farmer sells \"$query\" yet", "Try another item or spelling.")
                if (!s.searched && !s.loading)
                    Text("Type an item to see every farmer's price side by side — cheapest first.",
                        Modifier.padding(16.dp), color = FarmColors.Muted)
            }
            s.groups.forEach { g ->
                item(key = "h_${g.title}") {
                    val lo = g.offers.first().first.price
                    val hi = g.offers.last().first.price
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)) {
                        Text(g.title, style = MaterialTheme.typography.titleLarge, color = FarmColors.Forest)
                        Text("${g.offers.size} seller${if (g.offers.size == 1) "" else "s"} · " +
                            (if (lo == hi) rupees(lo) else "${rupees(lo)} – ${rupees(hi)}"), color = FarmColors.Muted)
                    }
                }
                val nearest = g.offers.filter { it.second != null }.minByOrNull { it.second!! }?.first?.id
                items(g.offers, key = { "o_${it.first.id}" }) { (p, d) ->
                    OfferRow(
                        p, d,
                        cheapest = p.id == g.offers.first().first.id && g.offers.size > 1,
                        nearest = p.id == nearest && g.offers.size > 1,
                        onOpen = { openProduct(p.id) },
                        onChoose = {
                            vm.add(p)
                            scope.launch {
                                if (snack.showSnackbar("${p.name} from ${p.farmerName} added", "View cart") ==
                                    androidx.compose.material3.SnackbarResult.ActionPerformed) openCart()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferRow(p: Product, distance: Double?, cheapest: Boolean, nearest: Boolean, onOpen: () -> Unit, onChoose: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(if (cheapest) 2.dp else 1.dp, if (cheapest) FarmColors.Leaf else FarmColors.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (cheapest) Badge("Cheapest", FarmColors.Leaf)
                if (nearest) Badge("Nearest", FarmColors.Harvest)
            }
            Text(p.farmerName.ifBlank { "Farmer" }, style = MaterialTheme.typography.titleMedium)
            Text(p.name, color = FarmColors.Muted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                distance?.let { Text(Geo.formatKm(it) + " away", color = FarmColors.Muted, fontSize = 14.sp) }
                if (p.deliveryAvailable) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.LocalShipping, "Delivers", tint = FarmColors.Leaf, modifier = Modifier.size(16.dp))
                    Text(" delivers", color = FarmColors.Leaf, fontSize = 14.sp)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(rupees(p.price), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FarmColors.Forest)
            Text("per ${p.unit}", color = FarmColors.Muted, fontSize = 13.sp)
            Button(onClick = onChoose, modifier = Modifier.padding(top = 4.dp)) { Text("Choose") }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(color, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
}
