package ir.tafakoritech.tpcall.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.tafakoritech.tpcall.R
import ir.tafakoritech.tpcall.di.AppContainer
import ir.tafakoritech.tpcall.ui.components.TpBottomBar
import ir.tafakoritech.tpcall.ui.components.TpBottomNavItem
import ir.tafakoritech.tpcall.ui.contacts.ContactsScreen
import ir.tafakoritech.tpcall.ui.history.HistoryScreen
import ir.tafakoritech.tpcall.ui.home.DialerViewModel
import ir.tafakoritech.tpcall.ui.home.HomeScreen

/**
 * The authenticated home with three bottom-navigation destinations:
 * تماس (keypad, default), مخاطبین (corporate directory) and تاریخچه (call history).
 *
 * The DialerViewModel is hoisted here so tapping a history row can prefill the
 * keypad with the peer's extension while switching to the Call tab.
 */
@Composable
fun MainScaffold(
    container: AppContainer,
    onLogout: () -> Unit,
) {
    val controller = container.callController
    val authApi = container.authApi
    val token = container.sessionStore.load()?.token ?: ""

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val dialerViewModel: DialerViewModel = viewModel(
        key = "dialer_$token",
        factory = DialerViewModel.factory(controller, authApi, token),
    )

    val bottomItems = listOf(
        TpBottomNavItem(icon = painterResource(R.drawable.ic_call), label = "تماس"),
        TpBottomNavItem(icon = painterResource(R.drawable.ic_person), label = "مخاطبین"),
        TpBottomNavItem(icon = painterResource(R.drawable.ic_history), label = "تاریخچه"),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                1 -> ContactsScreen(
                    controller = controller,
                    authApi = authApi,
                    token = token,
                )
                2 -> HistoryScreen(
                    controller = controller,
                    authApi = authApi,
                    token = token,
                    onDialNumber = { extension ->
                        dialerViewModel.prefill(extension)
                        selectedTab = 0
                    },
                )
                else -> HomeScreen(
                    controller = controller,
                    dialerViewModel = dialerViewModel,
                    onLogout = onLogout,
                    deviceReadinessManager = container.deviceReadinessManager,
                )
            }
        }
        TpBottomBar(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            items = bottomItems,
        )
    }
}
