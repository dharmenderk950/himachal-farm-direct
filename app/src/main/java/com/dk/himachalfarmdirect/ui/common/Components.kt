package com.dk.himachalfarmdirect.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.dk.himachalfarmdirect.data.geo.Geo
import com.dk.himachalfarmdirect.data.model.Category
import com.dk.himachalfarmdirect.data.model.Farmer
import com.dk.himachalfarmdirect.data.model.Product
import com.dk.himachalfarmdirect.ui.theme.FarmColors

fun rupees(v: Double): String = if (v % 1.0 == 0.0) "₹${v.toLong()}" else "₹%.2f".format(v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
    )
}

@Composable
fun OfflineBanner(online: Boolean) {
    if (online) return
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFFFF3E0)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CloudOff, null, tint = FarmColors.Soil, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("No internet — showing saved results. They will refresh when you're back online.",
            style = MaterialTheme.typography.bodyMedium, color = FarmColors.Soil)
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Low-data image: requests the small thumbnail and lets Coil's disk cache serve repeats. */
@Composable
fun Thumb(url: String, fallbackEmoji: String, modifier: Modifier = Modifier) {
    Box(modifier.background(FarmColors.Mint), contentAlignment = Alignment.Center) {
        if (url.isBlank()) Text(fallbackEmoji, style = MaterialTheme.typography.headlineSmall)
        else AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).size(400).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun ProductCard(
    p: Product,
    distanceKm: Double?,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.width(158.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, FarmColors.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
        color = Color.White,
    ) {
        Column {
            Box {
                Thumb(p.thumbUrl, Category.from(p.category).emoji, Modifier.fillMaxWidth().aspectRatio(1.2f))
                if (p.discountPercent > 0) {
                    Text("${p.discountPercent}% OFF",
                        Modifier.padding(6.dp).background(FarmColors.Harvest, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = FarmColors.Ink)
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(p.farmerName, style = MaterialTheme.typography.bodyMedium, color = FarmColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${rupees(p.price)}/${p.unit}", style = MaterialTheme.typography.titleMedium, color = FarmColors.Forest)
                    if (p.mrp > p.price) {
                        Spacer(Modifier.width(4.dp))
                        Text(rupees(p.mrp), style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough), color = FarmColors.Muted)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    distanceKm?.let { Text(Geo.formatKm(it), style = MaterialTheme.typography.bodyMedium, color = FarmColors.Muted) }
                    if (p.deliveryAvailable) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.LocalShipping, "Delivery available", tint = FarmColors.Leaf, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(38.dp)) { Text("Add") }
            }
        }
    }
}

@Composable
fun FarmerCard(f: Farmer, distanceKm: Double?, onOpen: () -> Unit) {
    Surface(
        Modifier.width(220.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, FarmColors.Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
        color = Color.White,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(f.photoUrl, "👩‍🌾", Modifier.size(52.dp).clip(CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.farmName.ifBlank { f.name }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    if (f.approved) Icon(Icons.Default.Verified, "Verified farmer", tint = FarmColors.Leaf, modifier = Modifier.size(16.dp).padding(start = 2.dp))
                }
                Text(f.village, style = MaterialTheme.typography.bodyMedium, color = FarmColors.Muted, maxLines = 1)
                Text(
                    listOfNotNull(distanceKm?.let { Geo.formatKm(it) }, if (f.deliveryAvailable) "Delivers" else "Pickup").joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = FarmColors.Leaf,
                )
            }
        }
    }
}

@Composable
fun EmptyState(emoji: String, title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = FarmColors.Muted)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}
