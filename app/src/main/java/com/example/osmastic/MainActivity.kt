package com.example.osmastic

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewmodel.compose.viewModel
// OSM tiles fix?
import org.osmdroid.config.Configuration
import java.io.File
// ebobaniy 2026 BOM update!
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Map
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlin.getValue
// import repo
import com.example.osmastic.repo.RepoPin
// HILT
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Singleton
import dagger.hilt.components.SingletonComponent
//meshtastic


// ⛰️⛰️⛰️ HILT module for specifying the context we need ⛰️⛰️⛰️
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideRepoPin(@ApplicationContext context: Context): RepoPin {
        return RepoPin(context)  // manual creation
    }
}
// ⛰️⛰️⛰️ HILT module for specifying the context we need ⛰️⛰️⛰️

// 📥📥📥  MAIN MODEL BATTERY 📥📥📥
data class StateGlobalModel(
    val currentDestination: AppDestinations = AppDestinations.MAP,  // bottom switcher
)

class StateGlobalViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(StateGlobalModel()) // RW, but private!
    val uiState: StateFlow<StateGlobalModel> = _uiState.asStateFlow() // readonly!

    // 🛠️🛠️🛠️ SERVICES (singleton via lazy)
    private val appContext get() = getApplication<Application>().applicationContext // GLOBAL APP CONTEXT (e.g. create a db file by correct path)
//    val repoPin by lazy { RepoPin(appContext) } // REPO not needed, HILT

    // Later: val bleManager by lazy { BleManager(appContext) }
    // Later: val meshService by lazy { MeshtasticService(appContext) }
    // 🛠️🛠️🛠️ SERVICES (singleton via lazy)

    // ⚙️⚙️⚙️ OSMDROID CONFIG INIT
    init {
        Configuration.getInstance().apply {
            userAgentValue = appContext.packageName
            osmdroidBasePath = File(appContext.cacheDir.absolutePath, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(appContext.cacheDir.absolutePath, "osmdroid/tiles").apply { mkdirs() }
        }
    }
    // ⚙️⚙️⚙️ OSMDROID CONFIG INIT


    // ➡️➡️➡️ INTERACTIVE
    // ➡️➡️➡️ INTERACTIVE
}
// 📥📥📥 MAIN MODEL BATTERY 📥📥📥

@AndroidEntryPoint
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

//@PreviewScreenSizes
//@Composable
//fun OsmasticApp() {
//    val appViewModel: StateGlobalViewModel = viewModel()
//    val stateOfModel by appViewModel.uiState.collectAsState()
//
//    val mapViewModel: StateMapViewModel = hiltViewModel()  // ← NOT viewModel()
//
//    // UI STATE PROPER NAVCONTROLLER IGNORE
//    val navController = rememberNavController()
//    val navBackStackEntry by navController.currentBackStackEntryAsState()
//    val currentDestination = navBackStackEntry?.destination
//
//    NavigationSuiteScaffold(
//        navigationSuiteItems = {
//            AppDestinations.entries.forEach { destination ->
//                item(
//                    icon = { Icon(destination.icon, destination.label) },
//                    label = { Text(destination.label) },
//                    selected = currentDestination?.route == destination.name,
//                    onClick = {
//                        // ONE LINE: navigate to enum name
//                        navController.navigate(destination.name) {
//                            // These 3 lines are the key to keeping your screen state in RAM
//                            launchSingleTop = true
//                            restoreState = true
//                            popUpTo(navController.graph.findStartDestination().id) {
//                                saveState = true
//                            }
//                        }
//                    }
//                )
//            }
//        }
//    ) { // innerPadding ->
//        // 4. NavHost with your 3 screens
//        NavHost(
//            navController = navController,
//            startDestination = AppDestinations.MAP.name,
//            modifier = Modifier.fillMaxSize()
//        ) {
//            // Each screen by enum name
//            composable(AppDestinations.PINLIST.name) {
//                ScreenLibrary(Modifier.fillMaxSize())
//            }
//            composable(AppDestinations.MAP.name) {
//                ScreenMap(viewModel = mapViewModel, Modifier.fillMaxSize())
//            }
//            composable(AppDestinations.CHANNELS.name) {
//                ScreenChannels(Modifier.fillMaxSize())
//            }
//        }
//    }
//
//} //OSMASTIC APP CLASS FINISHING BRACKET


// _____________________________________________________ //
@PreviewScreenSizes
@Composable
fun OsmasticApp() {
    val appViewModel: StateGlobalViewModel = viewModel()
    val stateOfModel by appViewModel.uiState.collectAsState()

    val mapViewModel: StateMapViewModel = hiltViewModel()  // ← NOT viewModel()


    ScreenMap(viewModel = mapViewModel, Modifier.fillMaxSize())

} //OSMASTIC APP CLASS FINISHING BRACKET
// _____________________________________________________ //

enum class AppDestinations(
    val label: String,
//    @param:DrawableRes val iconResId: Int // compiler checks if sees annotation, id must be R.drawable. thats it.
    val icon: ImageVector // well... we dont need icons as resources anymore - extended icons lib
) {
    PINLIST("Pin List", Icons.Outlined.Collections),
    MAP("Map", Icons.Outlined.Map),
    CHANNELS("Channels", Icons.Outlined.Forum),
}