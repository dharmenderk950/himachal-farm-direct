package com.dk.himachalfarmdirect.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.himachalfarmdirect.ui.common.rupees
import com.dk.himachalfarmdirect.ui.theme.FarmColors

/** First screen after login: two big, obvious choices. */
@Composable
fun RolePickerScreen(name: String, onBuyer: () -> Unit, onSeller: () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(FarmColors.Mint, Color.White)))
            .statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (name.isBlank()) "Welcome!" else "Welcome, ${name.substringBefore(' ')}!",
            style = MaterialTheme.typography.headlineSmall, color = FarmColors.Forest)
        Text("What would you like to do?", style = MaterialTheme.typography.titleMedium, color = FarmColors.Muted)
        Spacer(Modifier.height(28.dp))
        RoleCard("🛒", "I want to BUY", "Fresh vegetables, fruits, honey and grains from farmers near you",
            FarmColors.Leaf, onBuyer)
        Spacer(Modifier.height(16.dp))
        RoleCard("👨‍🌾", "I am a FARMER / SELLER", "Sell your produce directly to buyers nearby",
            FarmColors.Harvest, onSeller)
        Spacer(Modifier.height(20.dp))
        Text("You can switch any time from Account.", color = FarmColors.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RoleCard(emoji: String, title: String, sub: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
            .background(Color.White).clickable(onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 38.sp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = FarmColors.Ink)
            Text(sub, color = FarmColors.Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = accent, modifier = Modifier.size(32.dp))
    }
}

/** Always-visible cart button shown above the bottom bar while the cart has items. */
@Composable
fun CartBar(items: Int, total: Double, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(FarmColors.Forest, FarmColors.Leaf)))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ShoppingCart, null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$items item${if (items == 1) "" else "s"} in cart", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(rupees(total) + " + delivery", color = FarmColors.Sprout, fontSize = 14.sp)
        }
        Text("View cart", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
    }
}
