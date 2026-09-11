package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionEntity
import com.example.ui.FinanceViewModel
import com.example.ui.components.BankLogoBadge
import com.example.ui.components.DateRangePickerDialog
import com.example.ui.components.FintechCard
import com.example.ui.components.MetricSummaryCard
import com.example.ui.components.MonthlyDualBarChart
import com.example.ui.components.TimeFilterRow
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.DebitRed
import com.example.ui.theme.EmeraldGreen
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountDetailScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit
) {
    val account = viewModel.selectedAccount.collectAsState().value
    val allTransactions by viewModel.allTransactions.collectAsState()
    val monthlyTrends by viewModel.monthlyTrendsData.collectAsState()
    val timeFilter by viewModel.timeFilter.collectAsState()
    val showDateRangeDialog by viewModel.showDateRangeDialog.collectAsState()
    val customStartDate by viewModel.customStartDate.collectAsState()
    val customEndDate by viewModel.customEndDate.collectAsState()

    if (showDateRangeDialog) {
        DateRangePickerDialog(
            initialStartDate = customStartDate,
            initialEndDate = customEndDate,
            onDismiss = { viewModel.setShowDateRangeDialog(false) },
            onConfirm = { start, end -> viewModel.setCustomDateRange(start, end) }
        )
    }

    val inrFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    val dateFormatter = remember {
        SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault())
    }

    val accountTransactions = remember(account, allTransactions) {
        if (account == null) emptyList()
        else allTransactions.filter { it.accountId == account.id || it.accountNumberLast4 == account.accountNumberLast4 }
    }

    val totalCredit = remember(accountTransactions) {
        accountTransactions.filter { it.type == "CREDIT" }.sumOf { it.amount }
    }

    val totalDebit = remember(accountTransactions) {
        accountTransactions.filter { it.type == "DEBIT" }.sumOf { it.amount }
    }

    val netInflow = totalCredit - totalDebit
    val txCount = accountTransactions.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))

            // Header: Back Arrow, Bank Logo, Account Name, Active badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                BankLogoBadge(bankCode = account?.bankCode ?: "SBI", size = 36)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account?.accountName ?: "Account Details",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "XXXX${account?.accountNumberLast4 ?: "----"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (account?.isActive == false) "Inactive" else "Active",
                        color = EmeraldGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Time Filters: 1M, 3M, 6M, 1Y, Custom
        item {
            TimeFilterRow(
                selectedFilter = timeFilter,
                onSelect = { viewModel.setTimeFilter(it) },
                onCustomClick = { viewModel.setShowDateRangeDialog(true) }
            )
        }

        // 4 Metric cards (2x2 grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricSummaryCard(
                        title = "Total Credit",
                        amount = inrFormat.format(totalCredit),
                        badgeText = null,
                        icon = Icons.Default.ArrowDownward,
                        iconColor = EmeraldGreen,
                        iconBgColor = EmeraldGreen.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )

                    MetricSummaryCard(
                        title = "Total Debit",
                        amount = inrFormat.format(totalDebit),
                        badgeText = null,
                        icon = Icons.Default.ArrowUpward,
                        iconColor = DebitRed,
                        iconBgColor = DebitRed.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricSummaryCard(
                        title = "Net Inflow",
                        amount = inrFormat.format(netInflow),
                        badgeText = null,
                        icon = Icons.Default.AccountBalanceWallet,
                        iconColor = AccentBlue,
                        iconBgColor = AccentBlue.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )

                    MetricSummaryCard(
                        title = "Transactions",
                        amount = txCount.toString(),
                        badgeText = null,
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        iconColor = AccentPurple,
                        iconBgColor = AccentPurple.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Monthly Dual Bar Chart (Credit teal vs Debit red)
        item {
            FintechCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Monthly Flow",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                MonthlyDualBarChart(data = monthlyTrends)
            }
        }

        // Recent Transactions Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "See All",
                    color = AccentBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateToTransactions() }
                )
            }
        }

        if (accountTransactions.isEmpty()) {
            item {
                Text(
                    text = "No transactions found for this account",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        val displayTxList = accountTransactions.take(5)
        items(displayTxList) { tx ->
            val isCredit = tx.type == "CREDIT"
            val sign = if (isCredit) "+" else "-"
            val amountColor = if (isCredit) EmeraldGreen else DebitRed

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .clickable { onTransactionClick(tx) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BankLogoBadge(bankCode = tx.bankCode, size = 38)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.merchant.ifBlank { tx.categoryName },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${tx.categoryName} • ${tx.paymentMethod}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$sign${inrFormat.format(tx.amount)}",
                        color = amountColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormatter.format(Date(tx.timestamp)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
