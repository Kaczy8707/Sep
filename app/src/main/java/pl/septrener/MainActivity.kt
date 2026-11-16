package pl.septrener

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pl.septrener.content.Group
import pl.septrener.content.Scope
import pl.septrener.exam.ExamConfig
import pl.septrener.exam.QuestionPool
import pl.septrener.ui.components.MiniPlayer
import pl.septrener.ui.components.rememberNotificationGate
import pl.septrener.ui.screens.ExamSessionScreen
import pl.septrener.ui.screens.ExamSetupScreen
import pl.septrener.ui.screens.LessonScreen
import pl.septrener.ui.screens.LibraryScreen
import pl.septrener.ui.screens.NowPlayingScreen
import pl.septrener.ui.screens.ProgressScreen
import pl.septrener.ui.screens.QuestionScreen
import pl.septrener.ui.screens.SearchScreen
import pl.septrener.ui.screens.SettingsScreen
import pl.septrener.ui.screens.SourcesScreen
import pl.septrener.ui.theme.SepTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as SepApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SepTheme { SepRoot(container) } }
    }

    override fun onStart() {
        super.onStart()
        container.connection.connect()
    }

    override fun onStop() {
        // Odłączenie kontrolera nie zatrzymuje odtwarzania – usługa pierwszoplanowa działa dalej.
        container.connection.release()
        super.onStop()
    }
}

private data class TopDest(val route: String, val label: String, val icon: ImageVector)

private val topDestinations = listOf(
    TopDest("library", "Nauka", Icons.AutoMirrored.Filled.MenuBook),
    TopDest("exam", "Egzamin", Icons.Filled.RecordVoiceOver),
    TopDest("progress", "Postępy", Icons.Filled.BarChart),
    TopDest("settings", "Ustawienia", Icons.Filled.Settings),
)

@Composable
fun SepRoot(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val playback by container.playback.collectAsStateWithLifecycle()
    val settings by container.settings.collectAsStateWithLifecycle()
    val gate = rememberNotificationGate(container.settingsStore, settings.notificationsAsked)
    val showMini = playback.hasPlaylist && route != "nowplaying" && route?.startsWith("exam/session") != true &&
        !(route == "lesson/{id}" && backStack?.arguments?.getString("id") == playback.track?.itemId)

    Scaffold(
        bottomBar = {
            Column {
                if (showMini) MiniPlayer(
                    state = playback,
                    onOpen = { nav.navigate("nowplaying") },
                    onPlayPause = { if (playback.playing) container.connection.pause() else gate { container.connection.play() } },
                    onNext = { container.connection.next() },
                )
                NavigationBar {
                    topDestinations.forEach { d ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == d.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateTop(d.route) },
                            icon = { Icon(d.icon, contentDescription = null) },
                            label = { Text(d.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val openSettings = { nav.navigateTop("settings") }
        NavHost(nav, startDestination = "library") {
            composable("library") {
                LibraryScreen(container, gate, onOpenLesson = { nav.navigate("lesson/$it") }, onSearch = { nav.navigate("search") }, onOpenSettings = openSettings, contentPadding = padding)
            }
            composable("lesson/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                LessonScreen(
                    container, id, gate, onBack = { nav.popBackStack() },
                    onOpenLesson = { other -> nav.navigate("lesson/$other") { popUpTo("lesson/{id}") { inclusive = true } } },
                    onOpenSettings = openSettings, outerPadding = padding,
                    onOpenQuestion = { nav.navigate("question/$it") },
                )
            }
            composable("sources") { SourcesScreen(container, onBack = { nav.popBackStack() }, outerPadding = padding) }
            composable("search") {
                SearchScreen(container, onBack = { nav.popBackStack() }, onOpenLesson = { nav.navigate("lesson/$it") }, onOpenQuestion = { nav.navigate("question/$it") }, outerPadding = padding)
            }
            composable("question/{id}") { entry ->
                QuestionScreen(container, entry.arguments?.getString("id").orEmpty(), gate, onBack = { nav.popBackStack() }, onOpenLesson = { nav.navigate("lesson/$it") }, outerPadding = padding)
            }
            composable("exam") {
                ExamSetupScreen(
                    container, gate,
                    onStart = { c -> nav.navigate("exam/session/${c.group?.code ?: "ALL"}/${c.scope?.name ?: "ALL"}/${c.pool.name}/${c.shuffle}/${c.oral}/${c.scenarios}") },
                    onListen = { nav.navigate("nowplaying") },
                    onOpenSettings = openSettings, outerPadding = padding,
                )
            }
            composable("exam/session/{group}/{scope}/{pool}/{shuffle}/{oral}/{scenarios}") { entry ->
                val a = entry.arguments
                val config = ExamConfig(
                    group = a?.getString("group")?.let { Group.fromCode(it) },
                    scope = a?.getString("scope")?.let { n -> Scope.entries.firstOrNull { it.name == n } },
                    pool = a?.getString("pool")?.let { n -> QuestionPool.entries.firstOrNull { it.name == n } } ?: QuestionPool.ALL,
                    shuffle = a?.getString("shuffle") != "false",
                    oral = a?.getString("oral") != "false",
                    scenarios = a?.getString("scenarios") == "true",
                )
                ExamSessionScreen(container, config, onBack = { nav.popBackStack() }, onOpenSettings = openSettings, outerPadding = padding)
            }
            composable("progress") { ProgressScreen(container, onOpenQuestion = { nav.navigate("question/$it") }, outerPadding = padding) }
            composable("settings") { SettingsScreen(container, outerPadding = padding, onOpenSources = { nav.navigate("sources") }) }
            composable("nowplaying") {
                NowPlayingScreen(container, gate, onBack = { nav.popBackStack() }, onOpenLesson = { nav.navigate("lesson/$it") }, outerPadding = padding)
            }
        }
    }
}

private fun NavHostController.navigateTop(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
