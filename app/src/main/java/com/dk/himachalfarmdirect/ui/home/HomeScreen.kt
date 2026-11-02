package com.dk.himachalfarmdirect.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dk.himachalfarmdirect.R
import com.dk.himachalfarmdirect.data.model.Banner
import com.dk.himachalfarmdirect.data.model.Category
import com.dk.himachalfarmdirect.data.model.Product
import com.dk.himachalfarmdirect.ui.common.EmptyState
import com.dk.himachalfarmdirect.ui.common.FarmerCard
import com.dk.himachalfarmdirect.ui.common.OfflineBanner
import com.dk.himachalfarmdirect.ui.common.ProductCard
import com.dk.himachalfarmdirect.ui.common.SectionHeader
import com.dk.himachalfarmdirect.ui.common.Thumb
import com.dk.himachalfarmdirect.ui.theme.FarmColors

data class HomeNav(
    val openLocation: () -> Unit,
    val openSearch: () -> Unit,
    val openCategory: (String) -> Unit,
    val openList: (String) -> Unit,          // "recent" | "best" | "offers" | "nearbyFarmers"
    val openProduct: (String) -> Unit,
    val openFarmer: (String) -> Unit,
    val openCart: () -> Unit,
    val openWishlist: () -> Unit,
    val openNotifications: () -> Unit,
    val openHelp: () -> Unit,
    val openCompare: () -> Unit,
)

@Composable
fun HomeScreen(nav: HomeNav) {
    val vm: HomeViewModel = hiltViewModel()
    val s by vm.state.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val cartCount by vm.cartCount.collectAsStateWithLifecycle()
    val unread by vm.unread.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        HomeHeader(
            locationLabel = when {
                !s.discoveryOn -> "Browsing all areas"
                s.location == null -> "Set delivery location"
                else -> s.location!!.label
            },
            cartCount = cartCount, unread = unread, nav = nav,
        )
        OfflineBanner(online)
        if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = FarmColors.Leaf)

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { QuickActions(nav) }
            if (s.banners.isNotEmpty()) item { BannerRow(s.banners) { b -> b.categoryId.takeIf { it.isNotBlank() }?.let(nav.openCategory) } }
            else item { HeroCard { nav.openCategory(Category.VEGETABLES.id) } }
            item { SectionHeader("Shop by category") }
            item { CategoryStrip(nav.openCategory) }

            if (s.discoveryOn && s.location != null) item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.radii.forEach { r -> FilterChip(selected = s.radiusKm == r, onClick = { vm.setRadius(r) }, label = { Text("$r km") }) }
                }
                Row(Modifier.padding(horizontal = 16.dp)) {
                    FilterChip(selected = s.deliveryOnly, onClick = { vm.setDeliveryOnly(!s.deliveryOnly) }, label = { Text("Home delivery only") })
                }
            }

            s.error?.let { err -> item { EmptyState("📶", "Couldn't load products", err, "Try again") { vm.load() } } }

            if (s.discoveryOn && s.location == null) item {
                EmptyState("📍", "Where should we deliver?", "Set your village to see farmers and fresh produce near you.", "Choose location", nav.openLocation)
            }

            if (s.nearbyFarmers.isNotEmpty()) {
                item { SectionHeader("Farmers near you (${s.nearbyFarmers.size})", "See all") { nav.openList("nearbyFarmers") } }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(s.nearbyFarmers.take(15), key = { it.item.id }) { n -> FarmerCard(n.item, n.distanceKm) { nav.openFarmer(n.item.id) } }
                    }
                }
            } else if (!s.loading && s.discoveryOn && s.location != null && s.error == null) item {
                EmptyState("🌱", "No farmers within ${s.radiusKm} km yet",
                    "Try a bigger distance. We'll notify you when a farmer near you joins.",
                    if (s.radiusKm < s.radii.last()) "Search ${s.radii.last()} km" else null) { vm.setRadius(s.radii.last()) }
            }

            productRow("Offers & discounts", s.offers, "offers", nav, vm::addToCart)
            productRow("Fresh vegetables", s.byCategory[Category.VEGETABLES.id].orEmpty(), "cat:${Category.VEGETABLES.id}", nav, vm::addToCart)
            productRow("Seasonal fruits", s.byCategory[Category.FRUITS.id].orEmpty(), "cat:${Category.FRUITS.id}", nav, vm::addToCart)
            productRow("Pulses & grains", s.byCategory[Category.PULSES.id].orEmpty(), "cat:${Category.PULSES.id}", nav, vm::addToCart)
            productRow("Local honey", s.byCategory[Category.HONEY.id].orEmpty(), "cat:${Category.HONEY.id}", nav, vm::addToCart)
            productRow("Recently added", s.recent, "recent", nav, vm::addToCart)
            productRow("Best sellers", s.bestSellers, "best", nav, vm::addToCart)

            if (s.featuredFarmers.isNotEmpty()) {
                item { SectionHeader("Featured farmers") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(s.featuredFarmers, key = { it.id }) { f -> FarmerCard(f, null) { nav.openFarmer(f.id) } }
                    }
                }
            }

            item {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FarmColors.Mint)
                        .clickable(onClick = nav.openHelp).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.HelpOutline, null, tint = FarmColors.Forest)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Help Centre", style = MaterialTheme.typography.titleMedium, color = FarmColors.Forest)
                        Text("Orders, payments, delivery and more", color = FarmColors.Muted)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.productRow(
    title: String,
    list: List<Pair<Product, Double?>>,
    listKey: String,
    nav: HomeNav,
    onAdd: (Product) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h_$listKey") {
        SectionHeader(title, "See all") {
            if (listKey.startsWith("cat:")) nav.openCategory(listKey.removePrefix("cat:")) else nav.openList(listKey)
        }
    }
    item(key = "r_$listKey") {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list.take(12), key = { it.first.id }) { (p, d) ->
                ProductCard(p, d, onOpen = { nav.openProduct(p.id) }, onAdd = { onAdd(p) })
            }
        }
    }
}

@Composable
private fun HomeHeader(locationLabel: String, cartCount: Int, unread: Int, nav: HomeNav) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(FarmColors.Forest, FarmColors.Leaf)))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(34.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Himachal Farm Direct", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.clickable(onClick = nav.openLocation), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = FarmColors.Sprout, modifier = Modifier.size(16.dp))
                    Text(locationLabel, color = FarmColors.Sprout, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, false))
                    Icon(Icons.Default.KeyboardArrowDown, "Change location", tint = FarmColors.Sprout, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = nav.openWishlist) { Icon(Icons.Default.FavoriteBorder, "Wishlist", tint = Color.White) }
            IconButton(onClick = nav.openNotifications) {
                BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                    Icon(Icons.Default.Notifications, "Notifications", tint = Color.White)
                }
            }
            IconButton(onClick = nav.openCart) {
                BadgedBox(badge = { if (cartCount > 0) Badge(containerColor = FarmColors.Harvest) { Text("$cartCount", color = FarmColors.Ink) } }) {
                    Icon(Icons.Default.ShoppingCart, "Cart", tint = Color.White)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.padding(end = 12.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
                .clickable(onClick = nav.openSearch).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = FarmColors.Muted)
            Spacer(Modifier.width(8.dp))
            Text("Search apples, rajma, honey…", color = FarmColors.Muted)
        }
    }
}

private val categoryTint = mapOf(
    Category.VEGETABLES to Color(0xFFE3F4E1), Category.FRUITS to Color(0xFFFFE8E3),
    Category.PULSES to Color(0xFFFFF3D6), Category.HONEY to Color(0xFFFFEFC7),
    Category.DAIRY to Color(0xFFE6F1FB), Category.SPICES to Color(0xFFEAF6E7),
)

@Composable
private fun CategoryStrip(onOpen: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(Category.entries.filter { it != Category.OTHER }) { c ->
            Column(
                Modifier.width(92.dp).clip(RoundedCornerShape(18.dp)).background(categoryTint[c] ?: FarmColors.Mint)
                    .clickable { onOpen(c.id) }.padding(vertical = 14.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(c.emoji, fontSize = 34.sp)
                Spacer(Modifier.height(6.dp))
                Text(c.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = FarmColors.Ink)
            }
        }
    }
}

/** Four big shortcut buttons right under the header. */
@Composable
private fun QuickActions(nav: HomeNav) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickTile("⚖️", "Compare\nprices", Color(0xFFE6F1FB), Modifier.weight(1f), nav.openCompare)
        QuickTile("📍", "Farmers\nnear me", FarmColors.Mint, Modifier.weight(1f)) { nav.openList("nearbyFarmers") }
        QuickTile("🏷️", "Offers", Color(0xFFFFF3D6), Modifier.weight(1f)) { nav.openList("offers") }
        QuickTile("❓", "Help", Color(0xFFF3E8FB), Modifier.weight(1f), nav.openHelp)
    }
}

@Composable
private fun QuickTile(emoji: String, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(tint).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 26.sp)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, color = FarmColors.Ink, lineHeight = 15.sp)
    }
}

/** Shown when the admin hasn't added banners yet. */
@Composable
private fun HeroCard(onShop: () -> Unit) {
    Row(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(FarmColors.Harvest, Color(0xFFFFD54F))))
            .clickable(onClick = onShop).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Fresh from Himachal farms", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FarmColors.Ink)
            Text("Buy straight from the farmer — fair price, no middlemen.", color = FarmColors.Soil)
            Spacer(Modifier.height(8.dp))
            Text("Shop vegetables  ›", fontWeight = FontWeight.Bold, color = FarmColors.Forest)
        }
        Text("🧺", fontSize = 56.sp)
    }
}

@Composable
private fun BannerRow(banners: List<Banner>, onTap: (Banner) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(banners, key = { it.id }) { b ->
            Box(
                Modifier.width(300.dp).height(130.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, FarmColors.Line, RoundedCornerShape(16.dp)).clickable { onTap(b) },
            ) {
                Thumb(b.imageUrl, "🌄", Modifier.fillMaxSize())
                Column(
                    Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xCC1B5E20), Color.Transparent))).padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(b.title, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(b.subtitle, color = Color.White)
                }
            }
        }
    }
}
