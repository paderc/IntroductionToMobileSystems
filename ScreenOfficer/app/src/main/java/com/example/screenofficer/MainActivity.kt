package com.example.screenofficer

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OfficerTheme { ScreenOfficerApp(lifecycle) } }
    }
}

@Composable
private fun OfficerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF9BD5C4), onPrimary = Color(0xFF00382E),
        primaryContainer = Color(0xFF1F3B32), onPrimaryContainer = Color(0xFFFFFCF5),
        secondaryContainer = Color(0xFF3A2F1E), onSecondaryContainer = Color(0xFFF5DCA6),
        background = Color(0xFF121814), surface = Color(0xFF121814),
        onBackground = Color(0xFFFFFCF5), onSurface = Color(0xFFFFFCF5),
        surfaceVariant = Color(0xFF24372E), onSurfaceVariant = Color(0xFFC7D5CC),
        outline = Color(0xFF81958A),
    ) else lightColorScheme(
        primary = Color(0xFF145E52), onPrimary = Color.White,
        primaryContainer = Color(0xFFEDF5EE), onPrimaryContainer = Color(0xFF163D36),
        secondaryContainer = Color(0xFFF5DCA6), onSecondaryContainer = Color(0xFF163D36),
        background = Color(0xFFFFFCF5), surface = Color(0xFFFFFCF5),
        onBackground = Color(0xFF163D36), onSurface = Color(0xFF163D36),
        surfaceVariant = Color(0xFFEDF5EE), onSurfaceVariant = Color(0xFF4C6159),
        outline = Color(0xFF74867B),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun ScreenOfficerApp(lifecycle: Lifecycle) {
    val context = LocalContext.current
    val store = remember { RuleStore(context.applicationContext) }
    val repository = remember { UsageRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var rules by remember { mutableStateOf(emptyList<AppRule>()) }
    var apps by remember { mutableStateOf(emptyList<TrackedApp>()) }
    var appsLoading by remember { mutableStateOf(true) }
    var appsError by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<UsageSnapshot?>(null) }
    var usageAllowed by remember { mutableStateOf(false) }
    var serviceConnected by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf("home") }
    var selectedPackage by rememberSaveable { mutableStateOf("") }
    var selectedLabel by rememberSaveable { mutableStateOf("") }
    var draftMinutes by rememberSaveable { mutableStateOf("30") }
    var draftMessage by rememberSaveable { mutableStateOf(DEFAULT_MESSAGE) }
    var draftMode by rememberSaveable { mutableStateOf(BlockMode.SOFT.name) }
    var draftEnabled by rememberSaveable { mutableStateOf(true) }
    var validation by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var permissionDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var clearDialog by rememberSaveable { mutableStateOf(false) }
    var removePackage by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshVersion++ }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(resumed, refreshVersion) {
        if (!resumed) return@LaunchedEffect
        do {
            refreshing = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val currentRules = store.allRules()
                    Triple(currentRules, repository.hasAccess(), repository.snapshot(currentRules.map { it.packageName }.toSet()))
                }
                rules = result.first
                usageAllowed = result.second
                snapshot = result.third
                serviceConnected = AppBlockingService.connected
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snapshot = null
                usageAllowed = false
                serviceConnected = AppBlockingService.connected
                notice = "Usage could not be updated. Try Refresh."
            } finally {
                refreshing = false
            }
            delay(3_000)
        } while (resumed)
    }
    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        appsLoading = true
        appsError = null
        try {
            apps = withContext(Dispatchers.IO) { AppCatalog(context.applicationContext).loadApps() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            appsError = "Installed apps could not be loaded. Return to this screen to try again."
        } finally {
            appsLoading = false
        }
    }

    fun editRule(packageName: String, label: String) {
        val rule = rules.firstOrNull { it.packageName == packageName }
        selectedPackage = packageName
        selectedLabel = label
        draftMinutes = (rule?.limitMinutes ?: 30).toString()
        draftMessage = rule?.message ?: DEFAULT_MESSAGE
        draftMode = (rule?.mode ?: BlockMode.SOFT).name
        draftEnabled = rule?.enabled ?: true
        validation = false
        notice = null
        screen = "edit"
    }
    fun changeRule(action: () -> Unit, success: String) {
        scope.launch {
            saving = true
            try {
                withContext(Dispatchers.IO) { action() }
                refreshVersion++
                notice = success
                keyboard?.hide()
                screen = "home"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notice = "Your change could not be saved. Please try again."
            } finally { saving = false }
        }
    }
    BackHandler(screen != "home" && !saving) {
        keyboard?.hide()
        screen = "home"
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("ScreenOfficer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Your phone, your choice", style = MaterialTheme.typography.bodySmall)
                }
                if (screen != "home") TextButton(onClick = { keyboard?.hide(); screen = "home" }, enabled = !saving) { Text("Cancel") }
            }
        },
    ) { padding ->
        key(screen) {
            Column(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                notice?.let {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Text(it, Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                when (screen) {
                    "picker" -> {
                        Heading("Choose an app", "Select an installed app to track and limit its daily use.")
                        OutlinedTextField(
                            value = search, onValueChange = { search = it }, label = { Text("Search apps") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                        if (appsLoading) Text("Loading installed apps…")
                        appsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        val matching = apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
                        if (!appsLoading && matching.isEmpty()) Text("No matching apps. Phone, Settings and the home screen are always available.")
                        matching.forEach { app ->
                            OutlinedButton(onClick = { editRule(app.packageName, app.label) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    "edit" -> {
                        Heading("A limit on your terms.", "Set a daily limit for $selectedLabel. Usage resets at midnight in your phone's local time.")
                        OutlinedTextField(
                            value = draftMinutes,
                            onValueChange = { draftMinutes = it.filter(Char::isDigit).take(4) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Daily limit in minutes") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = validation && (draftMinutes.toIntOrNull()?.let { it in 1..1440 } != true),
                            supportingText = { Text("Choose 1–1440 minutes. Today's measured use counts toward this limit.") },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(15, 30, 60, 90).forEach { minutes ->
                                FilterChip(selected = draftMinutes == minutes.toString(), onClick = { draftMinutes = minutes.toString() }, label = { Text("$minutes min") }, modifier = Modifier.heightIn(min = 48.dp))
                            }
                        }
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("When the limit is reached", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            ModeChoice("Soft block", "Pause with a reminder. You can choose five more minutes of app use.", draftMode == BlockMode.SOFT.name) { draftMode = BlockMode.SOFT.name }
                            ModeChoice("Hard block", "The app stays blocked until midnight. There is no continue button.", draftMode == BlockMode.HARD.name) { draftMode = BlockMode.HARD.name }
                        }
                        OutlinedTextField(
                            value = draftMessage, onValueChange = { draftMessage = it.take(120) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("Your reminder message") }, minLines = 2, maxLines = 4,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            isError = validation && draftMessage.isBlank(),
                            supportingText = { Text(if (validation && draftMessage.isBlank()) "Enter a reminder message." else "${draftMessage.length}/120 characters") },
                        )
                        Text("You can edit, pause or remove either limit in ScreenOfficer. Blocking needs Usage Access and the ScreenOfficer accessibility service.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                validation = true
                                val minutes = draftMinutes.toIntOrNull()
                                if (minutes != null && minutes in 1..1440 && draftMessage.isNotBlank()) {
                                    val rule = AppRule(selectedPackage, selectedLabel, minutes, draftMessage.trim(), BlockMode.valueOf(draftMode), draftEnabled)
                                    changeRule({ store.save(rule) }, "Limit saved for $selectedLabel.")
                                }
                            },
                            enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) { Text(if (saving) "Saving…" else "Save limit") }
                    }
                    else -> {
                        Heading("Make room for what matters.", "See today's measured app use and choose when to pause.")
                        AccessCard(
                            usageAllowed, serviceConnected,
                            onUsage = { permissionDialog = "usage" },
                            onBlocking = { permissionDialog = "blocking" },
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                val current = snapshot
                                Text(
                                    if (current?.available == true && usageAllowed) "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(current.checkedAtMs))}"
                                    else "Monitoring off · usage unavailable",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { refreshVersion++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (refreshing) "Updating…" else "Refresh") }
                        }
                        snapshot?.error?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        if (rules.isEmpty()) {
                            Text("No limits yet. Add an app to begin tracking its daily use.")
                        }
                        rules.forEach { rule ->
                            RuleCard(
                                rule, snapshot?.usageMs?.get(rule.packageName)?.takeIf { snapshot?.available == true && usageAllowed },
                                onEdit = { editRule(rule.packageName, rule.label) },
                                onToggle = { changeRule({ store.save(rule.copy(enabled = !rule.enabled)) }, if (rule.enabled) "${rule.label} limit paused." else "${rule.label} limit resumed.") },
                                onRemove = { removePackage = rule.packageName },
                                enabled = !saving,
                            )
                        }
                        Button(onClick = { search = ""; screen = "picker"; notice = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Add an app") }
                        Text("Soft blocks let you extend use by five minutes. Hard blocks last until midnight. The phone owner can always change these settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider()
                        Text("Usage and limits stay on this device. ScreenOfficer does not read app content. Android provides the usage history; timing can vary slightly by device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (rules.isNotEmpty()) TextButton(onClick = { clearDialog = true }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Clear saved limits") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (permissionDialog != null) {
        val usage = permissionDialog == "usage"
        AlertDialog(
            onDismissRequest = { permissionDialog = null },
            title = { Text(if (usage) "Allow usage tracking" else "Enable app blocking") },
            text = { Text(if (usage)
                "Usage Access lets ScreenOfficer measure time spent in apps with saved limits, including use earlier today. Results stay on this device. You can revoke access in Android Settings."
                else "The ScreenOfficer accessibility service detects which app is open and displays a blocking screen when its limit is reached. It does not read text, record screens or perform gestures. Enable ScreenOfficer on the next Settings screen. You can turn it off there at any time.") },
            confirmButton = { TextButton(onClick = {
                permissionDialog = null
                try {
                    if (usage) PermissionAccess.openUsageSettings(context) else PermissionAccess.openAccessibilitySettings(context)
                } catch (_: Exception) { notice = "Open Android Settings to enable ${if (usage) "Usage Access" else "the ScreenOfficer accessibility service"}." }
            }) { Text("Open settings") } },
            dismissButton = { TextButton(onClick = { permissionDialog = null }) { Text("Cancel") } },
        )
    }
    if (clearDialog) AlertDialog(
        onDismissRequest = { clearDialog = false }, title = { Text("Clear saved limits?") },
        text = { Text("Remove all ScreenOfficer limits and five-minute extensions. Android's usage history and access permissions are unchanged.") },
        confirmButton = { TextButton(onClick = { clearDialog = false; changeRule({ store.clear() }, "All saved limits cleared.") }) { Text("Clear limits") } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } },
    )
    removePackage?.let { packageName ->
        val label = rules.firstOrNull { it.packageName == packageName }?.label ?: "this app"
        AlertDialog(
            onDismissRequest = { removePackage = null }, title = { Text("Remove limit for $label?") },
            text = { Text("This app will no longer be tracked or blocked by ScreenOfficer.") },
            confirmButton = { TextButton(onClick = { removePackage = null; changeRule({ store.remove(packageName) }, "Limit removed for $label.") }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removePackage = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Heading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccessCard(usage: Boolean, blocking: Boolean, onUsage: () -> Unit, onBlocking: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (usage && blocking) "Tracking and blocking ready" else "Set up tracking and blocking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Usage Access: ${if (usage) "on" else "off"}\nBlocking service: ${if (blocking) "connected" else "off"}", style = MaterialTheme.typography.bodyMedium)
            if (!usage || !blocking) Text("Both permissions are needed to interrupt an app at its limit.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onUsage, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (usage) "Manage Usage Access" else "Enable Usage Access") }
            TextButton(onClick = onBlocking, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (blocking) "Manage blocking service" else "Enable blocking service") }
        }
    }
}

@Composable
private fun ModeChoice(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton).semantics(mergeDescendants = true) {},
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp), border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            RadioButton(selected, onClick = null, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun RuleCard(rule: AppRule, usedMs: Long?, onEdit: () -> Unit, onToggle: () -> Unit, onRemove: () -> Unit, enabled: Boolean) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(rule.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (usedMs == null) "Usage unavailable / ${rule.limitMinutes} min limit" else "${formatUsage(usedMs)} today / ${rule.limitMinutes} min limit", style = MaterialTheme.typography.bodyLarge)
            if (usedMs != null) LinearProgressIndicator(
                progress = { (usedMs.toFloat() / (rule.limitMinutes * 60_000L)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${rule.label} daily usage" },
            )
            Text("${if (rule.mode == BlockMode.SOFT) "Soft block" else "Hard block"} · ${if (rule.enabled) "Active" else "Paused"}", style = MaterialTheme.typography.labelLarge)
            Text("“${rule.message}”", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Edit limit for ${rule.label}" }) { Text("Edit") }
                TextButton(onClick = onToggle, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${if (rule.enabled) "Pause" else "Resume"} limit for ${rule.label}" }) { Text(if (rule.enabled) "Pause" else "Resume") }
                TextButton(onClick = onRemove, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Remove limit for ${rule.label}" }) { Text("Remove") }
            }
        }
    }
}

private fun formatUsage(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1_000
    return if (seconds < 60) "$seconds sec" else "${seconds / 60} min ${seconds % 60} sec"
}

