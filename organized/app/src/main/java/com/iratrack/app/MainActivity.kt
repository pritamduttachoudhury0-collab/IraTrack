package com.iratrack.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iratrack.app.analytics.LocalAnalytics
import com.iratrack.app.data.*
import com.iratrack.app.data.credentialType
import com.iratrack.app.export.ExportManager
import com.iratrack.app.notifications.NotificationHelper
import com.iratrack.app.providers.Adapters
import com.iratrack.app.providers.ProviderRegistry
import com.iratrack.app.security.CredentialStore
import com.iratrack.app.sync.SyncWorker
import com.iratrack.app.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncWorker.schedule(this)
        NotificationHelper.ensureChannel(this)

        setContent {
            IraTrackTheme { IraTrackApp() }
        }
    }
}

private fun shareExport(context: android.content.Context, file: java.io.File, mimeType: String, toast: (String) -> Unit) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(
            Intent.EXTRA_STREAM,
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Export usage")) }
        .onFailure { toast("Export prepared, but no share target is available.") }
}

private fun money(v: Double) =
    NumberFormat.getCurrencyInstance(Locale.US).format(v)

@Composable
fun IraTrackApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val credentials = remember { CredentialStore(context) }
    val records by db.usageDao().observeAll().collectAsState(initial = emptyList())
    val states by db.providerStateDao().observeAll().collectAsState(initial = emptyList())

    var screen by remember { mutableStateOf("dashboard") }
    var selected by remember { mutableStateOf<ProviderId?>(null) }
    var guideProvider by remember { mutableStateOf<ProviderId?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun toast(text: String) { message = text }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101510)) {
                NavItem("dashboard", "Dashboard", Icons.Default.Home, screen) { screen = "dashboard" }
                NavItem("providers", "Providers", Icons.Default.AccountTree, screen) { screen = "providers" }
                NavItem("analytics", "Analytics", Icons.Default.Insights, screen) { screen = "analytics" }
                NavItem("security", "Security", Icons.Default.Security, screen) { screen = "security" }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (screen) {
                "dashboard" -> Dashboard(
                    records,
                    states,
                    onProvider = { selected = it; screen = "detail" },
                    onSync = {
                        scope.launch {
                            var success = 0
                            ProviderId.entries.forEach { id ->
                                val key = credentials.get(id.name) ?: return@forEach
                                val result = runCatching { Adapters.get(id).sync(key) }.getOrElse {
                                    SyncResult(emptyList(), "${id.label}: ${it.message ?: "synchronization failed"}", false)
                                }
                                if (result.records.isNotEmpty()) db.usageDao().insertAll(result.records)
                                val previousState = db.providerStateDao().get(id.name)
                                val lastSync = if (result.success) System.currentTimeMillis() else previousState?.lastSync
                                db.providerStateDao().upsert(
                                    ProviderState(id.name, true, lastSync, result.statusMessage, result.success)
                                )
                                if (result.success) success++
                                toast(result.statusMessage)
                            }
                            if (success == 0 && ProviderId.entries.none { credentials.has(it.name) }) {
                                toast("Add a provider credential first.")
                            }
                        }
                    }
                )
                "providers" -> ProviderScreen(
                    credentials,
                    states,
                    onSave = { id, key ->
                        credentials.put(id.name, key)
                        scope.launch {
                            db.providerStateDao().upsert(ProviderState(id.name, true, null, "Credential stored", false))
                        }
                        toast("${id.label} credential stored locally.")
                    },
                    onDelete = { id ->
                        credentials.delete(id.name)
                        scope.launch {
                            db.providerStateDao().upsert(ProviderState(id.name, false, null, "Credential removed", false))
                        }
                        toast("${id.label} credential removed.")
                    },
                    onDetails = { selected = it; screen = "detail" },
                    onGuide = { guideProvider = it; screen = "guide" }
                )
                "analytics" -> AnalyticsScreen(records)
                "security" -> SecurityScreen(
                    records,
                    credentials,
                    onExportCsv = {
                        shareExport(context, ExportManager.csv(context, records), "text/csv", ::toast)
                    },
                    onExportJson = {
                        shareExport(context, ExportManager.json(context, records), "application/json", ::toast)
                    },
                    onDelete = {
                        ExportManager.deleteExports(context)
                        credentials.deleteAll()
                        scope.launch {
                            db.usageDao().deleteAll()
                            db.providerStateDao().deleteAll()
                        }
                        toast("All local credentials and usage history deleted.")
                    }
                )
                "detail" -> selected?.let { p ->
                    DetailScreen(p, records.filter { it.provider == p.name }) { screen = "providers" }
                }
                "guide" -> guideProvider?.let { p ->
                    GuideContent.forProvider(p)?.let { guide ->
                        GuideScreen(guide) { screen = "providers" }
                    }
                }
            }

            message?.let { msg ->
                LaunchedEffect(msg) {
                    delay(2800)
                    message = null
                }
                Snackbar(
                    Modifier.align(Alignment.BottomCenter).padding(14.dp),
                    action = { TextButton({ message = null }) { Text("OK") } }
                ) { Text(msg) }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(
    key: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedKey: String, click: () -> Unit
) {
    NavigationBarItem(
        selected = selectedKey == key,
        onClick = click,
        icon = { Icon(icon, null) },
        label = { Text(label) }
    )
}

@Composable
private fun Header(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = Muted, fontSize = 13.sp) }
    }
}

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(16.dp),
        content = content
    )
}


@Composable
private fun SpendChart(records: List<UsageRecord>) {
    val now = System.currentTimeMillis()
    val days = (6 downTo 0).map { offset ->
        val end = now - offset * 86_400_000L
        val start = end - 86_400_000L
        records.filter { it.timestamp in start until end }.sumOf { it.costUsd ?: 0.0 }
    }
    val max = (days.maxOrNull() ?: 0.0).coerceAtLeast(0.01)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .padding(top = 10.dp)
    ) {
        val step = size.width / (days.size - 1).coerceAtLeast(1)
        val path = Path()

        days.forEachIndexed { index, value ->
            val x = index * step
            val y = size.height - ((value / max) * (size.height - 16.dp.toPx()))
            if (index == 0) path.moveTo(x, y.toFloat()) else path.lineTo(x, y.toFloat())
        }

        drawPath(
            path = path,
            color = Accent,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
        )
    }
}

@Composable
private fun Dashboard(
    records: List<UsageRecord>,
    states: List<ProviderState>,
    onProvider: (ProviderId) -> Unit,
    onSync: () -> Unit
) {
    val now = System.currentTimeMillis()
    val total = LocalAnalytics.total(records)
    val today = LocalAnalytics.total(records, now - 86_400_000L)
    val week = LocalAnalytics.total(records, now - 604_800_000L)
    val month = LocalAnalytics.total(records, now - 2_592_000_000L)
    val anomalies = LocalAnalytics.anomalies(records)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("PRIVATE AI SPEND", "IraTrack · your local AI stack")
        CardBox(Modifier.padding(horizontal = 16.dp)) {
            Text("TOTAL", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(money(total), fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text("Provider-reported + estimated, clearly labelled", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Today", money(today))
                Metric("7 days", money(week))
                Metric("30 days", money(month))
            }
        }

        Spacer(Modifier.height(12.dp))

        CardBox(Modifier.padding(horizontal = 16.dp)) {
            Text("7-DAY SPEND", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            SpendChart(records)
            Text("Daily provider-reported/estimated cost history", color = Muted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(12.dp))

        if (anomalies.isNotEmpty()) {
            CardBox(Modifier.padding(horizontal = 16.dp)) {
                Text("UNUSUAL USAGE", color = Warning, fontWeight = FontWeight.Bold)
                anomalies.take(3).forEach {
                    Text("${it.provider}: +${it.percentageAboveBaseline}% above local baseline")
                }
                Text(
                    "IraTrack reports the increase. It does not guess its cause.",
                    color = Muted, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        CardBox(Modifier.padding(horizontal = 16.dp)) {
            Text("PROVIDERS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            ProviderId.entries.forEach { id ->
                val cost = records.filter { it.provider == id.name }.sumOf { it.costUsd ?: 0.0 }
                val state = states.firstOrNull { it.provider == id.name }
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onProvider(id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(id.label)
                        Text(
                            state?.lastStatus ?: "Not configured",
                            color = Muted, fontSize = 11.sp
                        )
                    }
                    Text(money(cost), fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSync,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Sync, null)
            Spacer(Modifier.width(8.dp))
            Text("SYNCHRONIZE")
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Previously synchronized data remains available offline.",
            color = Muted, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProviderScreen(
    credentials: CredentialStore,
    states: List<ProviderState>,
    onSave: (ProviderId, String) -> Unit,
    onDelete: (ProviderId) -> Unit,
    onDetails: (ProviderId) -> Unit,
    onGuide: (ProviderId) -> Unit
) {
    var selected by remember { mutableStateOf(ProviderId.OPENAI) }
    var key by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("PROVIDERS", "Credentials never enter the Room usage database.")
        CardBox(Modifier.padding(horizontal = 16.dp)) {
            Text("SELECT PROVIDER", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            ProviderId.entries.forEach { id ->
                FilterChip(
                    selected = selected == id,
                    onClick = { selected = id; key = "" },
                    label = { Text(id.label) },
                    modifier = Modifier.padding(end = 6.dp, bottom = 6.dp)
                )
            }

            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(selected.credentialType().hintLabel(selected.label)) },
                singleLine = true
            )

            // DeepSeek and xAI need more explanation than a single text field can carry
            // (DeepSeek's balance-vs-history distinction; xAI's separate Management API
            // key and self-resolved team). OpenAI and Anthropic keep their existing simple
            // flow -- no guide entry point is added for them or for the other providers.
            if (GuideContent.forProvider(selected) != null) {
                HowToConnectLink(onClick = { onGuide(selected) })
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = key.isNotBlank(),
                    onClick = { onSave(selected, key); key = "" }
                ) { Text("STORE") }

                OutlinedButton(
                    onClick = { onDetails(selected) }
                ) { Text("DETAILS") }

                if (credentials.has(selected.name)) {
                    OutlinedButton(onClick = { onDelete(selected) }) { Text("REMOVE") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        CardBox(Modifier.padding(horizontal = 16.dp)) {
            Text("CAPABILITY MATRIX", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            ProviderRegistry.capabilities.forEach { c ->
                Column(Modifier.padding(vertical = 7.dp)) {
                    Text(c.provider.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Usage: ${if (c.usage) "✓" else "—"}   Billing: ${c.billing}   Models: ${if (c.models) "✓" else "—"}",
                        color = Muted, fontSize = 12.sp
                    )
                    Text(c.notes, color = Muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AnalyticsScreen(records: List<UsageRecord>) {
    val anomalies = LocalAnalytics.anomalies(records)
    val byProvider = records.groupBy { it.provider }
        .mapValues { (_, rows) -> rows.sumOf { it.costUsd ?: 0.0 } }
        .toList()
        .sortedByDescending { it.second }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("MODEL & USAGE ANALYTICS", "Only synchronized provider data is displayed.")
        CardBox(Modifier.padding(16.dp)) {
            Text("SPEND BY PROVIDER", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            byProvider.forEach { (provider, cost) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text(provider, Modifier.weight(1f))
                    Text(money(cost), fontWeight = FontWeight.SemiBold)
                }
            }
            if (byProvider.isEmpty()) Text("No usage records yet.", color = Muted)
        }

        Spacer(Modifier.height(12.dp))
        CardBox(Modifier.padding(16.dp)) {
            Text("ANOMALIES", fontWeight = FontWeight.Bold)
            if (anomalies.isEmpty()) {
                Text("No anomaly detected from the available local history.", color = Muted)
            } else {
                anomalies.forEach {
                    Text("${it.provider}: +${it.percentageAboveBaseline}%")
                    Text(
                        "Recent ${money(it.recentCost)} vs baseline ${money(it.baselineCost)}",
                        color = Muted, fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        CardBox(Modifier.padding(16.dp)) {
            Text("MODEL DATA", fontWeight = FontWeight.Bold)
            val models = records.filter { !it.model.isNullOrBlank() }
                .groupBy { it.model!! }
                .mapValues { (_, rows) -> rows.sumOf { it.costUsd ?: 0.0 } }
                .toList()
                .sortedByDescending { it.second }

            if (models.isEmpty()) {
                Text(
                    "No model-level billing records are available from the synchronized providers.",
                    color = Muted
                )
            } else {
                models.take(20).forEach { (model, cost) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(model, Modifier.weight(1f))
                        Text(money(cost))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DetailScreen(provider: ProviderId, records: List<UsageRecord>, back: () -> Unit) {
    val cost = records.sumOf { it.costUsd ?: 0.0 }
    val requests = records.sumOf { it.requests ?: 0L }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(back) { Icon(Icons.Default.ArrowBack, null) }
            Text(provider.label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        CardBox(Modifier.padding(16.dp)) {
            Text(money(cost), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(
                records.firstOrNull()?.status?.name ?: "NO SYNCHRONIZED DATA",
                color = Muted
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Records", records.size.toString())
                Metric("Requests", requests.toString())
                Metric("Models", records.mapNotNull { it.model }.distinct().size.toString())
            }
        }

        Spacer(Modifier.height(12.dp))
        CardBox(Modifier.padding(16.dp)) {
            Text("HISTORY", fontWeight = FontWeight.Bold)
            records.take(50).forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text(it.model ?: it.unitLabel, Modifier.weight(1f))
                    Text(it.costUsd?.let(::money) ?: "Unavailable")
                }
            }
            if (records.isEmpty()) {
                Text(
                    "No synchronized records. IraTrack will not invent them.",
                    color = Muted, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SecurityScreen(
    records: List<UsageRecord>,
    credentials: CredentialStore,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onDelete: () -> Unit
) {
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("KEY CREDENTIAL SECURITY", "Device-local storage")
        CardBox(Modifier.padding(16.dp)) {
            Text("ANDROID KEYSTORE", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Credentials are encrypted using an AES key held by Android Keystore.")
            Spacer(Modifier.height(8.dp))
            listOf(
                "Credentials are separate from the Room database",
                "No IraTrack cloud account is required",
                "No analytics or advertising SDK is included",
                "No credential export",
                "No credential logging",
                "No remote IraTrack proxy"
            ).forEach { Text("• $it", color = Muted, modifier = Modifier.padding(vertical = 3.dp)) }
        }

        Spacer(Modifier.height(12.dp))
        CardBox(Modifier.padding(16.dp)) {
            Text("DATA OWNERSHIP", fontWeight = FontWeight.Bold)
            Text("${records.size} local usage records")
            Text("Exports contain usage data only; API credentials are never included.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row {
                OutlinedButton(onClick = onExportCsv) {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("EXPORT CSV")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onExportJson) {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("EXPORT JSON")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        CardBox(Modifier.padding(16.dp)) {
            Text("DANGER ZONE", fontWeight = FontWeight.Bold)
            Text("Remove every credential and every local usage record.", color = Muted)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { confirm = true }) { Text("DELETE EVERYTHING") }
        }

        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("Delete everything?") },
                text = { Text("All locally stored credentials and usage history will be permanently removed.") },
                confirmButton = {
                    TextButton(onClick = { confirm = false; onDelete() }) { Text("DELETE") }
                },
                dismissButton = {
                    TextButton(onClick = { confirm = false }) { Text("CANCEL") }
                }
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}
