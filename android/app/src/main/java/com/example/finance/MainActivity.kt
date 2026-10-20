package com.example.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finance.ui.theme.FinanceTheme
import java.util.Currency
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF18231F)
private val Muted = Color(0xFF75817B)
private val Canvas = Color(0xFFF5F7F4)
private val Green = Color(0xFF187653)
private val PaleGreen = Color(0xFFE7F2EC)

private enum class AppTab(val title: String, val mark: String) {
    Overview("Übersicht", "⌂"),
    Transactions("Umsätze", "↕")
}

private enum class Category(val title: String, val color: Color, val mark: String) {
    Income("Einkommen", Color(0xFF187653), "€"),
    Food("Lebensmittel", Color(0xFFE7A344), "L"),
    Transport("Transport", Color(0xFF6585C5), "T"),
    Shopping("Shopping", Color(0xFFAD72AA), "S"),
    Home("Wohnen", Color(0xFF4D9A9A), "W"),
    Leisure("Freizeit", Color(0xFFDC7F65), "F"),
    Other("Sonstiges", Color(0xFF829087), "?")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinanceTheme {
                FinanceApp()
            }
        }
    }
}

@Composable
private fun FinanceApp() {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Overview) }
    var overview by remember { mutableStateOf<FinanceOverview?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        isLoading = true
        errorMessage = null
        try {
            overview = withContext(Dispatchers.IO) { FinanceApi.fetchOverview() }
        } catch (_: Exception) {
            errorMessage = "Das lokale Backend ist gerade nicht erreichbar. Starte es am Computer und versuche es erneut."
        } finally {
            isLoading = false
        }
    }

    if (overview == null) {
        if (isLoading) LoadingScreen() else ConnectionErrorScreen(errorMessage.orEmpty()) { reloadKey += 1 }
        return
    }
    val financeOverview = overview ?: return

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.mark, fontSize = 21.sp, fontWeight = FontWeight.Medium) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            AppTab.Overview -> OverviewScreen(financeOverview, Modifier.padding(innerPadding))
            AppTab.Transactions -> TransactionsScreen(financeOverview, Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun LoadingScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Green)
            Spacer(Modifier.height(16.dp))
            Text("Saldo lädt deine Übersicht …", color = Muted)
        }
    }
}

@Composable
private fun ConnectionErrorScreen(message: String, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Keine Verbindung", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(message, color = Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onRetry) { Text("Erneut versuchen") }
        }
    }
}

@Composable
private fun OverviewScreen(overview: FinanceOverview, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Guten Tag", color = Muted, fontSize = 14.sp)
                    Text("Saldo", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PaleGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text("E", color = Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }

        item { SampleDataBanner() }
        item { BalanceCard(overview) }
        item { MonthSummary(overview) }
        item { SpendingCard(overview) }
        item {
            SectionHeading("Letzte Umsätze", overview.month)
        }
        items(overview.transactions.take(4)) { transaction ->
            TransactionRow(transaction, overview.currency)
        }
        item {
            Text(
                "Alle Umsätze findest du unten im Menü.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SampleDataBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PaleGreen)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Green))
        Spacer(Modifier.width(9.dp))
        Column {
            Text("Testdaten vom Backend", color = Green, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("Noch nicht mit einer Bank verbunden", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BalanceCard(overview: FinanceOverview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF145C43))
    ) {
        Column(Modifier.padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("GESAMTVERMÖGEN", color = Color(0xFFC3DED1), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Text("DEMO", color = Color(0xFFC3DED1), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(formatCurrency(overview.balanceCents, overview.currency), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(19.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(overview.accountInstitution, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text("·", color = Color(0xFFC3DED1))
                Spacer(Modifier.width(8.dp))
                Text(overview.accountName, color = Color(0xFFC3DED1), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MonthSummary(overview: FinanceOverview) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryCard("Einnahmen", formatCurrency(overview.incomeCents, overview.currency), "+ diesen Monat", Green, Modifier.weight(1f))
        SummaryCard("Ausgaben", formatCurrency(overview.expensesCents, overview.currency), "diesen Monat", Color(0xFFBE5C4C), Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(title: String, value: String, hint: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, softWrap = false)
            Spacer(Modifier.height(5.dp))
            Text(hint, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SpendingCard(overview: FinanceOverview) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Ausgaben", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("im ${overview.month}", color = Muted, fontSize = 12.sp)
                }
                Text(formatCurrency(overview.expensesCents, overview.currency), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(15.dp))
            overview.spending.forEachIndexed { index, category ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                SpendingBar(category, overview.currency)
            }
        }
    }
}

@Composable
private fun SpendingBar(category: SpendingCategory, currency: String) {
    val color = when (category.name) {
        "Wohnen" -> Color(0xFF4D9A9A)
        "Transport" -> Color(0xFF6585C5)
        "Essen & Freizeit" -> Color(0xFFE7A344)
        "Shopping" -> Color(0xFFAD72AA)
        else -> Green
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(category.name, modifier = Modifier.width(112.dp), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0F3F0))
        ) {
            Box(Modifier.fillMaxWidth(category.fraction.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(10.dp))
        Text(formatCurrency(category.amountCents, currency), color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeading(title: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(action, color = Green, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TransactionsScreen(overview: FinanceOverview, modifier: Modifier = Modifier) {
    var selectedCategory by rememberSaveable { mutableStateOf("Alle") }
    val categories = listOf("Alle") + Category.entries.map { it.title }
    val visibleTransactions = if (selectedCategory == "Alle") overview.transactions else overview.transactions.filter { it.category == selectedCategory }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("Deine Aktivität", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Alle Umsätze im Überblick", color = Muted, fontSize = 14.sp)
            }
        }
        item { SampleDataBanner() }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category, maxLines = 1) }
                    )
                }
            }
        }
        item { SectionHeading(overview.month, "${visibleTransactions.size} Umsätze") }
        items(visibleTransactions) { transaction ->
            TransactionRow(transaction, overview.currency)
        }
        if (visibleTransactions.isEmpty()) {
            item { Text("Keine Umsätze in dieser Kategorie.", color = Muted, modifier = Modifier.padding(vertical = 18.dp)) }
        }
    }
}

@Composable
private fun TransactionRow(transaction: FinanceTransaction, currency: String) {
    val category = Category.entries.firstOrNull { it.title == transaction.category } ?: Category.Other
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(category.color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Text(category.mark, color = category.color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.merchant, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text("${transaction.category} · ${transaction.dateLabel}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatAmount(transaction.amountCents, currency),
            color = if (transaction.amountCents > 0) Green else Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

private fun formatAmount(amountCents: Long, currencyCode: String): String {
    val currency = formatCurrency(kotlin.math.abs(amountCents), currencyCode)
    return if (amountCents > 0) "+$currency" else "−$currency"
}

private fun formatCurrency(amountCents: Long, currencyCode: String): String {
    return NumberFormat.getCurrencyInstance(Locale.GERMANY).apply {
        currency = Currency.getInstance(currencyCode)
    }.format(amountCents / 100.0)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun FinanceAppPreview() {
    FinanceTheme {
        Surface(color = Canvas) {
            LoadingScreen()
        }
    }
}
