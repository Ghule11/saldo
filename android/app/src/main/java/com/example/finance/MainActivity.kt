package com.example.finance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.finance.ui.theme.FinanceTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
private val BankTimeZone = ZoneId.of("Europe/Vienna")

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
    var bankingStatus by remember { mutableStateOf<BankingConnectionStatus?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var connectNotice by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cloudStore = remember(context) {
        CloudConnectionStore(context.applicationContext).also { FinanceApi.configureCloud(it) }
    }
    var cloudConnected by remember { mutableStateOf(cloudStore.credentials() != null) }
    var showCloudPairing by remember { mutableStateOf(false) }
    var isPairing by remember { mutableStateOf(false) }
    var pairingError by remember { mutableStateOf<String?>(null) }
    val categoryStore = remember(context) { CategoryOverrideStore(context.applicationContext) }
    val subscriptionStore = remember(context) { SubscriptionDecisionStore(context.applicationContext) }
    val recurringStore = remember(context) { RecurringVisibilityStore(context.applicationContext) }
    var categoryOverrides by remember { mutableStateOf(categoryStore.load()) }
    var merchantRules by remember { mutableStateOf(categoryStore.loadMerchantRules()) }
    var subscriptionDecisions by remember { mutableStateOf(subscriptionStore.load()) }
    var parentSenders by remember { mutableStateOf(categoryStore.loadParentSenders()) }
    var hiddenRecurring by remember { mutableStateOf(recurringStore.load()) }
    var editingTransaction by remember { mutableStateOf<FinanceTransaction?>(null) }
    var selectedTransaction by remember { mutableStateOf<FinanceTransaction?>(null) }

    fun hideRecurring(key: String) {
        recurringStore.hide(key)
        hiddenRecurring = recurringStore.load()
    }

    fun restoreRecurring(prefix: String) {
        recurringStore.restore(prefix)
        hiddenRecurring = recurringStore.load()
    }

    fun decideSubscription(merchant: String, decision: SubscriptionDecision) {
        val key = FinanceCategories.merchantKey(merchant)
        if (key.isBlank()) return
        subscriptionStore.save(key, decision)
        subscriptionDecisions = subscriptionStore.load()
        if (decision == SubscriptionDecision.Confirmed) {
            categoryStore.saveMerchantRule(key, TransactionCategory.Subscriptions)
            merchantRules = categoryStore.loadMerchantRules()
            recurringStore.show("subscription:$key")
            hiddenRecurring = recurringStore.load()
        } else if (merchantRules[key] == TransactionCategory.Subscriptions) {
            categoryStore.saveMerchantRule(key, null)
            merchantRules = categoryStore.loadMerchantRules()
        }
    }

    fun removeMerchantRule(merchant: String) {
        val key = FinanceCategories.merchantKey(merchant)
        categoryStore.saveMerchantRule(key, null)
        merchantRules = categoryStore.loadMerchantRules()
        subscriptionStore.save(key, null)
        subscriptionDecisions = subscriptionStore.load()
    }

    fun resetSubscriptionDecision(merchant: String) {
        subscriptionStore.save(merchant, null)
        subscriptionDecisions = subscriptionStore.load()
    }

    fun startBankConnection() {
        if (isConnecting) return
        isConnecting = true
        connectError = null
        connectNotice = null
        scope.launch {
            try {
                val url = withContext(Dispatchers.IO) { FinanceApi.startBankAustriaAuthorization() }
                if (url != null) {
                    val uri = Uri.parse(url)
                    if (uri.scheme != "https" || uri.host.isNullOrBlank()) {
                        throw IllegalStateException("Ungültiger Freigabe-Link.")
                    }
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    connectNotice = "Freigabe im Browser auf deinem Handy abschließen. Danach hier Daten aktualisieren."
                } else {
                    connectNotice = "Freigabe im Browser auf deinem Computer abschließen. Danach hier Daten aktualisieren."
                }
            } catch (exception: Exception) {
                connectError = exception.message ?: "Die Verbindung konnte nicht gestartet werden."
            } finally {
                isConnecting = false
            }
        }
    }

    fun pairCloud(code: String, environment: String) {
        if (isPairing) return
        isPairing = true
        pairingError = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { FinanceApi.pairCloud(code, environment) }
                cloudConnected = true
                showCloudPairing = false
                reloadKey++
            } catch (exception: Exception) {
                pairingError = exception.message ?: "Cloud-Kopplung fehlgeschlagen."
            } finally {
                isPairing = false
            }
        }
    }

    LaunchedEffect(reloadKey) {
        isLoading = true
        errorMessage = null
        val importedHistory = withContext(Dispatchers.IO) {
            runCatching { FinanceApi.fetchImportedHistory() }.getOrNull()
        }
        try {
            val status = withContext(Dispatchers.IO) { FinanceApi.fetchBankingStatus() }
            bankingStatus = status
            overview = if (status.environment == "PRODUCTION" && status.accountCount == 0) {
                errorMessage = "Bank Austria ist derzeit nicht verbunden. Die importierten Umsätze bleiben verfügbar."
                importedHistory
            } else try {
                val bank = withContext(Dispatchers.IO) { FinanceApi.fetchDashboardOverview(status) }
                FinanceApi.mergeImportedHistory(bank, importedHistory)
            } catch (exception: Exception) {
                if (status.environment != "PRODUCTION") throw exception
                val currentStatus = withContext(Dispatchers.IO) {
                    runCatching { FinanceApi.fetchBankingStatus() }.getOrNull()
                } ?: status
                bankingStatus = currentStatus
                val retryAt = currentStatus.retryAfter.toLocalBankTime()
                errorMessage = if (retryAt != null) {
                    "Bankabruf bis $retryAt pausiert. Deine importierten Umsätze bleiben verfügbar."
                } else {
                    "Bankdaten konnten nicht geladen werden: ${exception.message ?: "Verbindung abgelaufen"}"
                }
                overview?.takeIf { it.dataSource == "banking_production" }
                    ?.copy(isCached = true)
                    ?.let { FinanceApi.mergeImportedHistory(it, importedHistory) }
                    ?: importedHistory
            }
        } catch (_: Exception) {
            errorMessage = if (importedHistory != null) {
                "Der Bankstatus konnte nicht geprüft werden. Die importierten Umsätze sind weiterhin verfügbar."
            } else {
                if (cloudConnected) "Die Cloud-Daten konnten nicht geladen werden. Bitte später erneut versuchen."
                else "Die Finanzdaten konnten nicht geladen werden. Prüfe, ob Saldo auf dem Computer läuft."
            }
            overview = overview?.takeIf { it.dataSource == "banking_production" }
                ?.copy(isCached = true)
                ?.let { FinanceApi.mergeImportedHistory(it, importedHistory) }
                ?: importedHistory
        } finally {
            isLoading = false
        }
    }

    if (showCloudPairing) CloudPairingDialog(
        isPairing = isPairing,
        error = pairingError,
        onDismiss = { if (!isPairing) showCloudPairing = false },
        onPair = ::pairCloud
    )

    val data = overview
    if (data == null) {
        if (isLoading) LoadingScreen() else ConnectionErrorScreen(
            errorMessage.orEmpty(),
            { reloadKey++ },
            if (errorMessage?.contains("abgelaufen", ignoreCase = true) == true) ::startBankConnection else null,
            connectNotice,
            connectError,
            if (!cloudConnected) { { showCloudPairing = true } } else null
        )
        return
    }
    val categorized = remember(data, categoryOverrides, parentSenders, merchantRules, subscriptionDecisions) {
        FinanceCategories.apply(data, categoryOverrides, parentSenders, merchantRules, subscriptionDecisions)
    }
    val months = remember(categorized) { FinanceInsights.months(categorized) }
    val screenStateHolder = rememberSaveableStateHolder()
    val detailTransaction = selectedTransaction?.let { selected ->
        categorized.transactions.firstOrNull { it.identity == selected.identity } ?: selected
    }
    BackHandler(enabled = detailTransaction != null && editingTransaction == null) {
        selectedTransaction = null
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            if (detailTransaction == null) NavigationBar(containerColor = Panel) {
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
        if (detailTransaction != null) {
            val merchantKey = FinanceCategories.merchantKey(detailTransaction.merchant)
            val categorySource = when {
                detailTransaction.amountCents > 0 &&
                    FinanceCategories.senderKey(detailTransaction.merchant) in parentSenders -> "Von dir als Eltern markiert"
                detailTransaction.identity in categoryOverrides -> "Von dir für diese Buchung gewählt"
                merchantKey in merchantRules -> "Deine Regel für diesen Händler"
                else -> "Automatisch vorgeschlagen"
            }
            TransactionDetailScreen(
                detailTransaction, categorized, categorySource,
                onBack = { selectedTransaction = null },
                onEditCategory = { editingTransaction = detailTransaction },
                modifier = modifier
            )
        } else when (selectedTab) {
            AppTab.Overview -> OverviewScreen(
                categorized, months, hiddenRecurring, subscriptionDecisions, ::hideRecurring,
                ::restoreRecurring, ::decideSubscription, modifier
            )
            AppTab.Transactions -> screenStateHolder.SaveableStateProvider("transactions") {
                TransactionsScreen(categorized, months, { selectedTransaction = it }, modifier)
            }
            AppTab.Analysis -> AnalysisScreen(categorized, months, modifier)
            AppTab.Account -> AccountScreen(
                categorized, months, categoryOverrides.size + parentSenders.size + merchantRules.size,
                merchantRules, subscriptionDecisions, ::removeMerchantRule, ::resetSubscriptionDecision,
                bankingStatus, isLoading,
                isConnecting, errorMessage, connectError, connectNotice, { reloadKey++ },
                { startBankConnection() }, cloudConnected, FinanceApi.cloudEnvironment(),
                { showCloudPairing = true },
                {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { FinanceApi.disconnectCloud() }
                            cloudConnected = false
                            reloadKey++
                        } catch (exception: Exception) {
                            connectError = exception.message ?: "Cloud-Verbindung konnte nicht getrennt werden."
                        }
                    }
                }, modifier)
        }
    }

    editingTransaction?.let { transaction ->
        CategoryDialog(
            transaction = transaction,
            merchantRule = merchantRules[FinanceCategories.merchantKey(transaction.merchant)],
            onDismiss = { editingTransaction = null },
            onChoose = { choice, forMerchant ->
                if (transaction.amountCents > 0) {
                    categoryStore.save(transaction.identity, null)
                    categoryOverrides = categoryOverrides - transaction.identity
                    categoryStore.saveParentSender(transaction.merchant, choice == TransactionCategory.Parents)
                    parentSenders = categoryStore.loadParentSenders()
                } else if (forMerchant) {
                    val key = FinanceCategories.merchantKey(transaction.merchant)
                    categoryStore.save(transaction.identity, null)
                    categoryOverrides = categoryOverrides - transaction.identity
                    categoryStore.saveMerchantRule(key, choice)
                    merchantRules = categoryStore.loadMerchantRules()
                    subscriptionStore.save(key, when (choice) {
                        TransactionCategory.Subscriptions -> SubscriptionDecision.Confirmed
                        null -> null
                        else -> SubscriptionDecision.Rejected
                    })
                    subscriptionDecisions = subscriptionStore.load()
                    if (choice == TransactionCategory.Subscriptions) {
                        recurringStore.show("subscription:$key")
                        hiddenRecurring = recurringStore.load()
                    }
                } else {
                    categoryStore.save(transaction.identity, choice)
                    categoryOverrides = categoryOverrides.toMutableMap().apply {
                        if (choice == null) remove(transaction.identity) else put(transaction.identity, choice)
                    }
                }
                editingTransaction = null
            }
        )
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
private fun ConnectionErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onConnect: (() -> Unit)? = null,
    connectNotice: String? = null,
    connectError: String? = null,
    onCloudPair: (() -> Unit)? = null
) {
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
            if (onCloudPair != null) {
                Spacer(Modifier.height(12.dp))
                ActionButton("Cloud verbinden", onCloudPair)
            }
            if (onConnect != null) {
                Spacer(Modifier.height(12.dp))
                ActionButton("Bank Austria erneut verbinden", onConnect)
            }
            connectNotice?.let { Text(it, color = Muted, textAlign = TextAlign.Center) }
            connectError?.let { Text(it, color = Red, textAlign = TextAlign.Center) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudPairingDialog(
    isPairing: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onPair: (String, String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var environment by remember { mutableStateOf("PRODUCTION") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cloud-Testverbindung") },
        text = {
            Column {
                Text("Gib den einmaligen Kopplungscode ein. Er ist zehn Minuten gültig.")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = environment == "PRODUCTION",
                        onClick = { environment = "PRODUCTION" }, label = { Text("Bank Austria") })
                    FilterChip(selected = environment == "SANDBOX",
                        onClick = { environment = "SANDBOX" }, label = { Text("Testdaten") })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim() },
                    label = { Text("Kopplungscode") },
                    singleLine = true
                )
                if (error != null) Text(error, color = Red)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPair(code, environment) }, enabled = !isPairing && code.isNotBlank()) {
                Text(if (isPairing) "Verbinde …" else "Verbinden")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun OverviewScreen(
    data: FinanceOverview,
    months: List<MonthlySnapshot>,
    hiddenRecurring: Set<String>,
    subscriptionDecisions: Map<String, SubscriptionDecision>,
    onHideRecurring: (String) -> Unit,
    onRestoreRecurring: (String) -> Unit,
    onSubscriptionDecision: (String, SubscriptionDecision) -> Unit,
    modifier: Modifier
) {
    val current = months.firstOrNull()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item { Text("Saldo", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item { DataSourceBanner(data) }
        item { BalanceCard(data) }
        if (current != null) {
            item { SectionHeading("Monatsübersicht", current.label) }
            item { MonthlyTotals(current, data.currency) }
            item { NetCard(current, data.currency) }
            item { RecurringIncomeCard(data, data.currency, hiddenRecurring, onHideRecurring, onRestoreRecurring) }
            item { SubscriptionsCard(data, data.currency, hiddenRecurring, subscriptionDecisions,
                onSubscriptionDecision, onRestoreRecurring) }
        } else {
            item { EmptyNote("Für dieses Konto sind noch keine datierten Umsätze vorhanden.") }
        }
    }
}

@Composable
private fun TransactionsScreen(data: FinanceOverview, months: List<MonthlySnapshot>, onTransactionClick: (FinanceTransaction) -> Unit, modifier: Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf("Alle") }
    var monthKey by rememberSaveable { mutableStateOf("all") }
    var categoryFilter by rememberSaveable { mutableStateOf("") }
    var showCategoryFilter by remember { mutableStateOf(false) }
    val categories = data.transactions.map { it.category }.distinct().sorted()
    val selectedCategory = categoryFilter.takeIf { it in categories }.orEmpty()
    val selectedMonth = months.firstOrNull { it.key == monthKey }
    val filtered = (selectedMonth?.transactions ?: data.transactions)
        .filter { direction == "Alle" ||
            (direction == "Eingänge" && it.amountCents > 0) ||
            (direction == "Ausgaben" && it.amountCents < 0) }
        .filter { selectedCategory.isBlank() || it.category == selectedCategory }
        .filter { query.isBlank() || it.merchant.contains(query, ignoreCase = true) ||
            it.originalMerchant.contains(query, ignoreCase = true) ||
            it.detail.contains(query, ignoreCase = true) }
        .sortedByDescending { it.bookingDate ?: "" }
    val incomingCents = filtered.filter { it.amountCents > 0 }.sumOf { it.amountCents }
    val outgoingCents = filtered.filter { it.amountCents < 0 }.sumOf { -it.amountCents }
    val dayGroups = filtered.groupBy { it.bookingDate ?: it.dateLabel.ifBlank { "Ohne Datum" } }
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("Umsätze", "Suche und filtere deine Bewegungen") }
        item { EmptyNote("Tippe auf einen Umsatz für alle Details und seine Kategorie.") }
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
                listOf("Alle", "Eingänge", "Ausgaben").forEach { option ->
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
        item {
            ChoiceChip(
                if (selectedCategory.isBlank()) "Alle Kategorien ▾" else "$selectedCategory ▾",
                selectedCategory.isNotBlank()
            ) { showCategoryFilter = true }
        }
        item { SectionHeading("Bewegungen", "${filtered.size} ${if (filtered.size == 1) "Umsatz" else "Umsätze"}") }
        if (incomingCents > 0 || outgoingCents > 0) item {
            PanelCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (incomingCents > 0) Column(Modifier.weight(1f)) {
                        Text("Eingänge im Filter", color = Muted, fontSize = 11.sp)
                        Text(formatCurrency(incomingCents, data.currency), color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    if (outgoingCents > 0) Column(Modifier.weight(1f), horizontalAlignment = if (incomingCents > 0) Alignment.End else Alignment.Start) {
                        Text("Ausgaben im Filter", color = Muted, fontSize = 11.sp)
                        Text(formatCurrency(outgoingCents, data.currency), color = Red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        dayGroups.forEach { (day, transactions) ->
            item {
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(dayHeading(day), color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${transactions.size} ${if (transactions.size == 1) "Buchung" else "Buchungen"}", color = Muted, fontSize = 11.sp)
                }
            }
            items(transactions) { TransactionRow(it, data.currency, onTransactionClick) }
        }
        if (filtered.isEmpty()) item { EmptyNote("Keine passenden Umsätze gefunden.") }
    }
    if (showCategoryFilter) {
        AlertDialog(
            onDismissRequest = { showCategoryFilter = false },
            containerColor = Panel,
            title = { Text("Kategorie filtern", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    (listOf("") + categories).forEach { category ->
                        TextButton(onClick = {
                            categoryFilter = category
                            showCategoryFilter = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (category.isBlank()) "Alle Kategorien" else category,
                                color = if (category == selectedCategory) Purple else Ink)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoryFilter = false }) { Text("Schließen", color = Purple) } }
        )
    }
}

@Composable
private fun TransactionDetailScreen(
    transaction: FinanceTransaction,
    data: FinanceOverview,
    categorySource: String,
    onBack: () -> Unit,
    onEditCategory: () -> Unit,
    modifier: Modifier
) {
    val incoming = transaction.amountCents >= 0
    val accent = if (incoming) Green else Red
    val merchantKey = FinanceCategories.merchantKey(transaction.merchant)
    val related = data.transactions.filter { other ->
        (other.amountCents >= 0) == incoming &&
            if (merchantKey.isNotBlank()) FinanceCategories.merchantKey(other.merchant) == merchantKey
            else other.merchant.equals(transaction.merchant, ignoreCase = true)
    }
    val relatedTotal = related.sumOf { kotlin.math.abs(it.amountCents) }
    val transactionDate = transaction.transactionDate?.takeIf { it != transaction.bookingDate }
    val source = when {
        transaction.identity.startsWith("statement:") -> "Importierter Kontoauszug"
        data.dataSource == "banking_sandbox" -> "Testkonto"
        data.dataSource == "sample" -> "Beispieldaten"
        else -> "Bank Austria"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Zurück", color = Purple, fontWeight = FontWeight.SemiBold) }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(PanelRaised, PurpleMuted, Panel)))
                    .padding(22.dp)
            ) {
                Text(if (incoming) "EINGANG" else "AUSGABE", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(transaction.merchant, color = Ink, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(formatSigned(transaction.amountCents, data.currency), color = accent, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(transaction.dateLabel, color = Muted, fontSize = 13.sp)
            }
        }
        item {
            PanelCard {
                Text("Einordnung", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                InfoRow("Kategorie", transaction.category)
                Text(categorySource, color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                ActionButton("Kategorie ändern", onEditCategory)
            }
        }
        item {
            PanelCard {
                Text("Buchungsdetails", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                InfoRow("Buchungsdatum", transaction.dateLabel)
                if (transactionDate != null) InfoRow("Transaktionsdatum", germanDate(transactionDate))
                InfoRow("Quelle", source)
                if (!transaction.originalMerchant.equals(transaction.merchant, ignoreCase = true)) {
                    Spacer(Modifier.height(10.dp))
                    Text(if (incoming) "Originaler Absender" else "Originaler Händler", color = Muted, fontSize = 11.sp)
                    SelectionContainer {
                        Text(transaction.originalMerchant, color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        item {
            PanelCard {
                Text("Buchungstext", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(transaction.detail.ifBlank { "Kein weiterer Buchungstext vorhanden." },
                        color = if (transaction.detail.isBlank()) Muted else Ink,
                        fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
        if (related.size > 1) item {
            PanelCard {
                Text(if (incoming) "Von diesem Absender" else "Bei diesem Händler", color = Ink,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text("${related.size} Buchungen · ${formatCurrency(relatedTotal, data.currency)} insgesamt",
                    color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Im aktuell verfügbaren Verlauf", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

private fun germanDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))
}.getOrDefault(value)

private fun dayHeading(value: String): String = runCatching {
    val date = runCatching { LocalDate.parse(value) }.getOrElse {
        LocalDate.parse(value, DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))
    }
    date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))
        .replaceFirstChar { it.titlecase(Locale.GERMAN) }
}.getOrDefault(value)

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
            item { CategoryChart(month, data.currency) }
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
    correctedCount: Int,
    merchantRules: Map<String, TransactionCategory>,
    subscriptionDecisions: Map<String, SubscriptionDecision>,
    onRemoveMerchantRule: (String) -> Unit,
    onResetSubscriptionDecision: (String) -> Unit,
    bankingStatus: BankingConnectionStatus?,
    isLoading: Boolean,
    isConnecting: Boolean,
    errorMessage: String?,
    connectError: String?,
    connectNotice: String?,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    cloudConnected: Boolean,
    cloudEnvironment: String?,
    onCloudPair: () -> Unit,
    onCloudDisconnect: () -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item { ScreenTitle("Konto", "Verbindung und Daten") }
        item { DataSourceBanner(data) }
        item {
            PanelCard {
                SectionHeading("Cloud-Verbindung", if (cloudConnected) "Gekoppelt" else "Nicht gekoppelt")
                Spacer(Modifier.height(8.dp))
                Text(if (cloudEnvironment == "PRODUCTION")
                    "Bankdaten sind auch ohne laufenden PC erreichbar."
                    else if (cloudConnected) "Testdaten sind auch ohne laufenden PC erreichbar."
                    else "Einmaligen Kopplungscode eingeben, um Saldo unterwegs zu nutzen.",
                    color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                ActionButton(if (cloudConnected) "Cloud trennen" else "Cloud koppeln",
                    if (cloudConnected) onCloudDisconnect else onCloudPair)
            }
        }
        if (data.dataSource != "sample" || bankingStatus?.environment != "PRODUCTION") {
            item { BalanceCard(data) }
            item {
                PanelCard {
                    InfoRow("Konto", data.accountName)
                    InfoRow("Institut", data.accountInstitution)
                    InfoRow("Währung", data.currency)
                    InfoRow("Umsätze", "${data.transactions.size}")
                    InfoRow("Eigene Zuordnungen", "$correctedCount")
                    InfoRow("Zeitraum", if (months.isEmpty()) "Keine Daten" else
                        "${months.last().label} – ${months.first().label}")
                }
            }
        }
        if (merchantRules.isNotEmpty()) item {
            PanelCard {
                SectionHeading("Händlerregeln", "${merchantRules.size} gespeichert")
                Spacer(Modifier.height(8.dp))
                merchantRules.toSortedMap().forEach { (merchant, category) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(FinanceCategories.displayMerchant(merchant).replaceFirstChar { it.titlecase(Locale.GERMAN) },
                                color = Ink, fontSize = 12.sp)
                            Text(category.label, color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onRemoveMerchantRule(merchant) }) {
                            Text("Entfernen", color = Purple, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        val rejectedSubscriptions = subscriptionDecisions.filter { (merchant, decision) ->
            decision == SubscriptionDecision.Rejected && merchant !in merchantRules
        }
        if (rejectedSubscriptions.isNotEmpty()) item {
            PanelCard {
                SectionHeading("Keine Abos", "${rejectedSubscriptions.size} ausgeschlossen")
                Spacer(Modifier.height(8.dp))
                rejectedSubscriptions.keys.sorted().forEach { merchant ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(FinanceCategories.displayMerchant(merchant).replaceFirstChar { it.titlecase(Locale.GERMAN) },
                            modifier = Modifier.weight(1f), color = Ink, fontSize = 12.sp)
                        TextButton(onClick = { onResetSubscriptionDecision(merchant) }) {
                            Text("Rückgängig", color = Purple, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            ActionButton(if (isLoading) "Wird geprüft …" else "Daten prüfen", onRefresh, !isLoading)
        }
        if (bankingStatus?.environment == "PRODUCTION") item {
            val retryAt = bankingStatus.retryAfter.toLocalBankTime()
            EmptyNote(if (retryAt == null)
                "Saldo nutzt gespeicherte Bankdaten und fragt nur bei Bedarf neu ab. Die importierten Umsätze bleiben verfügbar."
            else
                "Bankabruf bis $retryAt pausiert. Das Konto ist verbunden; eine erneute Freigabe ist dafür nicht nötig.")
        }
        if (bankingStatus?.environment == "PRODUCTION") {
            item {
                ActionButton(
                    if (isConnecting) "Verbindung wird gestartet …" else
                        if (bankingStatus.accountCount > 0) "Bank Austria erneut verbinden" else "Bank Austria verbinden",
                    onConnect,
                    !isConnecting && bankingStatus.isConfigured && bankingStatus.isActive &&
                        bankingStatus.retryAfter.toLocalBankTime() == null
                )
            }
            if (!bankingStatus.isConfigured) item {
                EmptyNote("Die Production-Anwendung muss zuerst lokal eingerichtet werden.")
            }
            else if (!bankingStatus.isActive) item {
                EmptyNote("Verknüpfe dein Konto zuerst bei Enable Banking mit der Production-Anwendung.")
            }
        }
        if (errorMessage != null && !errorMessage.startsWith("Bankabruf bis")) item { EmptyNote(errorMessage) }
        if (connectError != null) item { EmptyNote(connectError) }
        if (connectNotice != null) item { EmptyNote(connectNotice) }
        if (data.dataSource == "banking_sandbox") item {
            EmptyNote("Die verbundenen Testdaten stammen aus einer Sandbox und sind keine echten Bankumsätze.")
        }
    }
}

private fun String?.toLocalBankTime(): String? = this?.let { value ->
    runCatching {
        val time = OffsetDateTime.parse(value)
        if (time.isAfter(OffsetDateTime.now())) {
            time.atZoneSameInstant(BankTimeZone)
                .format(DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMANY))
        } else null
    }.getOrNull()
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
private fun DataSourceBanner(data: FinanceOverview) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(PanelRaised).padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Purple))
        Spacer(Modifier.width(10.dp))
        val fetchedAt = data.fetchedAt?.let { value ->
            runCatching {
                OffsetDateTime.parse(value).atZoneSameInstant(BankTimeZone)
                    .format(DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMANY))
            }.getOrNull()
        }
        val label = when {
            data.dataSource == "banking_sandbox" -> "Verbundenes Testkonto · Sandbox"
            data.dataSource == "local_statement" -> "Importierte Umsätze · Bankabruf derzeit nicht verfügbar"
            data.dataSource == "banking_production" && data.isCached ->
                if (fetchedAt == null) "Gespeicherte Bankdaten" else "Bankdaten · Stand $fetchedAt"
            data.dataSource == "banking_production" && data.hasImportedHistory ->
                "Bank Austria · mit importierter Historie"
            data.dataSource == "banking_production" -> "Verbundenes Bankkonto · Bank Austria"
            else -> "Beispieldaten"
        }
        Text(label, color = Purple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BalanceCard(data: FinanceOverview) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF302044), Color(0xFF21182F), Color(0xFF181421))))
            .padding(horizontal = 22.dp, vertical = 27.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("KONTOSTAND", color = Color(0xFFD2BFF2), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (data.balanceAvailable) formatCurrency(data.balanceCents, data.currency) else "Aktuell nicht verfügbar",
            color = Ink,
            fontSize = if (data.balanceAvailable) 36.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(18.dp))
        Box(Modifier.width(42.dp).height(3.dp).clip(CircleShape).background(Purple.copy(alpha = 0.8f)))
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
        Text("Überschuss", color = Muted, fontSize = 13.sp)
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
        SectionHeading("Größte Ausgaben", month.label)
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
private fun CategoryChart(month: MonthlySnapshot, currency: String) {
    val categories = FinanceInsights.expenseCategories(month)
    PanelCard {
        SectionHeading("Ausgaben nach Kategorie", month.label)
        Spacer(Modifier.height(15.dp))
        if (categories.isEmpty()) {
            Text("Keine Ausgaben in diesem Monat.", color = Muted, fontSize = 13.sp)
        } else {
            categories.forEach { category ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(category.name, color = Ink, fontSize = 12.sp)
                    Text(formatCurrency(category.amountCents, currency), color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(PurpleMuted)) {
                    Box(Modifier.fillMaxWidth(category.fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Red))
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun SubscriptionsCard(
    data: FinanceOverview,
    currency: String,
    hidden: Set<String>,
    decisions: Map<String, SubscriptionDecision>,
    onDecision: (String, SubscriptionDecision) -> Unit,
    onRestore: (String) -> Unit
) {
    val subscriptions = remember(data, decisions) { FinanceInsights.subscriptions(data, decisions) }
    val confirmed = subscriptions.filter {
        decisions[it.key.removePrefix("subscription:")] == SubscriptionDecision.Confirmed
    }
    val suggestions = subscriptions.filter {
        decisions[it.key.removePrefix("subscription:")] == null && it.key !in hidden
    }.take(6)
    val hasHidden = subscriptions.any { it.key in hidden }
    if (confirmed.isEmpty() && suggestions.isEmpty() && !hasHidden) return

    PanelCard {
        SectionHeading("Abos", "${confirmed.size} bestätigt")
        if (confirmed.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RecurringMonthlyTotal(confirmed.sumOf { it.amountCents }, currency, Red,
                "Summe der letzten Beträge je bestätigtem Abo.")
            Spacer(Modifier.height(10.dp))
            confirmed.forEach { entry ->
                RecurringOverviewRow(entry, currency, Red, "Kein Abo") {
                    onDecision(entry.key.removePrefix("subscription:"), SubscriptionDecision.Rejected)
                }
            }
        }
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Mögliche Abos", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Bitte prüfen und zuordnen.", color = Muted, fontSize = 10.sp)
            suggestions.forEach { entry ->
                Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, color = Ink, fontSize = 12.sp, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Text("Zuletzt ${entry.lastDate}", color = Muted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(formatCurrency(entry.amountCents, currency), color = Red, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            onDecision(entry.key.removePrefix("subscription:"), SubscriptionDecision.Confirmed)
                        }) { Text("Ist ein Abo", color = Purple, fontSize = 11.sp) }
                        TextButton(onClick = {
                            onDecision(entry.key.removePrefix("subscription:"), SubscriptionDecision.Rejected)
                        }) { Text("Kein Abo", color = Muted, fontSize = 11.sp) }
                    }
                }
            }
        }
        if (hasHidden) TextButton(onClick = { onRestore("subscription:") }) {
            Text("Früher ausgeblendete anzeigen", color = Purple, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RecurringIncomeCard(
    data: FinanceOverview,
    currency: String,
    hidden: Set<String>,
    onHide: (String) -> Unit,
    onRestore: (String) -> Unit
) {
    val income = remember(data) { FinanceInsights.recurringIncome(data) }
    if (income.isEmpty()) return
    val visible = income.filterNot { it.key in hidden }.take(6)
    val hasHidden = income.any { it.key in hidden }
    PanelCard {
        SectionHeading("Regelmäßige Eingänge", "${visible.size} erkannt")
        if (visible.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            RecurringMonthlyTotal(visible.sumOf { it.amountCents }, currency, Green,
                "Summe der letzten Beträge je angezeigtem Eingang.")
            Spacer(Modifier.height(10.dp))
            visible.forEach { item ->
                RecurringOverviewRow(item, currency, Green, "Entfernen") { onHide(item.key) }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text("Alle Einträge ausgeblendet.", color = Muted, fontSize = 12.sp)
        }
        if (hasHidden) TextButton(onClick = { onRestore("income:") }) {
            Text("Ausgeblendete wieder anzeigen", color = Purple, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RecurringMonthlyTotal(amountCents: Long, currency: String, accent: Color, explanation: String) {
    Text("Monatlich geschätzt: ${formatCurrency(amountCents, currency)}",
        color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    Text(explanation, color = Muted, fontSize = 10.sp)
}

@Composable
private fun RecurringOverviewRow(
    entry: RecurringEntry,
    currency: String,
    accent: Color,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("In ${entry.monthCount} Monaten · zuletzt ${entry.lastDate}", color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatCurrency(entry.amountCents, currency), color = accent,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onAction) { Text(actionLabel, color = Muted, fontSize = 10.sp) }
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
private fun TransactionRow(transaction: FinanceTransaction, currency: String, onClick: (FinanceTransaction) -> Unit) {
    val incoming = transaction.amountCents >= 0
    val accent = if (incoming) Green else Red
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Panel).clickable { onClick(transaction) }.padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text(if (incoming) "↓" else "↑", color = accent, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(transaction.merchant, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text("${transaction.category} · ${transaction.dateLabel}", color = Muted, fontSize = 11.sp, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Text(formatSigned(transaction.amountCents, currency), color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun CategoryDialog(
    transaction: FinanceTransaction,
    merchantRule: TransactionCategory?,
    onDismiss: () -> Unit,
    onChoose: (TransactionCategory?, Boolean) -> Unit
) {
    val merchantKey = FinanceCategories.merchantKey(transaction.merchant)
    var forMerchant by remember(transaction.identity) {
        mutableStateOf(transaction.amountCents < 0 && merchantKey.isNotBlank())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Umsatz zuordnen", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("${transaction.merchant} · ${transaction.dateLabel}", color = Muted, fontSize = 13.sp, maxLines = 2)
                if (transaction.amountCents > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("Eltern gilt für alle Eingänge von diesem Absender.", color = Muted, fontSize = 11.sp)
                } else if (merchantKey.isNotBlank()) {
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip("Nur diese Buchung", !forMerchant) { forMerchant = false }
                        ChoiceChip("Diesen Händler", forMerchant) { forMerchant = true }
                    }
                    Text(if (forMerchant) "Gilt auch für künftige Buchungen dieses Händlers."
                        else "Ändert nur diese eine Buchung.", color = Muted, fontSize = 11.sp)
                    if (merchantRule != null) {
                        Text("Händlerregel: ${merchantRule.label}", color = Purple, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    FinanceCategories.choices(transaction).forEach { category ->
                        TextButton(onClick = { onChoose(category, forMerchant) }, modifier = Modifier.fillMaxWidth()) {
                            Text(category.label, color = if (transaction.category == category.label) Purple else Ink)
                        }
                    }
                    TextButton(onClick = { onChoose(null, forMerchant) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (forMerchant) "Händlerregel entfernen" else "Eigene Zuordnung entfernen", color = Muted)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen", color = Purple) } }
    )
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
