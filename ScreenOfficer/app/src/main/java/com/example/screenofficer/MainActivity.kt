@file:Suppress("DEPRECATION")

package com.example.screenofficer

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.util.TimeUtils.formatDuration
import com.example.screenofficer.ui.theme.ScreenOfficerTheme
import java.text.DateFormat
import java.time.ZoneId
import java.util.Calendar
import java.util.Date

var usageStatsLog : Map<String, Long> by mutableStateOf(emptyMap())
class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        this.setContent {
            ScreenOfficerTheme {
                ScreenOfficerApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        CheckAndRequestUsageAccess(this)
        usageStatsLog = GetUsageData(this)
    }
}

@SuppressLint("NewApi")
@PreviewScreenSizes
@Composable
fun ScreenOfficerApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val context = LocalContext.current;
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
            val cal = Calendar.getInstance().timeInMillis
            Column(
                modifier = Modifier.
                    fillMaxSize().
                    padding(innerPadding).
                    verticalScroll(rememberScrollState())
            ){
            for (entry in usageStatsLog){
                if (entry.component2() <= 0) continue;

                ShowUsageData(
                    Pair(entry.component1(), entry.component2()),
                    GetIcon(context, entry.component1()),
                    modifier = Modifier.padding(innerPadding)
                )
                }
            }
        }
    }
}
fun GetIcon(context : Context, packageName : String) : ImageBitmap{
    try {
        val icon = context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        return icon;
    } catch (e : Exception){
        return ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)!!
                .toBitmap(96, 96)
                .asImageBitmap()
    }
}
fun GetUsageData (context : Context, daysAgo : Long = 0) : Map<String, Long> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    val calendar = Calendar.getInstance()
    val endTime = calendar.timeInMillis

    //Set 00:00 of current day and get its milliseconds
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val beginTime = calendar.timeInMillis

    val map : MutableMap<String, Long> = mutableMapOf()
    for (pair in usageStatsManager.queryAndAggregateUsageStats(beginTime, endTime)){
        val packageName = pair.component1()
        val timeMs = pair.component2().totalTimeInForeground;
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(pair.component1(), 0)

            map += packageName to timeMs;
        } catch (e : PackageManager.NameNotFoundException){
            continue
        }
    }
    return map
}
@Composable
fun ShowUsageData(pair : Pair<String, Long>, icon : ImageBitmap, modifier : Modifier = Modifier) {
    val context = LocalContext.current

    val packageName = pair.component1()
    val time_ms = pair.component2()

    var name : String
    try {
        name = GetAppLabel(context, pair.component1())
    } catch (e : PackageManager.NameNotFoundException){
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier){
        Image(
            bitmap = icon,
            contentDescription = packageName,
            modifier = Modifier.size(40.dp)
        )
        Column{
            Text("${name}")
            Text("${DateUtils.formatElapsedTime(time_ms / 1000)}")
        }
    }
}
fun GetAppLabel(context: Context, packageName : String) : String {
    val pm = context.packageManager
    val appInfo = pm.getApplicationInfo(packageName, 0)
    return pm.getApplicationLabel(appInfo).toString()
}
fun CheckAndRequestUsageAccess(context: Context){
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    val granted = mode == AppOpsManager.MODE_ALLOWED

    if (granted) return
    try{
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        context.startActivity(intent)
    } catch(e : Exception){
        //TODO
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
    var i = rememberSaveable() {mutableStateOf(0) }
    Text(
        text = "Hello $name! ${i.component1()}",
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