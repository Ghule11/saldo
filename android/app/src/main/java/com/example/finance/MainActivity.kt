package com.example.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.finance.ui.theme.FinanceTheme
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Background = Color(0xFF0B0911)
private val Panel = Color(0xFF191522)
private val PanelRaised = Color(0xFF251E32)
private val Purple = Color(0xFFAC8BFF)
private val PurpleMuted = Color(0xFF493963)
private val Ink = Color(0xFFF6F2FF)
private val Muted = Color(0xFFADA4BA)
private val Green = Color(0xFF4EE3A1)
private val Red = Color(0xFFFF708B)

private enum class AppTab(val title: String, val mark: String) {
    Overview("Übersicht", "⌂"),
    Transactions("Umsätze", "↕"),
    Analysis("Analyse", "▥"),
    Account("Konto", "◉")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        setContent { FinanceTheme { FinanceApp() } }
    }
}

@Composable
private fun FinanceApp() {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Overview) }
    var overview by remember { mutableStateOf<FinanceOverview?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        isLoading = true
        errorMessage = null
        try {
            overview = withContext(Dispatchers.IO) { FinanceApi.fetchDashboardOverview() }
        } catch (_: Exception) {
            errorMessage = "Die Finanzdaten konnten nicht geladen werden. Prüfe, ob Saldo auf dem Computer läuft."
        } finally {
            isLoading = false
        }
    }

    val data = overview
    if (data == null) {
        if (isLoading) LoadingScreen() else ConnectionErrorScreen(errorMessage.orEmpty()) { reloadKey++ }
        return
    }
    val months = remember(data) { FinanceInsights.months(data) }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.mark, fontSize = 21.sp, fontWeight = FontWeight.Bold) },
                        label = { Text(tab.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Purple,
                            selectedTextColor = Purple,
                            indicatorColor = PurpleMuted,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (selectedTab) {
            AppTab.Overview -> OverviewScreen(data, months, modifier)
            AppTab.Transactions -> TransactionsScreen(data, months, modifier)
            AppTab.Analysis -> AnalysisScreen(data, months, modifier)
            AppTab.Account -> AccountScreen(data, months, isLoading, errorMessage, { reloadKey++ }, modifier)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Purple)
            Spacer(Modifier.height(16.dp))
            Text("Saldo lädt deine Übersicht …", color = Muted)
        }
    }
}

@Composable
private fun ConnectionErrorScreen(message: String, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Keine Verbindung", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(message, color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            ActionButton("Erneut versuchen", onRetry)
        }
    }
}

@Composable
private fun OverviewScreen(data: FinanceOverview, months: List<MonthlySnapshot>, modifier: Modifier) {
    val current = months.firstOrNull()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item { ScreenTitle("Saldo", "Dein Geld im Blick") }
        item { DataSourceBanner(data.dataSource == "banking_sandbox") }
        item { BalanceCard(data) }
        if (current != null) {
            item { SectionHeading("Monatsübersicht", current.label) }
            item { MonthlyTotals(current, data.currency) }
            item { NetCard(current, data.currency) }
            item { MonthlyChart(months, data.currency) }
            item { SectionHeading("Letzte Umsätze", current.label) }
            items(current.transactions.take(4)) { TransactionRow(it, data.currency) }
        } else {
            item { EmptyNote("Für dieses Konto sind noch keine datierten Umsätze vorhanden.") }
        }
    }
}

@Composable
private fun TransactionsScreen(data: FinanceOverview, months: List<MonthlySnapshot>, modifier: Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf("Alle") }
    var monthKey by rememberSaveable { mutableStateOf("all") }
    val selectedMonth = months.firstOrNull { it.key == monthKey }
    val filtered = (selectedMonth?.transactions ?: data.transactions)
        .filter { direction == "Alle" || (direction == "Eingänge" && it.amountCents > 0) ||
            (direction == "Ausgänge" && it.amountCents < 0) }
        .filter { query.isBlank() || it.merchant.contains(query, ignoreCase = true) ||
            it.detail.contains(query, ignoreCase = true) }
        .sortedByDescending { it.bookingDate ?: "" }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("Umsätze", "Suche und filtere deine Bewegungen") }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Umsatz suchen") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Ink, unfocusedTextColor = Ink,
                    focusedLabelColor = Purple, unfocusedLabelColor = Muted,
                    focusedBorderColor = Purple, unfocusedBorderColor = PurpleMuted,
                    cursorColor = Purple
                )
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Alle", "Eingänge", "Ausgänge").forEach { option ->
                    ChoiceChip(option, direction == option) { direction = option }
                }
            }
        }
        if (months.size > 1) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip("Alle Monate", monthKey == "all") { monthKey = "all" }
                    months.forEach { month ->
                        ChoiceChip(month.shortLabel, monthKey == month.key) { monthKey = month.key }
                    }
                }
            }
        }
        item { SectionHeading("Bewegungen", "${filtered.size} Umsätze") }
        items(filtered) { TransactionRow(it, data.currency) }
        if (filtered.isEmpty()) item { EmptyNote("Keine passenden Umsätze gefunden.") }
    }
}

@Composable
private fun AnalysisScreen(data: FinanceOverview, months: List<MonthlySnapshot>, modifier: Modifier) {
    var monthKey by rememberSaveable { mutableStateOf(months.firstOrNull()?.key.orEmpty()) }
    val month = months.firstOrNull { it.key == monthKey } ?: months.firstOrNull()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item { ScreenTitle("Analyse", "Monate und Ausgaben vergleichen") }
        if (month != null) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    months.forEach { option ->
                        ChoiceChip(option.shortLabel, month.key == option.key) { monthKey = option.key }
                    }
                }
            }
            item { SectionHeading(month.label, "${month.transactions.size} Umsätze") }
            item { MonthlyTotals(month, data.currency) }
            item { NetCard(month, data.currency) }
            item { MonthlyChart(months, data.currency) }
            item { OutflowChart(month, data.currency) }
        } else {
            item { EmptyNote("Noch keine Monatsdaten verfügbar.") }
        }
    }
}

@Composable
private fun AccountScreen(
    data: FinanceOverview,
    months: List<MonthlySnapshot>,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item { ScreenTitle("Konto", "Verbindung und Daten") }
        item { DataSourceBanner(data.dataSource == "banking_sandbox") }
        item { BalanceCard(data) }
        item {
            PanelCard {
                InfoRow("Konto", data.accountName)
                InfoRow("Institut", data.accountInstitution)
                InfoRow("Währung", data.currency)
                InfoRow("Umsätze", "${data.transactions.size}")
                InfoRow("Zeitraum", if (months.isEmpty()) "Keine Daten" else
                    "${months.last().label} – ${months.first().label}")
            }
        }
        item {
            ActionButton(if (isLoading) "Wird aktualisiert …" else "Daten aktualisieren", onRefresh, !isLoading)
        }
        if (errorMessage != null) item { EmptyNote(errorMessage) }
        item { EmptyNote("Die verbundenen Testdaten stammen aus einer Sandbox und sind keine echten Bankumsätze.") }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun DataSourceBanner(isSandbox: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(PanelRaised).padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Purple))
        Spacer(Modifier.width(10.dp))
        Text(if (isSandbox) "Verbundenes Testkonto · Sandbox" else "Beispieldaten", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BalanceCard(data: FinanceOverview) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B245A), Color(0xFF21172F), Color(0xFF17131F))))
            .padding(22.dp)
    ) {
        Text("KONTOSTAND", color = Color(0xFFD2BFF2), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Text(formatCurrency(data.balanceCents, data.currency), color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(20.dp))
        Text("${data.accountInstitution} · ${data.accountName}", color = Color(0xFFD2BFF2), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MonthlyTotals(month: MonthlySnapshot, currency: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TotalCard("Einnahmen", month.incomeCents, currency, Green, "+", Modifier.weight(1f))
        TotalCard("Ausgaben", month.outcomeCents, currency, Red, "−", Modifier.weight(1f))
    }
}

@Composable
private fun TotalCard(title: String, cents: Long, currency: String, accent: Color, sign: String, modifier: Modifier) {
    PanelCard(modifier) {
        Text(title, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Text("$sign${formatCurrency(cents, currency)}", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun NetCard(month: MonthlySnapshot, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(PanelRaised).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Monatssaldo", color = Muted, fontSize = 13.sp)
        Text(formatSigned(month.netCents, currency), color = if (month.netCents >= 0) Green else Red, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MonthlyChart(months: List<MonthlySnapshot>, currency: String) {
    val chartMonths = months.take(6).reversed()
    val maxAmount = chartMonths.maxOfOrNull { maxOf(it.incomeCents, it.outcomeCents) }?.coerceAtLeast(1L) ?: 1L
    PanelCard {
        SectionHeading("Monatstrend", "Letzte ${chartMonths.size}")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot("Einnahmen", Green)
            LegendDot("Ausgaben", Red)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            chartMonths.forEach { month ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.height(112.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        ChartBar(month.incomeCents, maxAmount, Green)
                        ChartBar(month.outcomeCents, maxAmount, Red)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(month.shortLabel, color = Muted, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text("Werte in ${currency}", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun ChartBar(value: Long, maximum: Long, color: Color) {
    val height = if (value == 0L) 2.dp else (104f * value / maximum).coerceAtLeast(5f).dp
    Box(Modifier.width(10.dp).height(height).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
}

@Composable
private fun OutflowChart(month: MonthlySnapshot, currency: String) {
    val merchants = FinanceInsights.topOutflows(month)
    PanelCard {
        SectionHeading("Größte Ausgänge", month.label)
        Spacer(Modifier.height(15.dp))
        if (merchants.isEmpty()) {
            Text("Keine Ausgänge in diesem Monat.", color = Muted, fontSize = 13.sp)
        } else {
            val maximum = merchants.first().amountCents.coerceAtLeast(1L)
            merchants.forEach { merchant ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(merchant.name, modifier = Modifier.weight(1f), color = Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(formatCurrency(merchant.amountCents, currency), color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(PurpleMuted)) {
                    Box(Modifier.fillMaxWidth((merchant.amountCents.toFloat() / maximum).coerceIn(0f, 1f)).fillMaxHeight().background(Red))
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun TransactionRow(transaction: FinanceTransaction, currency: String) {
    val incoming = transaction.amountCents >= 0
    val accent = if (incoming) Green else Red
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Panel).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(if (incoming) "↓" else "↑", color = accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(transaction.merchant, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text("${if (incoming) "Eingang" else "Ausgang"} · ${transaction.dateLabel}", color = Muted, fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(formatSigned(transaction.amountCents, currency), color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = Purple, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChoiceChip(title: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(title, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Panel,
            labelColor = Muted,
            selectedContainerColor = PurpleMuted,
            selectedLabelColor = Ink
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = PurpleMuted,
            selectedBorderColor = Purple
        )
    )
}

@Composable
private fun PanelCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(Modifier.padding(16.dp), content = { content() })
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}

@Composable
private fun EmptyNote(message: String) {
    Text(message, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Background)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

private fun formatSigned(amountCents: Long, currencyCode: String): String =
    "${if (amountCents >= 0) "+" else "−"}${formatCurrency(kotlin.math.abs(amountCents), currencyCode)}"

private fun formatCurrency(amountCents: Long, currencyCode: String): String =
    NumberFormat.getCurrencyInstance(Locale.GERMANY).apply {
        currency = Currency.getInstance(currencyCode)
    }.format(amountCents / 100.0)
