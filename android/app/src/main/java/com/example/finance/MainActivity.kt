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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.finance.ui.theme.FinanceTheme
import java.text.NumberFormat
import java.util.Locale

private val Ink = Color(0xFF18231F)
private val Muted = Color(0xFF75817B)
private val Canvas = Color(0xFFF5F7F4)
private val Green = Color(0xFF187653)
private val PaleGreen = Color(0xFFE7F2EC)
private val Line = Color(0xFFE8EDE9)

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
    Leisure("Freizeit", Color(0xFFDC7F65), "F")
}

private data class Transaction(
    val merchant: String,
    val detail: String,
    val date: String,
    val amountCents: Long,
    val category: Category
)

private val demoTransactions = listOf(
    Transaction("BILLA", "Lebensmittel · Karte", "Heute · 12:42", -4287, Category.Food),
    Transaction("Wiener Linien", "Jahreskarte · Karte", "Heute · 08:10", -5100, Category.Transport),
    Transaction("Gehalt", "Eingang · Überweisung", "Gestern", 240000, Category.Income),
    Transaction("Spotify", "Monatsabo · Lastschrift", "17. Okt.", -1099, Category.Leisure),
    Transaction("Miete", "Wohnung · Dauerauftrag", "15. Okt.", -92000, Category.Home),
    Transaction("dm drogerie", "Drogerie · Karte", "14. Okt.", -2376, Category.Shopping),
    Transaction("Hofer", "Lebensmittel · Karte", "13. Okt.", -3154, Category.Food),
    Transaction("Café Kaffee", "Essen & Trinken · Karte", "12. Okt.", -895, Category.Leisure),
    Transaction("ÖBB", "Zugticket · Karte", "10. Okt.", -11700, Category.Transport),
    Transaction("Buchhandlung", "Bücher · Karte", "9. Okt.", -1534, Category.Shopping),
    Transaction("Restaurant", "Essen gehen · Karte", "8. Okt.", -2528, Category.Leisure)
)

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
            AppTab.Overview -> OverviewScreen(Modifier.padding(innerPadding))
            AppTab.Transactions -> TransactionsScreen(Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun OverviewScreen(modifier: Modifier = Modifier) {
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

        item { DemoBanner() }
        item { BalanceCard() }
        item { MonthSummary() }
        item { SpendingCard() }
        item {
            SectionHeading("Letzte Umsätze", "Oktober")
        }
        items(demoTransactions.take(4)) { transaction ->
            TransactionRow(transaction)
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
private fun DemoBanner() {
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
        Text("Beispieldaten", color = Green, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text("· noch nicht mit einer Bank verbunden", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun BalanceCard() {
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
            Text("€ 4.286,17", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(19.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bank Austria", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text("·", color = Color(0xFFC3DED1))
                Spacer(Modifier.width(8.dp))
                Text("Beispielkonto", color = Color(0xFFC3DED1), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MonthSummary() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryCard("Einnahmen", "€ 2.400,00", "+ diesen Monat", Green, Modifier.weight(1f))
        SummaryCard("Ausgaben", "€ 1.246,73", "diesen Monat", Color(0xFFBE5C4C), Modifier.weight(1f))
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
private fun SpendingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Ausgaben", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("im Oktober", color = Muted, fontSize = 12.sp)
                }
                Text("€ 1.246,73", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(15.dp))
            SpendingBar("Wohnen", "€ 920,00", 1.0f, Color(0xFF4D9A9A))
            Spacer(Modifier.height(12.dp))
            SpendingBar("Transport", "€ 168,00", 0.183f, Color(0xFF6585C5))
            Spacer(Modifier.height(12.dp))
            SpendingBar("Essen & Freizeit", "€ 119,63", 0.13f, Color(0xFFE7A344))
            Spacer(Modifier.height(12.dp))
            SpendingBar("Shopping", "€ 39,10", 0.043f, Color(0xFFAD72AA))
        }
    }
}

@Composable
private fun SpendingBar(label: String, amount: String, fraction: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(112.dp), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0F3F0))
        ) {
            Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.width(10.dp))
        Text(amount, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
private fun TransactionsScreen(modifier: Modifier = Modifier) {
    var selectedCategory by rememberSaveable { mutableStateOf("Alle") }
    val categories = listOf("Alle") + Category.entries.map { it.title }
    val visibleTransactions = if (selectedCategory == "Alle") demoTransactions else demoTransactions.filter { it.category.title == selectedCategory }

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
        item { DemoBanner() }
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
        item { SectionHeading("Oktober 2026", "${visibleTransactions.size} Umsätze") }
        items(visibleTransactions) { transaction ->
            TransactionRow(transaction)
        }
        if (visibleTransactions.isEmpty()) {
            item { Text("Keine Umsätze in dieser Kategorie.", color = Muted, modifier = Modifier.padding(vertical = 18.dp)) }
        }
    }
}

@Composable
private fun TransactionRow(transaction: Transaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(transaction.category.color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Text(transaction.category.mark, color = transaction.category.color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.merchant, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text("${transaction.category.title} · ${transaction.date}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatAmount(transaction.amountCents),
            color = if (transaction.amountCents > 0) Green else Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

private fun formatAmount(amountCents: Long): String {
    val currency = NumberFormat.getCurrencyInstance(Locale.GERMANY).format(kotlin.math.abs(amountCents) / 100.0)
    return if (amountCents > 0) "+$currency" else "−$currency"
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun FinanceAppPreview() {
    FinanceTheme {
        Surface(color = Canvas) {
            OverviewScreen()
        }
    }
}
