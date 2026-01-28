package com.example.osmastic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.example.osmastic.ui.theme.OsmasticTheme
//ight, new ones:
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.util.GeoPoint
// OSM tiles fix?
import org.osmdroid.config.Configuration
import java.io.File
// ebobaniy 2026 BOM update!
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Map

// !!!!!!!!!!!! MAIN MODEL BATTERY
data class StateGlobalModel(
    val testCounter: Int = 0,
    val currentDestination: AppDestinations = AppDestinations.MAP,  // bottom switcher

    val mapCenter: GeoPoint = GeoPoint(59.9343, 30.3351), // default loc, SPB
    val mapZoom: Double = 12.0 // obvious
)

class StateGlobalViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StateGlobalModel()) // RW, but private!
    val uiState: StateFlow<StateGlobalModel> = _uiState.asStateFlow() // readonly!

    fun navigateTo(destination: AppDestinations) {
        _uiState.value = _uiState.value.copy(currentDestination = destination)
    }
    fun updateMapPosition(center: GeoPoint, zoom: Double) {
        _uiState.value = _uiState.value.copy(
            mapCenter = center,
            mapZoom = zoom
        )
    }

//    fun incrementTestCounter() {
//        _uiState.value = _uiState.value.copy(testCounter = _uiState.value.testCounter + 1)
//    }
}
// !!!!!!!!!!!! MAIN MODEL BATTERY


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir.absolutePath, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(cacheDir.absolutePath, "osmdroid/tiles").apply { mkdirs() }
        }

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
//                            imageVector = ImageVector.vectorResource(id = it.iconResId),
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
                AppDestinations.PINLIST -> ScreenLibrary(modifier = Modifier.padding(innerPadding))
                AppDestinations.MAP -> ScreenMap(modifier = Modifier.padding(innerPadding))
                AppDestinations.CHANNELS -> ScreenChannels(modifier = Modifier.padding(innerPadding))
            }

        }
    }
}

enum class AppDestinations(
    val label: String,
//    @param:DrawableRes val iconResId: Int // compiler checks if sees annotation, id must be R.drawable. thats it.
    val icon: ImageVector // well... we dont need icons as resources anymore - extended icons lib
) {
    PINLIST("Pin List", Icons.Outlined.Collections),
    MAP("Map", Icons.Outlined.Map),
    CHANNELS("Channels", Icons.Outlined.Forum),
}


