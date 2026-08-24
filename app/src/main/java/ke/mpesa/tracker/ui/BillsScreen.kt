package ke.mpesa.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ke.mpesa.tracker.data.Bill
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    editorFor: Bill?,
    editorOpen: Boolean,
    onEditorOpen: (Bill?) -> Unit,
    onEditorClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val date by vm.selectedDate.collectAsState()
    val rows by vm.billRows.collectAsState()

    val total = rows.sumOf { it.bill.amount }
    val paidTotal = rows.filter { it.paid }.sumOf { it.bill.amount }
    val doneCount = rows.count { it.paid }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            MonthPicker(
                date = date,
                onPrevious = { vm.shiftMonth(-1) },
                onNext = { vm.shiftMonth(1) }
            )
        }

        item {
            BillsSummary(
                doneCount = doneCount,
                totalCount = rows.size,
                paidTotal = paidTotal,
                remaining = total - paidTotal
            )
        }

        if (rows.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No bills yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Add rent, power, water, internet — anything you pay every month. Tick each one off as you pay it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { onEditorOpen(null) }) { Text("Add your first bill") }
                    }
                }
            }
        } else {
            items(rows, key = { it.bill.id }) { row ->
                BillCard(
                    bill = row.bill,
                    paid = row.paid,
                    onToggle = { vm.setBillPaid(row.bill, it) },
                    onEdit = { onEditorOpen(row.bill) }
                )
            }
        }
    }

    if (editorOpen) {
        BillEditorDialog(
            bill = editorFor,
            onDismiss = onEditorClose,
            onSave = { name, amount, day ->
                vm.saveBill(editorFor?.id, name, amount, day)
                onEditorClose()
            },
            onDelete = editorFor?.let { bill ->
                {
                    vm.deleteBill(bill)
                    onEditorClose()
                }
            }
        )
    }
}

@Composable
private fun MonthPicker(date: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = formatMonth(date),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun BillsSummary(doneCount: Int, totalCount: Int, paidTotal: Double, remaining: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Still to pay",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                money(remaining),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { if (totalCount == 0) 0f else doneCount.toFloat() / totalCount },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "$doneCount of $totalCount done · ${money(paidTotal)} paid",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillCard(bill: Bill, paid: Boolean, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = paid, onCheckedChange = onToggle)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = bill.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (paid) TextDecoration.LineThrough else null,
                    color = if (paid) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (paid) "Paid" else "Due ${ordinal(bill.dueDay)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = moneyShort(bill.amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (paid) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillEditorDialog(
    bill: Bill?,
    onDismiss: () -> Unit,
    onSave: (name: String, amount: Double, dueDay: Int) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(bill?.name ?: "") }
    var amount by remember { mutableStateOf(bill?.amount?.let { "%.0f".format(it) } ?: "") }
    var dueDay by remember { mutableStateOf(bill?.dueDay?.toString() ?: "1") }

    val amountValue = amount.replace(",", "").toDoubleOrNull()
    val dayValue = dueDay.toIntOrNull()
    val valid = name.isNotBlank() && amountValue != null && amountValue > 0 && dayValue in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bill == null) "Add a bill" else "Edit bill") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Rent, KPLC, Zuku…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount") },
                    prefix = { Text("Ksh ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = dueDay,
                    onValueChange = { dueDay = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Day of month it's due") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = dueDay.isNotEmpty() && dayValue !in 1..31,
                    supportingText = { Text("A number from 1 to 31") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete this bill")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(name, amountValue ?: 0.0, dayValue ?: 1) }
            ) { Text("Save bill") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
