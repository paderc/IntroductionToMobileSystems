package com.example.screenofficer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val TextColor = Color(0xFF163D36)
private val Teal = Color(0xFF145E52)
private val WarmWhite = Color(0xFFFFFCF5)
private val PaleGreen = Color(0xFFEDF5EE)
private val WarmAccent = Color(0xFFF5DCA6)
private val TextColorLight = Color(0xFF4C6159)
private val Outline = Color(0xFFB9C9BE)
private const val DefaultMessage = "Is this still how you want to spend your time?"
private const val MaxMessageLength = 120

private data class SampleApp(val id: String, val name: String, val sampleMinutes: Int)
private data class ReminderSettings(val minutes: Int, val message: String)

private val SampleApps = listOf(
    SampleApp("scrollspace", "ScrollSpace", 35),
    SampleApp("cliploop", "ClipLoop", 20),
)
private val MinuteChoices = listOf(15, 30, 45, 60, 90)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenOfficerTheme { ScreenOfficerApp() }
        }
    }
}

@Composable
private fun ScreenOfficerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal,
            onPrimary = Color.White,
            primaryContainer = PaleGreen,
            onPrimaryContainer = TextColor,
            secondary = TextColor,
            secondaryContainer = WarmAccent,
            onSecondaryContainer = TextColor,
            background = WarmWhite,
            onBackground = TextColor,
            surface = WarmWhite,
            onSurface = TextColor,
            surfaceVariant = PaleGreen,
            onSurfaceVariant = TextColorLight,
            outline = Outline,
            error = Color(0xFFA32C29),
        ),
        content = content,
    )
}

@Composable
private fun ScreenOfficerApp() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("screenofficer", Context.MODE_PRIVATE)
    }
    val settings = remember(preferences) {
        mutableStateMapOf<String, ReminderSettings>().apply {
            SampleApps.forEach { app ->
                if (preferences.getBoolean("${app.id}.saved", false)) {
                    val storedMinutes = preferences.getInt("${app.id}.minutes", 30)
                    put(
                        app.id,
                        ReminderSettings(
                            minutes = storedMinutes.takeIf { it in MinuteChoices } ?: 30,
                            message = preferences.getString("${app.id}.message", DefaultMessage)
                                ?.take(MaxMessageLength)?.takeIf { it.isNotBlank() }
                                ?: DefaultMessage,
                        ),
                    )
                }
            }
        }
    }
    var screen by rememberSaveable { mutableStateOf("home") }
    var selectedAppId by rememberSaveable { mutableStateOf(SampleApps.first().id) }
    var draftMinutes by rememberSaveable { mutableIntStateOf(30) }
    var draftMessage by rememberSaveable { mutableStateOf(DefaultMessage) }
    var showMessageError by rememberSaveable { mutableStateOf(false) }
    var homeNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val app = SampleApps.firstOrNull { it.id == selectedAppId } ?: SampleApps.first()
    val savedSettings = settings[app.id]

    fun leaveCurrentScreen() {
        keyboard?.hide()
        when (screen) {
            "edit" -> {
                homeNotice = "Editing cancelled. No changes were saved."
                screen = "home"
            }
            "break" -> screen = "warning"
            else -> screen = "home"
        }
    }

    BackHandler(enabled = screen != "home") { leaveCurrentScreen() }

    PrototypeFrame(
        pageKey = screen,
        onBack = if (screen == "home") null else ::leaveCurrentScreen,
        backLabel = if (screen == "edit") "Cancel" else "Back",
    ) {
        when (screen) {
            "edit" -> EditLimitScreen(
                app = app,
                minutes = draftMinutes,
                message = draftMessage,
                showMessageError = showMessageError,
                onMinutesChange = { draftMinutes = it },
                onMessageChange = {
                    draftMessage = it.take(MaxMessageLength)
                    showMessageError = false
                },
                onCancel = ::leaveCurrentScreen,
                onSave = {
                    if (draftMessage.isBlank()) {
                        showMessageError = true
                    } else {
                        val newSettings = ReminderSettings(draftMinutes, draftMessage.trim())
                        preferences.edit {
                            putBoolean("${app.id}.saved", true)
                                .putInt("${app.id}.minutes", newSettings.minutes)
                                .putString("${app.id}.message", newSettings.message)
                        }
                        settings[app.id] = newSettings
                        keyboard?.hide()
                        homeNotice = "Reminder saved for ${app.name}. It stays on this device after an app restart."
                        screen = "home"
                    }
                },
            )
            "warning" -> WarningScreen(
                app = app,
                settings = savedSettings ?: ReminderSettings(30, DefaultMessage),
                onBreak = { screen = "break" },
                onContinue = {
                    homeNotice = "You chose to continue in the preview. Your reminder settings are unchanged."
                    screen = "home"
                },
            )
            "break" -> BreakScreen(
                onReturn = {
                    homeNotice = "Break planned in the preview. No elapsed break time has been measured."
                    screen = "home"
                },
            )
            else -> HomeScreen(
                selectedAppId = app.id,
                settings = settings,
                notice = homeNotice,
                onSelectApp = {
                    selectedAppId = it
                    homeNotice = null
                },
                onEdit = {
                    draftMinutes = savedSettings?.minutes ?: 30
                    draftMessage = savedSettings?.message ?: DefaultMessage
                    showMessageError = false
                    homeNotice = null
                    screen = "edit"
                },
                onPreview = {
                    homeNotice = null
                    screen = "warning"
                },
            )
        }
    }
}

@Composable
private fun PrototypeFrame(
    pageKey: String = "preview",
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = WarmWhite,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WarmWhite)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ScreenOfficer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Your phone, your choice", style = MaterialTheme.typography.bodySmall, color = TextColorLight)
                    }
                    if (onBack != null) {
                        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(backLabel)
                        }
                    }
                }
                Surface(color = PaleGreen, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text("Prototype · Sample data", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "No live monitoring, blocking or phone permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextColorLight,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        key(pageKey) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                content()
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun PageHeading(eyebrow: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow.uppercase(), color = Teal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(description, style = MaterialTheme.typography.bodyLarge, color = TextColorLight)
    }
}

@PreviewScreenSizes
@Composable
fun ScreenOfficerApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Greeting(
                name = "User",
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    FAVORITES("Favorites", R.drawable.ic_favorite),
    PROFILE("Profile", R.drawable.ic_account_box),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ScreenOfficerTheme {
        Column(){
            Text("User")
            Text("User")
        }
    }
}