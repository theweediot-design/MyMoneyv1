package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AccountEntity
import com.example.data.model.BankEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import com.example.data.preferences.BankPreferenceManager
import com.example.data.repository.FinanceRepository
import com.example.sms.LiveChecklistState
import com.example.sms.ScanProgress
import com.example.sms.SmsDetectionManager
import com.example.sms.SmsScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class MonthOption(val year: Int, val month: Int, val label: String)

data class PeriodSummary(
    val totalCredit: Double = 0.0,
    val totalDebit: Double = 0.0,
    val netFlow: Double = 0.0,
    val transactionCount: Int = 0,
    val creditChangePct: String = "0%",
    val debitChangePct: String = "0%"
)

data class BankOverviewItem(
    val bankCode: String,
    val bankName: String,
    val colorHex: String,
    val totalAmount: Double,
    val proportion: Float,
    val txCount: Int
)

data class CategorySpendItem(
    val categoryName: String,
    val colorHex: String,
    val amount: Double,
    val percentage: Int
)

data class MonthlyBarData(
    val monthName: String,
    val credit: Double,
    val debit: Double
)

data class WeeklyBarData(
    val weekLabel: String,
    val credit: Double,
    val debit: Double
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = FinanceRepository(db)
    private val smsScanner = SmsScanner(application)
    private val smsManager = SmsDetectionManager.getInstance(application)
    private val bankPrefManager = BankPreferenceManager(application)

    init {
        viewModelScope.launch {
            repository.sanitizeDatabase()
        }
    }

    val hiddenAccountIds: StateFlow<Set<String>> = bankPrefManager.hiddenAccountIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAccounts: StateFlow<List<AccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBanks: StateFlow<List<BankEntity>> = repository.allBanks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Complete list of all supported Indian banks for the "Manage My Banks" feature.
     * Always includes all 19 major Indian banks in specified priority order, plus any
     * additional detected banks (like "OTHERS") if transactions exist.
     */
    val allManageableBanks: StateFlow<List<String>> = allTransactions.map { txList ->
        val fromTx = txList.map { it.bankCode }.filter { it.isNotBlank() }
        val extra = fromTx.filter { it !in ALL_SUPPORTED_INDIAN_BANKS && it != "AUBANK" }.distinct()
        ALL_SUPPORTED_INDIAN_BANKS + extra
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ALL_SUPPORTED_INDIAN_BANKS)

    // Persistent bank visibility selection backed by DataStore Preferences
    val selectedBanksFilter: StateFlow<Set<String>> = combine(
        bankPrefManager.activeBanksFlow,
        allManageableBanks
    ) { storedBanks, manageable ->
        if (storedBanks == null) {
            // Initial default: all manageable banks are active
            manageable.toSet()
        } else {
            // User explicitly configured active banks
            storedBanks
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ALL_SUPPORTED_INDIAN_BANKS.toSet())

    // Discovered banks for horizontal quick-filter chips row
    val discoveredBanks: StateFlow<List<String>> = combine(
        allTransactions,
        allAccounts,
        selectedBanksFilter
    ) { txList, accList, selected ->
        val fromTx = txList.map { it.bankCode }.filter { it.isNotBlank() }
        val fromAcc = accList.map { it.bankCode }.filter { it.isNotBlank() }
        val combined = (fromTx + fromAcc + selected).distinct().filter { it.isNotBlank() }
        val sorted = ALL_SUPPORTED_INDIAN_BANKS.filter { combined.contains(it) || (it == "AU" && combined.contains("AUBANK")) } +
                combined.filter { it !in ALL_SUPPORTED_INDIAN_BANKS && it != "AUBANK" }
        if (sorted.isNotEmpty()) sorted else ALL_SUPPORTED_INDIAN_BANKS
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ALL_SUPPORTED_INDIAN_BANKS)

    private fun isBankSelected(bankCode: String, selectedBanks: Set<String>): Boolean {
        if (selectedBanks.isEmpty()) return false
        return selectedBanks.contains(bankCode) ||
                (bankCode == "AUBANK" && selectedBanks.contains("AU")) ||
                (bankCode == "AU" && selectedBanks.contains("AUBANK"))
    }

    private val _showManageBanksDialog = MutableStateFlow(false)
    val showManageBanksDialog: StateFlow<Boolean> = _showManageBanksDialog.asStateFlow()

    fun setShowManageBanksDialog(show: Boolean) {
        _showManageBanksDialog.value = show
    }

    fun saveActiveBanks(banks: Set<String>) {
        viewModelScope.launch {
            bankPrefManager.saveActiveBanks(banks)
        }
    }

    fun toggleBankFilter(bankCode: String) {
        viewModelScope.launch {
            val current = selectedBanksFilter.value
            bankPrefManager.toggleBank(bankCode, current)
        }
    }

    fun clearBankFilter() {
        viewModelScope.launch {
            bankPrefManager.clearAll()
        }
    }

    fun selectAllBanks() {
        viewModelScope.launch {
            bankPrefManager.selectAll(allManageableBanks.value)
        }
    }

    // Visible transactions filtered strictly by active bank selection
    // Does NOT delete transactions from Room DB; only filters in UI data streams
    val visibleTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        selectedBanksFilter
    ) { list, selectedBanks ->
        if (selectedBanks.isEmpty()) {
            emptyList()
        } else {
            list.filter { isBankSelected(it.bankCode, selectedBanks) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Visible accounts filtered strictly by active bank selection and validity
    // Does NOT delete accounts from Room DB; only filters in UI data streams
    // Completely excludes any "Unknown Account" or phantom accounts without last4
    val visibleAccounts: StateFlow<List<AccountEntity>> = combine(
        allAccounts,
        selectedBanksFilter
    ) { accounts, selectedBanks ->
        if (selectedBanks.isEmpty()) {
            emptyList()
        } else {
            accounts.filter {
                isBankSelected(it.bankCode, selectedBanks) &&
                        it.accountNumberLast4.isNotBlank() &&
                        !it.accountName.contains("Unknown", ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleAccountHidden(account: AccountEntity) {
        viewModelScope.launch {
            val isCurrentlyHidden = hiddenAccountIds.value.contains(account.id.toString())
            bankPrefManager.toggleAccountHidden(account.id, hiddenAccountIds.value)
            repository.updateAccount(account.copy(isActive = isCurrentlyHidden))
        }
    }

    val detectionChecklist: StateFlow<LiveChecklistState> = smsManager.checklistState
    val isDetectionServiceActive: StateFlow<Boolean> = smsManager.isServiceActive
    val scanProgress: StateFlow<ScanProgress> = smsScanner.scanProgress

    private fun getCurrentMonthOption(): MonthOption {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return MonthOption(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH),
            label = sdf.format(cal.time)
        )
    }

    private val _selectedMonth = MutableStateFlow(getCurrentMonthOption())
    val selectedMonth: StateFlow<MonthOption> = _selectedMonth.asStateFlow()

    fun setSelectedMonth(month: MonthOption) {
        _selectedMonth.value = month
    }

    // Dynamic available months derived from real-world current date and distinct months in allTransactions
    val availableMonths: StateFlow<List<MonthOption>> = allTransactions.map { transactions ->
        generateAvailableMonths(transactions)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        generateAvailableMonths(emptyList())
    )

    private fun generateAvailableMonths(transactions: List<TransactionEntity>): List<MonthOption> {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()

        // Month key: Pair(year, month)
        val monthKeys = linkedSetOf<Pair<Int, Int>>()

        // 1. Current real-world month + recent 5 months as default baseline
        for (i in 0..5) {
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
            monthKeys.add(c.get(Calendar.YEAR) to c.get(Calendar.MONTH))
        }

        // 2. Distinct months/years actually present in allTransactions
        for (tx in transactions) {
            cal.timeInMillis = tx.timestamp
            monthKeys.add(cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH))
        }

        // Sort descending: newest month first
        return monthKeys.sortedWith(
            compareByDescending<Pair<Int, Int>> { it.first }
                .thenByDescending { it.second }
        ).map { (year, month) ->
            val c = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            MonthOption(year, month, sdf.format(c.time))
        }
    }

    // Filter states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _typeFilter = MutableStateFlow("ALL") // "ALL", "CREDIT", "DEBIT"
    val typeFilter: StateFlow<String> = _typeFilter.asStateFlow()

    private val _selectedBankFilter = MutableStateFlow<String?>("All Banks")
    val selectedBankFilter: StateFlow<String?> = _selectedBankFilter.asStateFlow()

    private val _selectedAccountFilter = MutableStateFlow<String?>("All Accounts")
    val selectedAccountFilter: StateFlow<String?> = _selectedAccountFilter.asStateFlow()

    private val _minAmountFilter = MutableStateFlow<Double?>(null)
    val minAmountFilter: StateFlow<Double?> = _minAmountFilter.asStateFlow()

    private val _maxAmountFilter = MutableStateFlow<Double?>(null)
    val maxAmountFilter: StateFlow<Double?> = _maxAmountFilter.asStateFlow()

    // Analytics Controls
    private val _analyticsTab = MutableStateFlow("CREDIT") // "CREDIT", "DEBIT", "NET"
    val analyticsTab: StateFlow<String> = _analyticsTab.asStateFlow()

    private val _trendsBank = MutableStateFlow("All Banks")
    val trendsBank: StateFlow<String> = _trendsBank.asStateFlow()

    private val _trendsMetric = MutableStateFlow("BOTH") // "CREDIT", "DEBIT", "BOTH"
    val trendsMetric: StateFlow<String> = _trendsMetric.asStateFlow()

    private val _timeFilter = MutableStateFlow("6M") // "1M", "3M", "6M", "1Y", "Custom"
    val timeFilter: StateFlow<String> = _timeFilter.asStateFlow()

    private val _customStartDate = MutableStateFlow<Long?>(null)
    val customStartDate: StateFlow<Long?> = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow<Long?>(null)
    val customEndDate: StateFlow<Long?> = _customEndDate.asStateFlow()

    private val _showDateRangeDialog = MutableStateFlow(false)
    val showDateRangeDialog: StateFlow<Boolean> = _showDateRangeDialog.asStateFlow()

    // Selected account for AccountDetailScreen
    private val _selectedAccount = MutableStateFlow<AccountEntity?>(null)
    val selectedAccount: StateFlow<AccountEntity?> = _selectedAccount.asStateFlow()

    // Selected transaction for details dialog
    private val _selectedTransaction = MutableStateFlow<TransactionEntity?>(null)
    val selectedTransaction: StateFlow<TransactionEntity?> = _selectedTransaction.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setTypeFilter(type: String) { _typeFilter.value = type }
    fun setBankFilter(bank: String?) { _selectedBankFilter.value = bank }
    fun setAccountFilter(acc: String?) { _selectedAccountFilter.value = acc }
    fun setMinAmount(amt: Double?) { _minAmountFilter.value = amt }
    fun setMaxAmount(amt: Double?) { _maxAmountFilter.value = amt }
    fun setAnalyticsTab(tab: String) { _analyticsTab.value = tab }
    fun setTrendsBank(bank: String) { _trendsBank.value = bank }
    fun setTrendsMetric(metric: String) { _trendsMetric.value = metric }
    fun setTimeFilter(tf: String) {
        if (tf == "Custom") {
            _showDateRangeDialog.value = true
        } else {
            _timeFilter.value = tf
        }
    }
    fun setShowDateRangeDialog(show: Boolean) {
        _showDateRangeDialog.value = show
    }
    fun setCustomDateRange(start: Long, end: Long) {
        _customStartDate.value = start
        _customEndDate.value = end
        _timeFilter.value = "Custom"
        _showDateRangeDialog.value = false
    }
    fun selectAccount(account: AccountEntity?) { _selectedAccount.value = account }
    fun selectTransaction(tx: TransactionEntity?) { _selectedTransaction.value = tx }

    fun resetFilters() {
        _searchQuery.value = ""
        _typeFilter.value = "ALL"
        _selectedBankFilter.value = "All Banks"
        _selectedAccountFilter.value = "All Accounts"
        _minAmountFilter.value = null
        _maxAmountFilter.value = null
        selectAllBanks()
    }

    // Filtered transactions for Transactions List screen
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        visibleTransactions,
        selectedMonth,
        searchQuery,
        typeFilter,
        selectedBankFilter,
        selectedAccountFilter,
        minAmountFilter,
        maxAmountFilter
    ) { params ->
        val list = params[0] as List<TransactionEntity>
        val month = params[1] as MonthOption
        val query = (params[2] as String).lowercase(Locale.getDefault())
        val type = params[3] as String
        val bank = params[4] as String?
        val account = params[5] as String?
        val minAmt = params[6] as Double?
        val maxAmt = params[7] as Double?

        list.filter { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            val inMonth = cal.get(Calendar.YEAR) == month.year && cal.get(Calendar.MONTH) == month.month

            val matchType = when (type) {
                "CREDIT" -> tx.type == "CREDIT"
                "DEBIT" -> tx.type == "DEBIT"
                else -> true
            }

            val matchBank = bank.isNullOrBlank() || bank == "All Banks" || tx.bankCode.equals(bank, ignoreCase = true) || tx.bankName.contains(bank, ignoreCase = true)
            val matchAccount = account.isNullOrBlank() || account == "All Accounts" || tx.accountName.contains(account, ignoreCase = true)

            val matchMin = minAmt == null || tx.amount >= minAmt
            val matchMax = maxAmt == null || tx.amount <= maxAmt

            val matchQuery = query.isBlank() ||
                    tx.merchant.lowercase(Locale.getDefault()).contains(query) ||
                    tx.accountName.lowercase(Locale.getDefault()).contains(query) ||
                    tx.categoryName.lowercase(Locale.getDefault()).contains(query) ||
                    tx.bankName.lowercase(Locale.getDefault()).contains(query) ||
                    tx.refNumber.lowercase(Locale.getDefault()).contains(query) ||
                    tx.utrNumber.lowercase(Locale.getDefault()).contains(query)

            inMonth && matchType && matchBank && matchAccount && matchMin && matchMax && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Period summary metrics for Home Dashboard
    val periodSummary: StateFlow<PeriodSummary> = combine(
        visibleTransactions,
        selectedMonth
    ) { list, month ->
        var credit = 0.0
        var debit = 0.0
        var count = 0

        val cal = Calendar.getInstance()
        for (tx in list) {
            cal.timeInMillis = tx.timestamp
            if (cal.get(Calendar.YEAR) == month.year && cal.get(Calendar.MONTH) == month.month) {
                if (tx.type == "CREDIT") credit += tx.amount else debit += tx.amount
                count++
            }
        }

        val prevCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, month.year)
            set(Calendar.MONTH, month.month)
            add(Calendar.MONTH, -1)
        }
        val prevYear = prevCal.get(Calendar.YEAR)
        val prevMonth = prevCal.get(Calendar.MONTH)

        var prevCredit = 0.0
        var prevDebit = 0.0
        for (tx in list) {
            cal.timeInMillis = tx.timestamp
            if (cal.get(Calendar.YEAR) == prevYear && cal.get(Calendar.MONTH) == prevMonth) {
                if (tx.type == "CREDIT") prevCredit += tx.amount else prevDebit += tx.amount
            }
        }

        val creditPct = if (prevCredit > 0) {
            val pct = ((credit - prevCredit) / prevCredit) * 100
            if (pct >= 0) "+${pct.toInt()}%" else "${pct.toInt()}%"
        } else if (credit > 0) "+100%" else "0%"

        val debitPct = if (prevDebit > 0) {
            val pct = ((debit - prevDebit) / prevDebit) * 100
            if (pct >= 0) "+${pct.toInt()}%" else "${pct.toInt()}%"
        } else if (debit > 0) "+100%" else "0%"

        PeriodSummary(
            totalCredit = credit,
            totalDebit = debit,
            netFlow = credit - debit,
            transactionCount = count,
            creditChangePct = creditPct,
            debitChangePct = debitPct
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeriodSummary())

    // Bank-wise overview for Home Dashboard
    val bankOverviewList: StateFlow<List<BankOverviewItem>> = combine(
        visibleTransactions,
        selectedMonth
    ) { list, month ->
        val cal = Calendar.getInstance()
        val bankMap = mutableMapOf<String, Double>()
        val bankCountMap = mutableMapOf<String, Int>()

        for (tx in list) {
            cal.timeInMillis = tx.timestamp
            if (cal.get(Calendar.YEAR) == month.year && cal.get(Calendar.MONTH) == month.month) {
                val code = tx.bankCode
                bankMap[code] = (bankMap[code] ?: 0.0) + tx.amount
                bankCountMap[code] = (bankCountMap[code] ?: 0) + 1
            }
        }

        val totalAll = bankMap.values.sum().coerceAtLeast(1.0)

        bankMap.entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
            .map { (code, amt) ->
                BankOverviewItem(
                    bankCode = code,
                    bankName = ALL_BANK_NAMES[code] ?: code,
                    colorHex = ALL_BANK_COLORS[code] ?: "#3B82F6",
                    totalAmount = amt,
                    proportion = (amt / totalAll).toFloat().coerceIn(0.05f, 1.0f),
                    txCount = bankCountMap[code] ?: 0
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category breakdown for Screen 8 (Spending Categories)
    val categoryBreakdown: StateFlow<List<CategorySpendItem>> = combine(
        visibleTransactions,
        selectedMonth
    ) { list, month ->
        val cal = Calendar.getInstance()
        val catMap = mutableMapOf<String, Double>()
        val colorMap = mutableMapOf<String, String>()

        for (tx in list) {
            cal.timeInMillis = tx.timestamp
            if (tx.type == "DEBIT" && cal.get(Calendar.YEAR) == month.year && cal.get(Calendar.MONTH) == month.month) {
                catMap[tx.categoryName] = (catMap[tx.categoryName] ?: 0.0) + tx.amount
                colorMap[tx.categoryName] = tx.categoryColorHex
            }
        }

        val total = catMap.values.sum().coerceAtLeast(1.0)
        catMap.entries.sortedByDescending { it.value }.map { (cat, amt) ->
            val pct = ((amt / total) * 100).toInt()
            CategorySpendItem(
                categoryName = cat,
                colorHex = colorMap[cat] ?: "#3B82F6",
                amount = amt,
                percentage = pct
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monthly data for Bar Charts and Trends (Calculated dynamically for the previous 6 months)
    fun isTimestampInFilter(timestamp: Long, filter: String, startCustom: Long?, endCustom: Long?): Boolean {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        return when (filter) {
            "1M" -> {
                cal.timeInMillis = now
                cal.add(Calendar.MONTH, -1)
                timestamp >= cal.timeInMillis
            }
            "3M" -> {
                cal.timeInMillis = now
                cal.add(Calendar.MONTH, -3)
                timestamp >= cal.timeInMillis
            }
            "6M" -> {
                cal.timeInMillis = now
                cal.add(Calendar.MONTH, -6)
                timestamp >= cal.timeInMillis
            }
            "1Y" -> {
                cal.timeInMillis = now
                cal.add(Calendar.YEAR, -1)
                timestamp >= cal.timeInMillis
            }
            "Custom" -> {
                val start = startCustom ?: 0L
                val end = if (endCustom != null) endCustom + 86400000L - 1 else Long.MAX_VALUE
                timestamp in start..end
            }
            else -> true
        }
    }

    // Monthly data for Bar Charts and Trends (Calculated dynamically based on timeFilter)
    val monthlyTrendsData: StateFlow<List<MonthlyBarData>> = combine(
        visibleTransactions,
        _trendsBank,
        timeFilter,
        customStartDate,
        customEndDate
    ) { params ->
        val list = params[0] as List<TransactionEntity>
        val bankFilter = params[1] as String
        val tf = params[2] as String
        val cStart = params[3] as Long?
        val cEnd = params[4] as Long?

        val cal = Calendar.getInstance()
        val sdfShort = SimpleDateFormat("MMM", Locale.getDefault())

        val monthsCount = when (tf) {
            "1M" -> 1
            "3M" -> 3
            "6M" -> 6
            "1Y" -> 12
            "Custom" -> {
                if (cStart != null && cEnd != null) {
                    val startCal = Calendar.getInstance().apply { timeInMillis = cStart }
                    val endCal = Calendar.getInstance().apply { timeInMillis = cEnd }
                    val diff = (endCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)) * 12 +
                            (endCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH)) + 1
                    diff.coerceIn(1, 24)
                } else 6
            }
            else -> 6
        }

        val months = (monthsCount - 1 downTo 0).map { offset ->
            val c = Calendar.getInstance().apply { add(Calendar.MONTH, -offset) }
            Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), sdfShort.format(c.time))
        }

        months.map { (yr, mo, label) ->
            var credit = 0.0
            var debit = 0.0

            for (tx in list) {
                if (isTimestampInFilter(tx.timestamp, tf, cStart, cEnd)) {
                    cal.timeInMillis = tx.timestamp
                    val matchBank = bankFilter == "All Banks" || tx.bankCode.equals(bankFilter, true) || tx.bankName.contains(bankFilter, true)
                    if (matchBank && cal.get(Calendar.YEAR) == yr && cal.get(Calendar.MONTH) == mo) {
                        if (tx.type == "CREDIT") credit += tx.amount else debit += tx.amount
                    }
                }
            }

            MonthlyBarData(label, credit, debit)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Weekly summary for Screen 9 (Weekly Bar chart W1-W5 calculated from real data)
    val weeklySummaryData: StateFlow<List<WeeklyBarData>> = combine(
        visibleTransactions,
        selectedMonth
    ) { list, month ->
        val cal = Calendar.getInstance()
        val weekCredits = DoubleArray(5) { 0.0 }
        val weekDebits = DoubleArray(5) { 0.0 }

        for (tx in list) {
            cal.timeInMillis = tx.timestamp
            if (cal.get(Calendar.YEAR) == month.year && cal.get(Calendar.MONTH) == month.month) {
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val weekIndex = ((day - 1) / 7).coerceIn(0, 4)
                if (tx.type == "CREDIT") {
                    weekCredits[weekIndex] += tx.amount
                } else {
                    weekDebits[weekIndex] += tx.amount
                }
            }
        }

        (1..5).map { w ->
            WeeklyBarData("W$w", weekCredits[w - 1], weekDebits[w - 1])
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bank-wise Comparison data for Screen 4
    val bankWiseComparison: StateFlow<List<BankOverviewItem>> = combine(
        visibleTransactions,
        analyticsTab,
        timeFilter,
        customStartDate,
        customEndDate
    ) { params ->
        val list = params[0] as List<TransactionEntity>
        val tab = params[1] as String
        val tf = params[2] as String
        val cStart = params[3] as Long?
        val cEnd = params[4] as Long?

        val bankAmounts = mutableMapOf<String, Double>()
        val bankCountMap = mutableMapOf<String, Int>()

        for (tx in list) {
            if (isTimestampInFilter(tx.timestamp, tf, cStart, cEnd)) {
                val matchesTab = when (tab) {
                    "CREDIT" -> tx.type == "CREDIT"
                    "DEBIT" -> tx.type == "DEBIT"
                    else -> true
                }
                if (matchesTab) {
                    val amt = if (tab == "NET") {
                        if (tx.type == "CREDIT") tx.amount else -tx.amount
                    } else tx.amount
                    val code = tx.bankCode
                    bankAmounts[code] = (bankAmounts[code] ?: 0.0) + amt
                    bankCountMap[code] = (bankCountMap[code] ?: 0) + 1
                }
            }
        }

        val maxVal = bankAmounts.values.map { kotlin.math.abs(it) }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

        bankAmounts.entries
            .filter { if (tab == "NET") it.value != 0.0 else it.value > 0.0 }
            .sortedByDescending { it.value }
            .map { (code, amt) ->
                BankOverviewItem(
                    bankCode = code,
                    bankName = ALL_BANK_NAMES[code] ?: code,
                    colorHex = ALL_BANK_COLORS[code] ?: "#3B82F6",
                    totalAmount = amt,
                    proportion = (kotlin.math.abs(amt) / maxVal).toFloat().coerceIn(0.05f, 1.0f),
                    txCount = bankCountMap[code] ?: 0
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Actions
    fun updateTransaction(tx: TransactionEntity) {
        viewModelScope.launch { repository.updateTransaction(tx) }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }

    fun triggerScan(onComplete: ((ScanProgress) -> Unit)? = null) {
        viewModelScope.launch {
            val res = smsScanner.scanInbox()
            onComplete?.invoke(res)
        }
    }

    fun toggleSmsService(active: Boolean) {
        smsManager.toggleService(active)
    }

    fun exportToCsv(context: android.content.Context) {
        viewModelScope.launch {
            repository.exportTransactionsToCsv(context, allTransactions.value)
        }
    }

    companion object {
        val ALL_SUPPORTED_INDIAN_BANKS = listOf(
            "SBI",
            "KOTAK",
            "HDFC",
            "ICICI",
            "AXIS",
            "PNB",
            "BOB",
            "CANARA",
            "UNION",
            "INDIAN",
            "CENTRAL",
            "INDUSIND",
            "IDFC",
            "YES",
            "FEDERAL",
            "BANDHAN",
            "PAYTM",
            "AIRTEL",
            "AU"
        )

        val ALL_KNOWN_BANKS = ALL_SUPPORTED_INDIAN_BANKS + listOf("AUBANK", "OTHERS")

        val ALL_BANK_COLORS = mapOf(
            "SBI" to "#2563EB",
            "KOTAK" to "#DC2626",
            "HDFC" to "#0284C7",
            "ICICI" to "#EA580C",
            "AXIS" to "#BE185D",
            "PNB" to "#A21CAF",
            "BOB" to "#F97316",
            "CANARA" to "#0284C7",
            "UNION" to "#1D4ED8",
            "INDIAN" to "#B45309",
            "CENTRAL" to "#0F766E",
            "INDUSIND" to "#831843",
            "IDFC" to "#9333EA",
            "YES" to "#2563EB",
            "FEDERAL" to "#F59E0B",
            "BANDHAN" to "#0D9488",
            "PAYTM" to "#0EA5E9",
            "AIRTEL" to "#EF4444",
            "AU" to "#7C3AED",
            "AUBANK" to "#7C3AED",
            "OTHERS" to "#64748B"
        )

        val ALL_BANK_NAMES = mapOf(
            "SBI" to "State Bank of India",
            "KOTAK" to "Kotak Mahindra Bank",
            "HDFC" to "HDFC Bank",
            "ICICI" to "ICICI Bank",
            "AXIS" to "Axis Bank",
            "PNB" to "Punjab National Bank",
            "BOB" to "Bank of Baroda",
            "CANARA" to "Canara Bank",
            "UNION" to "Union Bank of India",
            "INDIAN" to "Indian Bank",
            "CENTRAL" to "Central Bank of India",
            "INDUSIND" to "IndusInd Bank",
            "IDFC" to "IDFC FIRST Bank",
            "YES" to "YES Bank",
            "FEDERAL" to "Federal Bank",
            "BANDHAN" to "Bandhan Bank",
            "PAYTM" to "Paytm Payments Bank",
            "AIRTEL" to "Airtel Payments Bank",
            "AU" to "AU Small Finance Bank",
            "AUBANK" to "AU Small Finance Bank",
            "OTHERS" to "Other Bank"
        )
    }
}
