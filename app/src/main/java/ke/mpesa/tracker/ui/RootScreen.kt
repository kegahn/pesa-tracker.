package ke.mpesa.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ke.mpesa.tracker.data.Bill

private enum class Tab(val label: String) { Spending("Spending"), Bills("Bills") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    vm: MainViewModel,
    hasSmsPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var tab by remember { mutableStateOf(Tab.Spending) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorBill by remember { mutableStateOf<Bill?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val status by vm.status.collectAsState()

    LaunchedEffect(status) {
        status?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearStatus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (tab == Tab.Spending) "Daily spending" else "Monthly bills",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    if (tab == Tab.Spending && hasSmsPermission) {
                        IconButton(onClick = vm::importInbox) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Scan my messages")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            Icon(
                                if (entry == Tab.Spending) Icons.Outlined.AccountBalanceWallet
                                else Icons.Filled.CheckCircle,
                                contentDescription = null
                            )
                        },
                        label = { Text(entry.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.Bills) {
                ExtendedFloatingActionButton(
                    text = { Text("Add bill") },
                    icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                    onClick = {
                        editorBill = null
                        editorOpen = true
                    }
                )
            }
        }
    ) { padding ->
        when {
            tab == Tab.Spending && !hasSmsPermission ->
                PermissionGate(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onRequestPermission = onRequestPermission
                )

            tab == Tab.Spending ->
                SpendingScreen(vm = vm, contentPadding = padding, modifier = Modifier.fillMaxSize())

            else ->
                BillsScreen(
                    vm = vm,
                    contentPadding = padding,
                    editorFor = editorBill,
                    editorOpen = editorOpen,
                    onEditorOpen = { bill ->
                        editorBill = bill
                        editorOpen = true
                    },
                    onEditorClose = {
                        editorOpen = false
                        editorBill = null
                    },
                    modifier = Modifier.fillMaxSize()
                )
        }
    }
}

@Composable
private fun PermissionGate(modifier: Modifier = Modifier, onRequestPermission: () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Let the app read your M-Pesa messages",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Every payment is read from the confirmation SMS on this phone. Nothing is uploaded anywhere — the app has no internet permission at all.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                Text("Allow SMS access")
            }
        }
    }
}
