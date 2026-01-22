package com.example.osmastic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.sp
import com.example.osmastic.ui.theme.OsmasticTheme
import java.nio.channels.Channels
//ight, new ones:
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.util.GeoPoint

// !!!!!!!!!!!! MAIN MODEL BATTERY
data class StateGlobalModel(
    val testCounter: Int = 0,
    val currentDestination: AppDestinations = AppDestinations.MAP  // bottom switcher
)

class StateGlobalViewModel : ViewModel() {
    // !!!!!!!!!!!!!!!! Single source of truth for global state
    private val _uiState = MutableStateFlow(StateGlobalModel()) // RW, but private!
    val uiState: StateFlow<StateGlobalModel> = _uiState.asStateFlow() // readonly!

    fun incrementCounter() {
        _uiState.value = _uiState.value.copy(testCounter = _uiState.value.testCounter + 1)
    }

    fun navigateTo(destination: AppDestinations) {
        _uiState.value = _uiState.value.copy(currentDestination = destination)
    }

}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OsmasticTheme {
                OsmasticApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun OsmasticApp() {
//    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.MAP) }
    val appViewModel: StateGlobalViewModel = viewModel()
    val uiState by appViewModel.uiState.collectAsState()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
//                    selected = it == currentDestination,
//                    onClick = { currentDestination = it }
                    selected = it == uiState.currentDestination,  // ← CHANGE HERE
                    onClick = { appViewModel.navigateTo(it) }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

//            when (currentDestination) {
            when (uiState.currentDestination) {
                AppDestinations.LIBRARY -> ScreenLibrary(modifier = Modifier.padding(innerPadding))
                AppDestinations.MAP -> ScreenMap(modifier = Modifier.padding(innerPadding))
                AppDestinations.CHANNELS -> ScreenChannels(modifier = Modifier.padding(innerPadding))
            }

        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    LIBRARY("Library", Icons.Default.Create ),
    MAP("Map", Icons.Default.Place ),
    CHANNELS("Channels", Icons.Default.Menu ),
}


