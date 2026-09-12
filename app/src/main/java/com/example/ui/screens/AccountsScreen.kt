package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.data.model.AccountEntity
import com.example.ui.FinanceViewModel
import com.example.ui.components.BankFilterChipsRow
import com.example.ui.components.BankLogoBadge
import com.example.ui.components.FintechCard
import com.example.ui.components.ManageBanksDialog
import com.example.ui.components.NoBanksSelectedEmptyCard
import com.example.ui.theme.EmeraldGreen
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AccountsScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit,
    onAccountClick: (AccountEntity) -> Unit
) {
    val accounts by viewModel.visibleAccounts.collectAsState()
    val allTransactions by viewModel.visibleTransactions.collectAsState()
    val discoveredBanks by viewModel.discoveredBanks.collectAsState()
    val selectedBanks by viewModel.selectedBanksFilter.collectAsState()
    val allManageableBanks by viewModel.allManageableBanks.collectAsState()
    val showManageBanksDialog by viewModel.showManageBanksDialog.collectAsState()

    var filterActiveOnly by remember { mutableStateOf(false) }

    if (showManageBanksDialog) {
        ManageBanksDialog(
            allBanks = allManageableBanks,
            activeBanks = selectedBanks,
            onDismiss = { viewModel.setShowManageBanksDialog(false) },
            onSave = {
                viewModel.saveActiveBanks(it)
                viewModel.setShowManageBanksDialog(false)
            }
        )
    }

    val expandedBanks = remember {
        mutableStateMapOf<String, Boolean>()
    }

    val inrFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 0
        }
    }

    val displayedAccounts = remember(accounts, filterActiveOnly) {
        if (filterActiveOnly) accounts.filter { it.isActive } else accounts
    }

    val groupedByBank = remember(displayedAccounts) {
        val groups = displayedAccounts.groupBy { it.bankCode }
        groups.entries.map { it.key to it.value }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))

            // Top Header: Back Arrow + My Accounts + Manage Banks button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "My Accounts",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { viewModel.setShowManageBanksDialog(true) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Manage Banks",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Bank Selection Filter Chips Row
        item {
            BankFilterChipsRow(
                discoveredBanks = discoveredBanks,
                selectedBanks = selectedBanks,
                onToggleBank = { viewModel.toggleBankFilter(it) },
                onClearFilter = { viewModel.clearBankFilter() },
                onSelectAll = { viewModel.selectAllBanks() },
                onManageBanksClick = { viewModel.setShowManageBanksDialog(true) }
            )
        }

        if (selectedBanks.isEmpty()) {
            item {
                NoBanksSelectedEmptyCard(
                    onManageBanksClick = { viewModel.setShowManageBanksDialog(true) }
                )
            }
        } else {
            // Filter chips: All Accounts | Active
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (!filterActiveOnly) EmeraldGreen else MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                if (!filterActiveOnly) EmeraldGreen else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { filterActiveOnly = false }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "All Accounts (${accounts.size})",
                            color = if (!filterActiveOnly) Color(0xFF042F24) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (filterActiveOnly) EmeraldGreen else MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                if (filterActiveOnly) EmeraldGreen else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { filterActiveOnly = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Active (${accounts.count { it.isActive }})",
                            color = if (filterActiveOnly) Color(0xFF042F24) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (accounts.isEmpty()) {
                item {
                    FintechCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No accounts found for selected banks",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Scan your transactional SMS inbox from More > Auto SMS Detection to discover bank accounts from your selected banks, or select more banks in Manage Banks.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }

        // Expandable Bank Accordions
        items(groupedByBank) { (bankCode, bankAccounts) ->
            val isExpanded = expandedBanks[bankCode] ?: true
            val bankName = bankAccounts.firstOrNull()?.bankName ?: bankCode

            FintechCard(modifier = Modifier.fillMaxWidth()) {
                // Header row of the Accordion
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedBanks[bankCode] = !isExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BankLogoBadge(bankCode = bankCode, size = 36)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = bankName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${bankAccounts.size} Accounts",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Child Account Rows
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        bankAccounts.forEach { account ->
                            val accountTx = allTransactions.filter { it.accountId == account.id }
                            val totalCredits = accountTx.filter { it.type == "CREDIT" }.sumOf { it.amount }
                            val totalDebits = accountTx.filter { it.type == "DEBIT" }.sumOf { it.amount }
                            val displayBalance = totalCredits - totalDebits

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        viewModel.selectAccount(account)
                                        onAccountClick(account)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = account.accountName,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "XXXX${account.accountNumberLast4}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = inrFormat.format(displayBalance),
                                        color = EmeraldGreen,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Details",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
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
