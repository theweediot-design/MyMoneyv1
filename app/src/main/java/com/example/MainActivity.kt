package com.example

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.security.BiometricAvailability
import com.example.security.SecurityManager
import com.example.ui.FinanceViewModel
import com.example.ui.components.AppBottomNav
import com.example.ui.screens.AccountDetailScreen
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.BankAnalyticsScreen
import com.example.ui.screens.BankTrendsScreen
import com.example.ui.screens.CategoryWiseScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MonthlySummaryScreen
import com.example.ui.screens.MoreSettingsScreen
import com.example.ui.screens.PinLockScreen
import com.example.ui.screens.SecuritySettingsScreen
import com.example.ui.screens.SmsAutoDetectionScreen
import com.example.ui.screens.TransactionDetailDialog
import com.example.ui.screens.TransactionsListScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeManager

class MainActivity : FragmentActivity() {

    private lateinit var securityManager: SecurityManager
    private lateinit var themeManager: ThemeManager
    private var backgroundTimestamp: Long = 0L
    private val isUnlockedState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        securityManager = SecurityManager(this)
        themeManager = ThemeManager(this)

        isUnlockedState.value = !securityManager.isPinSet
        updateFlagSecure(securityManager.isHideAmountsInRecentApps)

        setContent {
            val currentTheme by themeManager.currentTheme.collectAsState()
            val isUnlocked by isUnlockedState

            MyApplicationTheme(themeMode = currentTheme) {
                MainAppContent(
                    securityManager = securityManager,
                    themeManager = themeManager,
                    isUnlocked = isUnlocked,
                    onUnlockChange = { isUnlockedState.value = it },
                    onRequestBiometric = { onSuccess, onError ->
                        promptBiometrics(onSuccess, onError)
                    },
                    onToggleHideAmounts = { updateFlagSecure(it) }
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        backgroundTimestamp = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundTimestamp > 0L && securityManager.isPinSet) {
            val elapsed = System.currentTimeMillis() - backgroundTimestamp
            val timeout = securityManager.getAutoLockTimeoutMillis()
            if (elapsed >= timeout) {
                isUnlockedState.value = false
            }
        }
        backgroundTimestamp = 0L
    }

    fun updateFlagSecure(hide: Boolean) {
        if (hide) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun promptBiometrics(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val availability = securityManager.checkBiometricAvailability()
        if (availability != BiometricAvailability.AVAILABLE) {
            val msg = when (availability) {
                BiometricAvailability.NONE_ENROLLED -> "No fingerprint or face enrolled on device"
                BiometricAvailability.NO_HARDWARE -> "Biometric hardware is not available"
                BiometricAvailability.HW_UNAVAILABLE -> "Biometric sensor is currently unavailable"
                else -> "Biometric authentication is not supported"
            }
            onError(msg)
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock MyMoney")
            .setSubtitle("Use your fingerprint to access your finances")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Fingerprint not recognized. Try again or enter PIN.")
            }
        })

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
fun MainAppContent(
    securityManager: SecurityManager,
    themeManager: ThemeManager,
    isUnlocked: Boolean,
    onUnlockChange: (Boolean) -> Unit,
    onRequestBiometric: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onToggleHideAmounts: (Boolean) -> Unit,
    viewModel: FinanceViewModel = viewModel()
) {
    var isChangingPin by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("HOME") }
    var navigationStack by remember { mutableStateOf(listOf("HOME")) }
    var bioErrorMessage by remember { mutableStateOf<String?>(null) }

    val selectedTransaction by viewModel.selectedTransaction.collectAsState()

    // Permission request on launch
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
        )
    }

    fun navigateTo(screen: String) {
        navigationStack = navigationStack + screen
        currentScreen = screen
    }

    fun navigateBack() {
        if (navigationStack.size > 1) {
            val newStack = navigationStack.dropLast(1)
            navigationStack = newStack
            currentScreen = newStack.last()
        } else {
            currentScreen = "HOME"
        }
    }

    fun selectTab(tab: String) {
        val route = when (tab) {
            "Home" -> "HOME"
            "Transactions" -> "TRANSACTIONS"
            "Analytics" -> "ANALYTICS_BANK"
            "Accounts" -> "ACCOUNTS"
            "More" -> "MORE"
            else -> "HOME"
        }
        navigationStack = listOf(route)
        currentScreen = route
    }

    // Check PIN / Biometric Lock
    if (!isUnlocked) {
        PinLockScreen(
            securityManager = securityManager,
            isSettingPin = false,
            onRequestBiometric = {
                bioErrorMessage = null
                onRequestBiometric(
                    { onUnlockChange(true) },
                    { err -> bioErrorMessage = err }
                )
            },
            externalErrorMessage = bioErrorMessage,
            onSuccess = { onUnlockChange(true) }
        )
        return
    }

    if (isChangingPin) {
        PinLockScreen(
            securityManager = securityManager,
            isSettingPin = true,
            onSuccess = { isChangingPin = false }
        )
        return
    }

    val primaryTabs = listOf("HOME", "TRANSACTIONS", "ANALYTICS_BANK", "ACCOUNTS", "MORE")
    val isPrimaryTab = currentScreen in primaryTabs

    val currentTabName = when (currentScreen) {
        "HOME" -> "Home"
        "TRANSACTIONS" -> "Transactions"
        "ANALYTICS_BANK" -> "Analytics"
        "ACCOUNTS" -> "Accounts"
        "MORE" -> "More"
        else -> ""
    }

    BackHandler(enabled = navigationStack.size > 1) {
        navigateBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isPrimaryTab) {
                AppBottomNav(
                    currentTab = currentTabName,
                    onTabSelect = { selectTab(it) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "HOME" -> HomeScreen(
                    viewModel = viewModel,
                    onNavigate = { navigateTo(it) }
                )

                "ACCOUNTS" -> AccountsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateBack() },
                    onAccountClick = {
                        viewModel.selectAccount(it)
                        navigateTo("ACCOUNT_DETAILS")
                    }
                )

                "ACCOUNT_DETAILS" -> AccountDetailScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateBack() },
                    onNavigateToTransactions = { navigateTo("TRANSACTIONS") },
                    onTransactionClick = { viewModel.selectTransaction(it) }
                )

                "ANALYTICS_BANK" -> BankAnalyticsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateBack() },
                    onBankClick = {
                        viewModel.setTrendsBank(it)
                        navigateTo("TRENDS")
                    }
                )

                "TRENDS" -> BankTrendsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateBack() }
                )

                "TRANSACTIONS" -> TransactionsListScreen(
                    viewModel = viewModel,
                    onNavigateBack = if (navigationStack.size > 1) { { navigateBack() } } else null,
                    onTransactionClick = { viewModel.selectTransaction(it) }
                )

                "CATEGORY_WISE" -> CategoryWiseScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateBack() },
                    onCategoryClick = { cat ->
                        viewModel.setSearchQuery(cat)
                        navigateTo("TRANSACTIONS")
                    }
                )

                "MONTHLY_SUMMARY" -> MonthlySummaryScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateBack() }
                )

                "SMS_DETECTION" -> SmsAutoDetectionScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navigateBack() }
                )

                "SECURITY" -> SecuritySettingsScreen(
                    securityManager = securityManager,
                    onNavigateBack = { navigateBack() },
                    onChangePinRequested = { isChangingPin = true },
                    onToggleHideAmounts = onToggleHideAmounts
                )

                "MORE" -> MoreSettingsScreen(
                    viewModel = viewModel,
                    themeManager = themeManager,
                    onNavigate = { navigateTo(it) }
                )
            }
        }

        // Transaction Detail Dialog
        selectedTransaction?.let { tx ->
            TransactionDetailDialog(
                transaction = tx,
                onDismiss = { viewModel.selectTransaction(null) },
                onDelete = { id ->
                    viewModel.deleteTransaction(id)
                    viewModel.selectTransaction(null)
                },
                onUpdateCategory = { item, newCat ->
                    viewModel.updateTransaction(item.copy(categoryName = newCat))
                    viewModel.selectTransaction(null)
                }
            )
        }
    }
}

