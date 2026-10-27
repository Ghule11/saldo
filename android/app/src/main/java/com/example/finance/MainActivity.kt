package com.example.finance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.finance.ui.theme.FinanceTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Background = Color(0xFF120E19)
private val Panel = Color(0xFF1D1727)
private val PanelRaised = Color(0xFF2A2039)
private val Divider = Color(0xFF3A3046)
private val Purple = Color(0xFFB38BEE)
private val PurpleMuted = Color(0xFF49395D)
private val Ink = Color(0xFFF4EFF9)
private val Muted = Color(0xFFB4A9BF)
private val Green = Color(0xFF6CD4A9)
private val Red = Color(0xFFF08FA4)
private val BankTimeZone = ZoneId.of("Europe/Vienna")

private enum class AppTab(val title: String) {
    Overview("Übersicht"),
    Transactions("Umsätze"),
    Analysis("Analyse"),
    Account("Konto")
}

@Composable
private fun TabIcon(tab: AppTab, selected: Boolean) {
    val glyphColor = if (selected) Background else Muted
    Box(
        Modifier.size(34.dp).clip(CircleShape)
            .background(if (selected) Purple else Color.Transparent)
            .border(1.dp, if (selected) Purple else Divider, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val unit = size.width / 24f
            fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
            val lineWidth = 1.8f * unit
            when (tab) {
                AppTab.Overview -> {
                    val roof = Path().apply {
                        moveTo(3f * unit, 11f * unit)
                        lineTo(12f * unit, 4f * unit)
                        lineTo(21f * unit, 11f * unit)
                    }
                    drawPath(roof, glyphColor, style = Stroke(lineWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawLine(glyphColor, point(5f, 10f), point(5f, 20f), lineWidth, StrokeCap.Round)
                    drawLine(glyphColor, point(19f, 10f), point(19f, 20f), lineWidth, StrokeCap.Round)
                    drawLine(glyphColor, point(5f, 20f), point(19f, 20f), lineWidth, StrokeCap.Round)
                    drawLine(glyphColor, point(10f, 20f), point(10f, 15f), lineWidth, StrokeCap.Round)
                    drawLine(glyphColor, point(14f, 20f), point(14f, 15f), lineWidth, StrokeCap.Round)
                    drawLine(glyphColor, point(10f, 15f), point(14f, 15f), lineWidth, StrokeCap.Round)
                }
                AppTab.Transactions -> {
                    drawLine(glyphColor, point(7f, 19f), point(7f, 5f), lineWidth, StrokeCap.Round)
                    drawLine(glyphColor, point(7f, 5f), point(3.5f, 8.5f), lineWidth, StrokeCap.Round)
                    drawLine(glyphColor, point(7f, 5f), point(10.5f, 8.5f), lineWidth, StrokeCap.Round)
                    drawLine(glyphColor, point(17f, 5f), point(17f, 19f), lineWidth, StrokeCap.Round)
                    drawLine(glyphColor, point(17f, 19f), point(13.5f, 15.5f), lineWidth, StrokeCap.Round)
                    drawLine(glyphColor, point(17f, 19f), point(20.5f, 15.5f), lineWidth, StrokeCap.Round)
                }
                AppTab.Analysis -> {
                    drawLine(glyphColor, point(5f, 19f), point(5f, 12f), 3f * unit, StrokeCap.Round)
                    drawLine(glyphColor, point(12f, 19f), point(12f, 5f), 3f * unit, StrokeCap.Round)
                    drawLine(glyphColor, point(19f, 19f), point(19f, 9f), 3f * unit, StrokeCap.Round)
                }
                AppTab.Account -> {
                    drawCircle(glyphColor, radius = 3f * unit, center = point(12f, 8f), style = Stroke(lineWidth))
                    val shoulders = Path().apply {
                        moveTo(4f * unit, 20f * unit)
                        cubicTo(4f * unit, 12f * unit, 20f * unit, 12f * unit, 20f * unit, 20f * unit)
                    }
                    drawPath(shoulders, glyphColor, style = Stroke(lineWidth, cap = StrokeCap.Round))
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    private var hasResumed = false
    private var resumeVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        setContent { FinanceTheme { FinanceApp(resumeVersion) } }
    }

    override fun onResume() {
        super.onResume()
        if (hasResumed) resumeVersion++ else hasResumed = true
    }
}

@Composable
private fun FinanceApp(resumeVersion: Int) {
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
    val analysisStore = remember(context) { AnalysisPreferencesStore(context.applicationContext) }
    var categoryOverrides by remember { mutableStateOf(categoryStore.load()) }
    var merchantRules by remember { mutableStateOf(categoryStore.loadMerchantRules()) }
    var subscriptionDecisions by remember { mutableStateOf(subscriptionStore.load()) }
    var parentSenders by remember { mutableStateOf(categoryStore.loadParentSenders()) }
    var hiddenRecurring by remember { mutableStateOf(recurringStore.load()) }
    var excludedFromAnalysis by remember { mutableStateOf(analysisStore.excludedIdentities()) }
    var includeReserves by remember { mutableStateOf(analysisStore.includeReserves()) }
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

    LaunchedEffect(reloadKey, resumeVersion) {
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
            if (detailTransaction == null) Column(Modifier.background(Panel).navigationBarsPadding()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
                Row(Modifier.fillMaxWidth().height(72.dp)) {
                    AppTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .selectable(selected = selected, role = Role.Tab) { selectedTab = tab },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            TabIcon(tab, selected)
                            Spacer(Modifier.height(5.dp))
                            Text(tab.title, color = if (selected) Ink else Muted,
                                fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
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
                excludedFromAnalysis = detailTransaction.identity in excludedFromAnalysis,
                onToggleAnalysis = {
                    val exclude = detailTransaction.identity !in excludedFromAnalysis
                    analysisStore.setExcluded(detailTransaction.identity, exclude)
                    excludedFromAnalysis = analysisStore.excludedIdentities()
                },
                modifier = modifier
            )
        } else when (selectedTab) {
            AppTab.Overview -> OverviewScreen(
                categorized, months, bankingStatus, hiddenRecurring, subscriptionDecisions, ::hideRecurring,
                ::restoreRecurring, ::decideSubscription, modifier
            )
            AppTab.Transactions -> screenStateHolder.SaveableStateProvider("transactions") {
                TransactionsScreen(categorized, months, { selectedTransaction = it }, modifier)
            }
            AppTab.Analysis -> AnalysisScreen(
                categorized, months, subscriptionDecisions, excludedFromAnalysis,
                hiddenRecurring, includeReserves,
                onIncludeReserves = { include ->
                    analysisStore.setIncludeReserves(include)
                    includeReserves = include
                }, modifier
            )
            AppTab.Account -> AccountScreen(
                categorized, months, categoryOverrides.size + parentSenders.size + merchantRules.size,
                merchantRules, subscriptionDecisions, ::removeMerchantRule, ::resetSubscriptionDecision,
                bankingStatus, isLoading,
                isConnecting, errorMessage, connectError, connectNotice, { reloadKey++ },
                { startBankConnection() }, cloudConnected,
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
                    val individual = choice?.takeIf { it != TransactionCategory.Parents && it != TransactionCategory.Income }
                    categoryStore.save(transaction.identity, individual)
                    categoryOverrides = categoryOverrides.toMutableMap().apply {
                        if (individual == null) remove(transaction.identity) else put(transaction.identity, individual)
                    }
                    if (choice != TransactionCategory.Cash) {
                        categoryStore.saveParentSender(transaction.merchant, choice == TransactionCategory.Parents)
                        parentSenders = categoryStore.loadParentSenders()
                    }
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
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Purple, strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text("Gespeicherte Daten laden …", color = Muted, fontSize = 13.sp)
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
    bankingStatus: BankingConnectionStatus?,
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Saldo", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold) }
        item { DataSourceBanner(data) }
        if (bankingStatus?.refresh?.lastOutcome == "failed") {
            item { RefreshFailureNotice(bankingStatus) }
        }
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        item { ScreenTitle("Umsätze", "") }
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
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (incomingCents > 0) Column(Modifier.weight(1f)) {
                    Text("Eingänge im Filter", color = Muted, fontSize = 11.sp)
                    Text(formatCurrency(incomingCents, data.currency), color = Green, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                if (outgoingCents > 0) Column(Modifier.weight(1f), horizontalAlignment = if (incomingCents > 0) Alignment.End else Alignment.Start) {
                    Text("Ausgaben im Filter", color = Muted, fontSize = 11.sp)
                    Text(formatCurrency(outgoingCents, data.currency), color = Red, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        dayGroups.forEach { (day, transactions) ->
            item {
                Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 4.dp)
                    .background(PanelRaised).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Purple))
                    Text(dayHeading(day), modifier = Modifier.weight(1f).padding(start = 10.dp),
                        color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${transactions.size} ${if (transactions.size == 1) "Buchung" else "Buchungen"}",
                        modifier = Modifier.padding(end = 10.dp), color = Muted, fontSize = 10.sp)
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
    excludedFromAnalysis: Boolean,
    onToggleAnalysis: () -> Unit,
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
                Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(if (incoming) "Eingang" else "Ausgabe", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(9.dp))
                Text(transaction.merchant, color = Ink, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(9.dp))
                Text(formatSigned(transaction.amountCents, data.currency), color = accent, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
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
        if (transaction.identity.isNotBlank()) item {
            PanelCard {
                Text("Analyse", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(if (excludedFromAnalysis)
                    "Diese Buchung zählt nicht für Prognose, Rücklagen und Saison."
                else "Diese Buchung zählt für Prognose, Rücklagen und Saison.",
                    color = Muted, fontSize = 12.sp)
                TextButton(onClick = onToggleAnalysis) {
                    Text(if (excludedFromAnalysis) "Wieder berücksichtigen" else "Aus Analyse ausschließen",
                        color = Purple)
                }
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
private fun AnalysisScreen(
    data: FinanceOverview,
    months: List<MonthlySnapshot>,
    decisions: Map<String, SubscriptionDecision>,
    excluded: Set<String>,
    hiddenRecurring: Set<String>,
    includeReserves: Boolean,
    onIncludeReserves: (Boolean) -> Unit,
    modifier: Modifier
) {
    val today = LocalDate.now(BankTimeZone)
    val report = remember(data, decisions, excluded, hiddenRecurring, includeReserves, today) {
        FinanceAnalysis.calculate(data, decisions, excluded, includeReserves, today,
            hiddenRecurring)
    }
    var monthKey by rememberSaveable { mutableStateOf(months.firstOrNull()?.key.orEmpty()) }
    var showMonthlyDetails by rememberSaveable { mutableStateOf(false) }
    var showReserves by rememberSaveable { mutableStateOf(false) }
    var showSeasonality by rememberSaveable { mutableStateOf(false) }
    val month = months.firstOrNull { it.key == monthKey } ?: months.firstOrNull()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("Analyse", "") }
        if (data.dataSource == "banking_production") {
            item { DataSourceBanner(data) }
            item { ForecastCard(report.forecast, data.currency, data.fetchedAt) }
            report.monthComparison?.let { comparison ->
                item { MonthComparisonCard(comparison, data.currency) }
            }
            item {
                DisclosureRow("Rücklagen", showReserves) { showReserves = !showReserves }
            }
            if (showReserves) item {
                ReservesCard(report.reserves, data.currency, includeReserves, onIncludeReserves)
            }
            item {
                DisclosureRow("Saisonanalyse", showSeasonality) { showSeasonality = !showSeasonality }
            }
            if (showSeasonality) item { SeasonalityCard(report.seasonality, data.currency) }
        } else {
            item {
                EmptyNote("Prognose und Rücklagen erscheinen mit deinen Bank-Austria-Daten. " +
                    "Testdaten und reine Kontoauszüge zeigen nur den Monatsverlauf.")
            }
        }
        item {
            DisclosureRow("Monatsdetails", showMonthlyDetails) { showMonthlyDetails = !showMonthlyDetails }
        }
        if (showMonthlyDetails) {
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
                item { SectionHeading(month.label, month.transactions.size.toString() + " Umsätze") }
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
}

@Composable
private fun DisclosureRow(title: String, expanded: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(if (expanded) "Schließen" else "Anzeigen", color = Purple, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MonthComparisonCard(comparison: MonthComparison, currency: String) {
    val maximum = maxOf(comparison.currentCents, comparison.previousAverageCents, 1L)
    PanelCard {
        SectionHeading("Monatsvergleich", "bis ${comparison.dayOfMonth}.")
        Spacer(Modifier.height(13.dp))
        ComparisonBar("Dieser Monat", comparison.currentCents, maximum, Purple, currency)
        Spacer(Modifier.height(12.dp))
        ComparisonBar("Ø letzte 3 Monate", comparison.previousAverageCents, maximum,
            Color(0xFF726483), currency)
        Spacer(Modifier.height(12.dp))
        val difference = comparison.differenceCents
        Text(when {
            difference < 0 -> formatCurrency(-difference, currency) + " weniger"
            difference > 0 -> formatCurrency(difference, currency) + " mehr"
            else -> "Gleichauf"
        }, color = when {
            difference < 0 -> Green
            difference > 0 -> Red
            else -> Muted
        }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ComparisonBar(label: String, amount: Long, maximum: Long, accent: Color,
                          currency: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(formatCurrency(amount, currency), color = Ink, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(5.dp))
    Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(PurpleMuted)) {
        Box(Modifier.fillMaxWidth((amount.toFloat() / maximum).coerceIn(0f, 1f))
            .fillMaxHeight().background(accent))
    }
}

@Composable
private fun ForecastCard(forecast: MonthlyForecast, currency: String, fetchedAt: String?) {
    val signalText = when (forecast.signal) {
        ForecastSignal.InLimit -> "Im Rahmen"
        ForecastSignal.Tight -> "Knapp"
        ForecastSignal.Over -> "Über den Einnahmen"
        null -> "Vorläufig"
    }
    val signalColor = when (forecast.signal) {
        ForecastSignal.InLimit -> Green
        ForecastSignal.Tight -> Color(0xFFFFBE7D)
        ForecastSignal.Over -> Red
        null -> Muted
    }
    PanelCard {
        SectionHeading("Monatsprognose", signalText)
        val stale = fetchedAt?.let { value ->
            runCatching {
                OffsetDateTime.parse(value).atZoneSameInstant(BankTimeZone).toLocalDate()
                    .isBefore(LocalDate.now(BankTimeZone).minusDays(1))
            }.getOrDefault(true)
        } ?: true
        if (stale) {
            Spacer(Modifier.height(8.dp))
            Text("Bankdaten älter als gestern",
                color = Color(0xFFFFBE7D), fontSize = 11.sp)
        }
        Spacer(Modifier.height(13.dp))
        if (forecast.projectedCents != null) {
            Text("Voraussichtliche Ausgaben", color = Muted, fontSize = 12.sp)
            Text(formatCurrency(forecast.projectedCents, currency), color = Ink,
                fontSize = 27.sp, fontWeight = FontWeight.Bold)
        } else {
            Text("Noch keine belastbare Prognose", color = Ink,
                fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Mindestens 3 Vormonate nötig",
                color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(13.dp))
        InfoRow("Bereits ausgegeben", formatCurrency(forecast.spentCents, currency))
        InfoRow("Offene Abos", formatCurrency(forecast.upcomingFixedCents, currency))
        if (forecast.variableDailyCents != null) {
            InfoRow("Variable Ausgaben pro Tag", formatCurrency(forecast.variableDailyCents, currency))
        }
        InfoRow("Resttage", forecast.remainingDays.toString())
        if (forecast.differenceCents != null) {
            Spacer(Modifier.height(9.dp))
            Text("Differenz: " + formatSigned(forecast.differenceCents, currency),
                color = signalColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (forecast.dailyRoomCents != null) {
            Spacer(Modifier.height(10.dp))
            Text("Tägliches Restbudget: " + formatSigned(forecast.dailyRoomCents, currency),
                color = if (forecast.dailyRoomCents >= 0) Green else Red,
                fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (forecast.reservesIncluded && forecast.reserveDeductionCents > 0) {
                InfoRow("Rücklagen berücksichtigt",
                    formatCurrency(forecast.reserveDeductionCents, currency))
            }
        }
        if (forecast.fixedCosts.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            Text("Offen: " + forecast.fixedCosts.joinToString(", ") { it.name },
                color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ReservesCard(
    reserves: ReserveSummary,
    currency: String,
    includeReserves: Boolean,
    onIncludeReserves: (Boolean) -> Unit
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    PanelCard {
        SectionHeading("Rücklagen", reserves.entries.size.toString() +
            if (reserves.entries.size == 1) " Vorschlag" else " Vorschläge")
        Spacer(Modifier.height(10.dp))
        if (reserves.entries.isEmpty()) {
            Text("Keine Rücklagen erkannt",
                color = Muted, fontSize = 12.sp)
        } else {
            Text("Monatlich geschätzt: " + formatCurrency(reserves.monthlyCents, currency),
                color = Purple, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Im täglichen Restbudget abziehen", modifier = Modifier.weight(1f),
                    color = Ink, fontSize = 12.sp)
                Switch(checked = includeReserves, onCheckedChange = onIncludeReserves)
            }
            Spacer(Modifier.height(10.dp))
            (if (showAll) reserves.entries else reserves.entries.take(5)).forEach { entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = Ink, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(entry.period + " · " + formatCurrency(entry.yearlyCents, currency) +
                            " pro Jahr", color = Muted, fontSize = 10.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(formatCurrency(entry.monthlyCents, currency) + "/Mo.",
                        color = Purple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (reserves.entries.size > 5) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Weniger anzeigen" else "Alle Rücklagen anzeigen",
                        color = Purple, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SeasonalityCard(seasonality: SeasonalitySummary, currency: String) {
    var expandedMonth by rememberSaveable { mutableIntStateOf(0) }
    PanelCard {
        SectionHeading("Saisonanalyse", seasonality.historyMonths.toString() + " Monate")
        Spacer(Modifier.height(10.dp))
        if (seasonality.months.isEmpty()) {
            Text("Mindestens 12 Monate nötig",
                color = Muted, fontSize = 12.sp)
            return@PanelCard
        }
        if (!seasonality.canClassify) {
            Text("Vorläufige Werte",
                color = Color(0xFFFFBE7D), fontSize = 11.sp)
            Spacer(Modifier.height(9.dp))
        }
        if (seasonality.otherSharePercent >= 30) {
            Text(seasonality.otherSharePercent.toString() + "% Sonstiges",
                color = Color(0xFFFFBE7D), fontSize = 11.sp)
            Spacer(Modifier.height(9.dp))
        }
        seasonality.months.forEach { month ->
            val name = Month.of(month.month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMAN)
                .replaceFirstChar { it.titlecase(Locale.GERMAN) }
            val label = when (month.signal) {
                SeasonalSignal.Save -> "Sparen"
                SeasonalSignal.Room -> "Spielraum"
                SeasonalSignal.Neutral -> "Neutral"
                null -> "Vorläufig"
            }
            val accent = when (month.signal) {
                SeasonalSignal.Save -> Red
                SeasonalSignal.Room -> Green
                SeasonalSignal.Neutral -> Purple
                null -> Muted
            }
            Row(Modifier.fillMaxWidth().clickable {
                expandedMonth = if (expandedMonth == month.month) 0 else month.month
            }.padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text((if (month.deviationPercent >= 0) "+" else "") +
                        month.deviationPercent + "%", color = accent,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(label + if (expandedMonth == month.month) "  ⌃" else "  ⌄",
                        color = Muted, fontSize = 10.sp)
                }
            }
            if (expandedMonth == month.month && month.drivers.isNotEmpty()) {
                month.drivers.forEach { driver ->
                    Text(driver.category + ": " + formatCurrency(driver.averageCents, currency) +
                        " statt " + formatCurrency(driver.baselineCents, currency) +
                        " im Monatsmittel", color = Muted, fontSize = 10.sp,
                        modifier = Modifier.padding(start = 10.dp, bottom = 5.dp))
                }
            }
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
    onCloudPair: () -> Unit,
    onCloudDisconnect: () -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenTitle("Konto", "") }
        item { DataSourceBanner(data) }
        if (bankingStatus?.refresh?.lastOutcome == "failed") {
            item { RefreshFailureNotice(bankingStatus) }
        }
        item {
            PanelCard {
                SectionHeading("Cloud-Verbindung", if (cloudConnected) "Gekoppelt" else "Nicht gekoppelt")
                if (!cloudConnected) {
                    Spacer(Modifier.height(8.dp))
                    Text("Einmaligen Kopplungscode eingeben, um Saldo unterwegs zu nutzen.",
                        color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                if (cloudConnected) TextButton(onClick = onCloudDisconnect) {
                    Text("Cloud trennen", color = Purple)
                } else ActionButton("Cloud koppeln", onCloudPair)
            }
        }
        if (data.dataSource != "sample" || bankingStatus?.environment != "PRODUCTION") {
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
                "Bankabrufe laufen täglich um 08:00, 12:00, 14:00, 17:00 und 21:00 Uhr."
            else
                "Bankabruf bis $retryAt pausiert. Das Konto ist verbunden; eine erneute Freigabe ist dafür nicht nötig.")
        }
        if (bankingStatus?.environment == "PRODUCTION") {
            item {
                val enabled = !isConnecting && bankingStatus.isConfigured && bankingStatus.isActive &&
                    bankingStatus.retryAfter.toLocalBankTime() == null
                if (bankingStatus.accountCount > 0) {
                    TextButton(onClick = onConnect, enabled = enabled) {
                        Text(if (isConnecting) "Verbindung wird gestartet …" else "Bank Austria erneut verbinden",
                            color = Purple)
                    }
                } else {
                    ActionButton(if (isConnecting) "Verbindung wird gestartet …" else "Bank Austria verbinden",
                        onConnect, enabled)
                }
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

private fun String?.toBankDateTime(): String? = this?.let { value ->
    runCatching {
        OffsetDateTime.parse(value).atZoneSameInstant(BankTimeZone)
            .format(DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMANY))
    }.getOrNull()
}

@Composable
private fun RefreshFailureNotice(status: BankingConnectionStatus) {
    val attempt = status.refresh?.lastAttemptAt.toBankDateTime()
    val pausedUntil = status.retryAfter.toLocalBankTime()
    PanelCard {
        Text(if (pausedUntil != null) "Bankabruf pausiert" else "Bankabruf fehlgeschlagen",
            color = Color(0xFFFFBE7D), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            if (pausedUntil != null) "Die Bank erlaubt derzeit keinen neuen Abruf. Erneuter Versuch frühestens ab $pausedUntil."
            else "Der geplante Abruf${if (attempt != null) " am $attempt" else ""} hat nicht geklappt.",
            color = Ink, fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        Text("Saldo zeigt weiterhin den zuletzt gespeicherten Datenstand.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DataSourceBanner(data: FinanceOverview) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BalanceCard(data: FinanceOverview) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(PanelRaised).padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Kontostand", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(7.dp))
        Text(
            if (data.balanceAvailable) formatCurrency(data.balanceCents, data.currency) else "Aktuell nicht verfügbar",
            color = Ink,
            fontSize = if (data.balanceAvailable) 34.sp else 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MonthlyTotals(month: MonthlySnapshot, currency: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TotalCard("Einnahmen", month.incomeCents, currency, Green, "+", Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(43.dp).background(Divider))
        TotalCard("Ausgaben", month.outcomeCents, currency, Red, "−", Modifier.weight(1f))
    }
}

@Composable
private fun TotalCard(title: String, cents: Long, currency: String, accent: Color, sign: String, modifier: Modifier) {
    Column(modifier) {
        Text(title, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(5.dp))
        Text("$sign${formatCurrency(cents, currency)}", color = accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun NetCard(month: MonthlySnapshot, currency: String) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Überschuss", color = Muted, fontSize = 13.sp)
            Text(formatSigned(month.netCents, currency), color = if (month.netCents >= 0) Green else Red,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
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
            RecurringMonthlyTotal(confirmed.sumOf { it.amountCents }, currency, Red)
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
            RecurringMonthlyTotal(visible.sumOf { it.amountCents }, currency, Green)
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
private fun RecurringMonthlyTotal(amountCents: Long, currency: String, accent: Color) {
    Text("Monatlich ca. ${formatCurrency(amountCents, currency)}",
        color = accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun RecurringOverviewRow(
    entry: RecurringEntry,
    currency: String,
    accent: Color,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
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
    Column(Modifier.fillMaxWidth().clickable { onClick(transaction) }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(transaction.merchant, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text("${transaction.category} · ${transaction.dateLabel}", color = Muted,
                    fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Text(formatSigned(transaction.amountCents, currency), color = accent,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
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
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = Muted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChoiceChip(title: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(title, fontSize = 12.sp) },
        shape = RoundedCornerShape(7.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Background,
            labelColor = Muted,
            selectedContainerColor = PanelRaised,
            selectedLabelColor = Ink
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Divider,
            selectedBorderColor = Purple
        )
    )
}

@Composable
private fun PanelCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
        Column(Modifier.padding(top = 15.dp, bottom = 5.dp), content = { content() })
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
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Purple, contentColor = Background)) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatSigned(amountCents: Long, currencyCode: String): String =
    "${if (amountCents >= 0) "+" else "−"}${formatCurrency(kotlin.math.abs(amountCents), currencyCode)}"

private fun formatCurrency(amountCents: Long, currencyCode: String): String =
    NumberFormat.getCurrencyInstance(Locale.GERMANY).apply {
        currency = Currency.getInstance(currencyCode)
    }.format(amountCents / 100.0)
