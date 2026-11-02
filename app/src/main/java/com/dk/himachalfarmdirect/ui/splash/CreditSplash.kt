package com.dk.himachalfarmdirect.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dk.himachalfarmdirect.R
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import kotlinx.coroutines.delay

/** Brief DK credit shown on every launch (tap to skip). */
@Composable
fun CreditSplash(onDone: () -> Unit) {
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(500))
        delay(1800)
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FarmColors.Forest, FarmColors.Leaf)))
            .clickable(onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.alpha(fade.value).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(112.dp))
            Spacer(Modifier.height(16.dp))
            Text("Himachal Farm Direct", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Fresh from the farmer, near you", color = FarmColors.Sprout, fontSize = 15.sp)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).alpha(fade.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Developed by DK", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("dharmenderk950@gmail.com", color = FarmColors.Sprout, fontSize = 13.sp, textAlign = TextAlign.Center)
            Text("WhatsApp: dkc@1978", color = FarmColors.Sprout, fontSize = 13.sp)
        }
    }
}
