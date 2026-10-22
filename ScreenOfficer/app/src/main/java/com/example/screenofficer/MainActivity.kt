package com.example.screenofficer

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.*
import androidx.activity.compose.*
import androidx.core.content.edit
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.screenofficer.ui.theme.ScreenOfficerTheme
private val TextColor = Color(0xFF163D36)
private val Teal = Color(0xFF145E52)
private val WarmWhite = Color(0xFFFFFCF5)
private val PaleGreen = Color(0xFFEDF5EE)
private val WarmAccent = Color(0xFFF5DCA6)
private val TextColorLight = Color(0xFF4C6159)
private val Outline = Color(0xFFB9C9BE)
private val DeepDark = Color(0xFF121212)
private val DarkSurfaceVariant = Color(0xFF1E2B27)
private val DarkPrimaryContainer = Color(0xFF1F2E29)
private val DarkSecondaryContainer = Color(0xFF3A2F1E)
private val DarkOutline = Color(0xFF3E4E47)
private val DarkOnSurfaceVariant = Color(0xFFC7D5CC)
private val DarkError = Color(0xFFF2B8B5)
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
            ScreenOfficerTheme{
                ScreenOfficerApp()
            }
        }
    }
}

@Composable
private fun ScreenOfficerTheme(content: @Composable () -> Unit) {
    var mode : ColorScheme
    val isDarkMode = isSystemInDarkTheme()
    Log.d("ScreenOfficer", "isDarkMode=$isDarkMode")
    if (isDarkMode) {
        mode = darkColorScheme(
            primary              = Teal,
            onPrimary            = Color.White,
            primaryContainer     = DarkPrimaryContainer,
            onPrimaryContainer   = WarmWhite,
            secondary            = WarmAccent,
            onSecondary          = TextColor,
            secondaryContainer   = DarkSecondaryContainer,
            onSecondaryContainer = WarmAccent,
            background           = DeepDark,
            onBackground         = WarmWhite,
            surface              = DeepDark,
            onSurface            = WarmWhite,
            surfaceVariant       = DarkSurfaceVariant,
            onSurfaceVariant     = DarkOnSurfaceVariant,
            outline              = DarkOutline,
            error                = DarkError,
        )
    }
    else {
        mode = lightColorScheme(
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
        )
    }
    MaterialTheme(
        mode,
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
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
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
                        Text("Your phone, your choice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (onBack != null) {
                        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(backLabel)
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text("Prototype · Sample data", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "No live monitoring, blocking or phone permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Text(eyebrow.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeScreen(
    selectedAppId: String,
    settings: Map<String, ReminderSettings>,
    notice: String?,
    onSelectApp: (String) -> Unit,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
) {
    PageHeading(
        eyebrow = "01 / Your overview",
        title = "Make room for what matters.",
        description = "Choose a sample app and shape a reminder that sounds like you.",
    )
    if (notice != null) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
            Text(
                notice,
                modifier = Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
        Text("Choose one sample app", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        SampleApps.forEach { app ->
            SampleAppCard(
                app = app,
                settings = settings[app.id],
                selected = app.id == selectedAppId,
                onSelect = { onSelectApp(app.id) },
            )
        }
    }
    val selectedApp = SampleApps.first { it.id == selectedAppId }
    val selectedSettings = settings[selectedAppId]
    if (selectedSettings != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your reminder message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("“${selectedSettings.message}”", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (selectedSettings == null) "Set reminder for ${selectedApp.name}" else "Edit reminder for ${selectedApp.name}")
        }
        OutlinedButton(
            onClick = onPreview,
            enabled = selectedSettings != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text("Preview warning")
        }
        Text(
            if (selectedSettings == null) "Save a reminder to try the warning preview."
            else "Preview opens a simulated warning immediately. Sample usage stays fixed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Text(
        "You choose when to pause. This prototype explores gentle reminders and makes no claim to reduce phone use.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SampleAppCard(app: SampleApp, settings: ReminderSettings?, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Today · ${app.sampleMinutes} min (sample)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (settings == null) "No reminder saved" else "Reminder at ${settings.minutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(8.dp))
            RadioButton(selected = selected, onClick = null, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun EditLimitScreen(
    app: SampleApp,
    minutes: Int,
    message: String,
    showMessageError: Boolean,
    onMinutesChange: (Int) -> Unit,
    onMessageChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    PageHeading(
        eyebrow = "02 / Edit reminder",
        title = "A reminder, on your terms.",
        description = "Choose a daily threshold for ${app.name}. In this prototype, the threshold is a saved preference, not an enforced limit.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.selectableGroup()) {
        Text("Daily reminder threshold", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MinuteChoices.forEach { choice ->
                Surface(
                    modifier = Modifier
                        .widthIn(min = 96.dp)
                        .heightIn(min = 52.dp)
                        .selectable(selected = minutes == choice, onClick = { onMinutesChange(choice) }, role = Role.RadioButton)
                        .semantics(mergeDescendants = true) {},
                    color = if (minutes == choice) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(if (minutes == choice) 2.dp else 1.dp, if (minutes == choice) Teal else Outline),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$choice min",
                            modifier = Modifier.clearAndSetSemantics { contentDescription = "$choice minutes" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (minutes == choice) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
    OutlinedTextField(
        value = message,
        onValueChange = onMessageChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Your reminder message") },
        minLines = 2,
        maxLines = 4,
        isError = showMessageError,
        supportingText = {
            Column {
                if (showMessageError) Text("Enter a message before saving.")
                Text("${message.length}/$MaxMessageLength characters")
            }
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(16.dp),
    )
    Text("Saved only on this device. No account, network or real app-usage access is needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Save reminder") }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel changes") }
    }
}

@Composable
private fun WarningScreen(app: SampleApp, settings: ReminderSettings, onBreak: () -> Unit, onContinue: () -> Unit) {
    PageHeading(
        eyebrow = "03 / Warning preview",
        title = "Time for a check-in?",
        description = "Imagine ${app.name} has reached your ${settings.minutes}-minute daily threshold.",
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${settings.minutes} min · Your chosen threshold", style = MaterialTheme.typography.bodyMedium)
            Text("“${settings.message}”", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        }
    }
    Text(
        "Demo trigger: you tapped “Preview warning”. The fixed sample usage did not trigger this screen, and no other app has been interrupted.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onBreak, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Plan a break") }
        OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Continue for now") }
        Text("The choice stays yours. Both options return control to you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BreakScreen(onReturn: () -> Unit) {
    PageHeading(
        eyebrow = "04 / Break plan",
        title = "Break planned.",
        description = "You chose a pause in the preview. When you are ready, put your phone down and step away for a moment.",
    )
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(56.dp).clearAndSetSemantics {},
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("II", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Text("A small next step", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Stretch, look away from the screen, or return to something you meant to do.", style = MaterialTheme.typography.bodyLarge)
        }
    }
    Text(
        "This is an acknowledgement of your choice, not a completed break. The prototype does not run a timer or measure time away from your phone.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onReturn, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Return to overview") }
}

@Preview(name = "01 Overview", showBackground = true)
@Composable
private fun HomePreview() {
    ScreenOfficerTheme() {
        PrototypeFrame {
            HomeScreen(
                selectedAppId = "scrollspace",
                settings = mapOf("scrollspace" to ReminderSettings(30, DefaultMessage)),
                notice = null,
                onSelectApp = {}, onEdit = {}, onPreview = {},
            )
        }
    }
}

@Preview(name = "02 Set reminder", showBackground = true)
@Composable
private fun EditPreview() {
    ScreenOfficerTheme {
        PrototypeFrame(onBack = {}, backLabel = "Cancel") {
            EditLimitScreen(SampleApps.first(), 30, DefaultMessage, false, {}, {}, {}, {})
        }
    }
}

@Preview(name = "03 Warning preview", showBackground = true)
@Composable
private fun WarningPreview() {
    ScreenOfficerTheme {
        PrototypeFrame(onBack = {}) {
            WarningScreen(SampleApps.first(), ReminderSettings(30, DefaultMessage), {}, {})
        }
    }
}

@Preview(name = "04 Break acknowledgement", showBackground = true)
@Composable
private fun BreakPreview() {
    ScreenOfficerTheme { PrototypeFrame(onBack = {}) { BreakScreen {} } }
}
