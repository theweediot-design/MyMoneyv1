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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.FinanceViewModel
import com.example.ui.components.BankLogoBadge
import com.example.ui.components.FintechCard
import com.example.ui.components.MetricSummaryCard
import com.example.ui.components.MonthSelectorPill
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentPurple
import com.example.ui.theme.DebitRed
import com.example.ui.theme.EmeraldGreen
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: FinanceViewModel,
    onNavigate: (String) -> Unit
) {
    val summary by viewModel.periodSummary.collectAsState()
    val bankOverview by viewModel.bankOverviewList.collectAsState()
    val currentMonth by viewModel.selectedMonth.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()

    var showMonthMenu by remember { mutableStateOf(false) }

    val inrFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))

            // Header: Good Morning 👋
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "My",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Money",
                            color = EmeraldGreen,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dashboard",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Your finances at a glance",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                Row {
                    IconButton(
                        onClick = { onNavigate("SMS_DETECTION") },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "SMS Detection Status",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onNavigate("SECURITY") },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Security Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Month Selector Dropdown
        item {
            Box {
                MonthSelectorPill(
                    currentLabel = currentMonth.label,
                    onClick = { showMonthMenu = true }
                )

                DropdownMenu(
                    expanded = showMonthMenu,
                    onDismissRequest = { showMonthMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    availableMonths.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    color = if (option == currentMonth) EmeraldGreen else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                viewModel.setSelectedMonth(option)
                                showMonthMenu = false
                            }
                        )
                    }
                }
            }
        }

        // 4 Summary Metrics (2x2 Grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricSummaryCard(
                        title = "Total Credit",
                        amount = inrFormat.format(summary.totalCredit),
                        badgeText = summary.creditChangePct,
                        isPositiveBadge = !summary.creditChangePct.startsWith("-"),
                        icon = Icons.Default.ArrowDownward,
                        iconColor = EmeraldGreen,
                        iconBgColor = EmeraldGreen.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )

                    MetricSummaryCard(
                        title = "Total Debit",
                        amount = inrFormat.format(summary.totalDebit),
                        badgeText = summary.debitChangePct,
                        isPositiveBadge = summary.debitChangePct.startsWith("-"),
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
                        title = "Net Flow",
                        amount = inrFormat.format(summary.netFlow),
                        badgeText = null,
                        icon = Icons.Default.AccountBalanceWallet,
                        iconColor = AccentBlue,
                        iconBgColor = AccentBlue.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )

                    MetricSummaryCard(
                        title = "Transactions",
                        amount = summary.transactionCount.toString(),
                        badgeText = null,
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        iconColor = AccentPurple,
                        iconBgColor = AccentPurple.copy(alpha = 0.15f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Quick Navigation Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val quickActions = listOf(
                    Triple("Transactions", Icons.AutoMirrored.Filled.ReceiptLong, "TRANSACTIONS"),
                    Triple("Analytics", Icons.Default.BarChart, "ANALYTICS_BANK"),
                    Triple("Accounts", Icons.Default.AccountBalance, "ACCOUNTS"),
                    Triple("More", Icons.Default.Settings, "MORE")
                )

                quickActions.forEach { (label, icon, route) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onNavigate(route) }
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Bank-wise Overview Section
        item {
            FintechCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bank-wise Overview",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "See All",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigate("ACCOUNTS") }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (bankOverview.isEmpty()) {
                    Text(
                        text = "No bank transactions recorded for this month.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        bankOverview.forEach { item ->
                            val barColor = try {
                                Color(android.graphics.Color.parseColor(item.colorHex))
                            } catch (_: Exception) {
                                EmeraldGreen
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setTrendsBank(item.bankCode)
                                        onNavigate("ACCOUNTS")
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BankLogoBadge(bankCode = item.bankCode, size = 32)
                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = item.bankName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(90.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                // Colored pill progress bar
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(item.proportion)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(barColor)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = inrFormat.format(item.totalAmount),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
