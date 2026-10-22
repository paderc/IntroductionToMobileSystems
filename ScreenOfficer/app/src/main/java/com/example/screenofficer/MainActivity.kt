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
            ScreenOfficerTheme {
                ScreenOfficerApp()
            }
        }
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