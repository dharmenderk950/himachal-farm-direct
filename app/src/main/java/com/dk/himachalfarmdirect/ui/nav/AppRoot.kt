package com.dk.himachalfarmdirect.ui.nav

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dk.himachalfarmdirect.data.repo.AppMode
import com.dk.himachalfarmdirect.data.repo.UserRepository
import com.dk.himachalfarmdirect.ui.account.AccountNav
import com.dk.himachalfarmdirect.ui.account.AccountScreen
import com.dk.himachalfarmdirect.ui.account.CategoriesScreen
import com.dk.himachalfarmdirect.ui.account.OrdersScreen
import com.dk.himachalfarmdirect.ui.auth.LoginScreen
import com.dk.himachalfarmdirect.ui.help.HelpArticleScreen
import com.dk.himachalfarmdirect.ui.help.HelpCentreScreen
import com.dk.himachalfarmdirect.ui.help.SupportFormScreen
import com.dk.himachalfarmdirect.ui.help.TicketsScreen
import com.dk.himachalfarmdirect.ui.home.HomeNav
import com.dk.himachalfarmdirect.ui.home.HomeScreen
import com.dk.himachalfarmdirect.ui.location.LocationScreen
import com.dk.himachalfarmdirect.ui.product.CartScreen
import com.dk.himachalfarmdirect.ui.product.FarmerScreen
import com.dk.himachalfarmdirect.ui.product.NotificationsScreen
import com.dk.himachalfarmdirect.ui.product.ProductDetailScreen
import com.dk.himachalfarmdirect.ui.product.ProductListScreen
import com.dk.himachalfarmdirect.ui.product.WishlistScreen
import com.dk.himachalfarmdirect.ui.seller.FarmProfileScreen
import com.dk.himachalfarmdirect.ui.seller.MyProductsScreen
import com.dk.himachalfarmdirect.ui.seller.ProductEditScreen
import com.dk.himachalfarmdirect.ui.seller.SellerHomeScreen
import com.dk.himachalfarmdirect.ui.theme.FarmColors
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(private val users: UserRepository) : ViewModel() {
    /** null = still checking, false = signed out. */
    val auth = users.authState.map { u: FirebaseUser? -> u != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val mode = users.mode.stateIn(viewModelScope, SharingStarted.Eagerly, AppMode.BUYER)

    fun onSignedIn(name: String) = viewModelScope.launch { runCatching { users.ensureProfile(name) } }
    fun refreshProfile() = viewModelScope.launch { runCatching { users.ensureProfile() } }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppRoot(openTarget: String?) {
    val vm: RootViewModel = hiltViewModel()
    val signedIn by vm.auth.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val nav = rememberNavController()

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(signedIn) {
        if (signedIn == true) {
            vm.refreshProfile()
            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    when (signedIn) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        false -> NavHost(nav, startDestination = "login") {
            composable("login") { LoginScreen(onSignedIn = vm::onSignedIn, onHelp = { nav.navigate("help/article/b_login") }) }
            helpGraph(nav, signedIn = false)
        }
        true -> MainScaffold(nav, mode, openTarget)
    }
}

@Composable
private fun MainScaffold(nav: NavHostController, mode: AppMode, openTarget: String?) {
    val seller = mode == AppMode.SELLER
    // Spec B: Home, Categories, Orders, Account. In Seller mode the same 4 slots show seller tools.
    val tabs = listOf(
        Tab("home", "Home", if (seller) Icons.Default.Dashboard else Icons.Default.Home),
        Tab("categories", if (seller) "Products" else "Categories", if (seller) Icons.Default.Inventory2 else Icons.Default.Category),
        Tab("orders", "Orders", Icons.Default.ReceiptLong),
        Tab("account", "Account", Icons.Default.Person),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    LaunchedEffect(openTarget) {
        when (openTarget) {
            "tickets" -> nav.navigate("tickets")
            "orders" -> nav.navigate("orders")
            "nearby" -> nav.navigate("list/nearbyFarmers")
            "notifications" -> nav.navigate("notifications")
        }
    }

    Scaffold(
        bottomBar = {
            if (current in tabs.map { it.route }) NavigationBar(containerColor = Color.White) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = FarmColors.Forest, indicatorColor = FarmColors.Mint),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
            composable("home") {
                if (seller) SellerHomeScreen(
                    openProducts = { nav.navigate("categories") }, openOrders = { nav.navigate("orders") },
                    openFarmProfile = { nav.navigate("farmProfile") }, openHelp = { nav.navigate("help") },
                ) else HomeScreen(
                    HomeNav(
                        openLocation = { nav.navigate("location") },
                        openSearch = { nav.navigate("list/search") },
                        openCategory = { nav.navigate("list/cat:$it") },
                        openList = { nav.navigate("list/$it") },
                        openProduct = { nav.navigate("product/$it") },
                        openFarmer = { nav.navigate("farmer/$it") },
                        openCart = { nav.navigate("cart") },
                        openWishlist = { nav.navigate("wishlist") },
                        openNotifications = { nav.navigate("notifications") },
                        openHelp = { nav.navigate("help") },
                    )
                )
            }
            composable("categories") {
                if (seller) MyProductsScreen(openEdit = { id -> nav.navigate("productEdit/${id ?: "new"}") })
                else CategoriesScreen(openCategory = { nav.navigate("list/cat:$it") })
            }
            composable("orders") { OrdersScreen() }
            composable("account") {
                AccountScreen(
                    AccountNav(
                        openLocation = { nav.navigate("location") },
                        openWishlist = { nav.navigate("wishlist") },
                        openNotifications = { nav.navigate("notifications") },
                        openHelp = { nav.navigate("help") },
                        openTickets = { nav.navigate("tickets") },
                        openFarmProfile = { nav.navigate("farmProfile") },
                    )
                )
            }
            composable("location") { LocationScreen(onBack = { nav.popBackStack() }, onDone = { nav.popBackStack() }) }
            composable("list/{kind}", listOf(navArgument("kind") { type = NavType.StringType })) {
                ProductListScreen(onBack = { nav.popBackStack() }, openProduct = { nav.navigate("product/$it") }, openFarmer = { nav.navigate("farmer/$it") })
            }
            composable("product/{productId}") {
                ProductDetailScreen(
                    onBack = { nav.popBackStack() }, openFarmer = { nav.navigate("farmer/$it") },
                    openProduct = { nav.navigate("product/$it") }, openCart = { nav.navigate("cart") },
                )
            }
            composable("farmer/{farmerId}") { FarmerScreen(onBack = { nav.popBackStack() }, openProduct = { nav.navigate("product/$it") }) }
            composable("cart") {
                CartScreen(onBack = { nav.popBackStack() }, openLocation = { nav.navigate("location") },
                    openOrders = { nav.navigate("orders") { popUpTo("home") } })
            }
            composable("wishlist") { WishlistScreen(onBack = { nav.popBackStack() }, openProduct = { nav.navigate("product/$it") }) }
            composable("notifications") {
                NotificationsScreen(onBack = { nav.popBackStack() }, open = { target ->
                    when (target) {
                        "tickets" -> nav.navigate("tickets")
                        "orders" -> nav.navigate("orders")
                        "nearby" -> nav.navigate("list/nearbyFarmers")
                        else -> if (target.startsWith("farmer/") || target.startsWith("product/")) nav.navigate(target)
                    }
                })
            }
            composable("farmProfile") { FarmProfileScreen(onBack = { nav.popBackStack() }) }
            composable("productEdit/{productId}") { ProductEditScreen(onBack = { nav.popBackStack() }) }
            composable("tickets") { TicketsScreen(onBack = { nav.popBackStack() }, openForm = { nav.navigate("support?subject=") }) }
            helpGraph(nav, signedIn = true)
        }
    }
}

/** Help Centre routes — reachable signed-in (Home, Account) and signed-out (login screen). */
private fun androidx.navigation.NavGraphBuilder.helpGraph(nav: NavHostController, signedIn: Boolean) {
    composable("help") {
        HelpCentreScreen(
            signedIn = signedIn,
            onBack = { nav.popBackStack() },
            openArticle = { nav.navigate("help/article/$it") },
            openForm = { nav.navigate("support?subject=${android.net.Uri.encode(it)}") },
            openTickets = { nav.navigate("tickets") },
        )
    }
    composable("help/article/{id}") { e ->
        HelpArticleScreen(e.arguments?.getString("id").orEmpty(), signedIn, onBack = { nav.popBackStack() },
            openForm = { nav.navigate("support?subject=${android.net.Uri.encode(it)}") })
    }
    composable("support?subject={subject}", listOf(navArgument("subject") { type = NavType.StringType; defaultValue = "" })) { e ->
        SupportFormScreen(e.arguments?.getString("subject").orEmpty(), onBack = { nav.popBackStack() },
            openTickets = { nav.navigate("tickets") { popUpTo("help") } })
    }
}
