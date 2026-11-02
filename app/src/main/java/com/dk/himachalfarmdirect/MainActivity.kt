package com.dk.himachalfarmdirect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dk.himachalfarmdirect.ui.nav.AppRoot
import com.dk.himachalfarmdirect.ui.splash.CreditSplash
import com.dk.himachalfarmdirect.ui.theme.FarmTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openTarget = intent?.getStringExtra("open")
        setContent {
            FarmTheme {
                var splashDone by rememberSaveable { mutableStateOf(false) }
                if (!splashDone) CreditSplash { splashDone = true }
                else AppRoot(openTarget = remember { openTarget })
            }
        }
    }
}
