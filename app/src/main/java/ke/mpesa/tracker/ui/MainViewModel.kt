package ke.mpesa.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ke.mpesa.tracker.data.AppDatabase
import ke.mpesa.tracker.data.Bill
import ke.mpesa.tracker.data.BillPayment
import ke.mpesa.tracker.data.Transaction
import ke.mpesa.tracker.sms.SmsImporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class BillRow(val bill: Bill, val paid: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).dao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    val transactions: StateFlow<List<Transaction>> = _selectedDate
        .flatMapLatest { date -> dao.transactionsBetween(date.startMillis(), date.plusDays(1).startMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dayTotal: StateFlow<Double> = _selectedDate
        .flatMapLatest { date -> dao.spendBetween(date.startMillis(), date.plusDays(1).startMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val monthTotal: StateFlow<Double> = _selectedDate
        .flatMapLatest { date ->
            val month = YearMonth.from(date)
            dao.spendBetween(month.atDay(1).startMillis(), month.plusMonths(1).atDay(1).startMillis())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val billRows: StateFlow<List<BillRow>> = _selectedDate
        .flatMapLatest { date ->
            val key = date.monthKey()
            combine(dao.bills(), dao.paymentsForMonth(key)) { bills, payments ->
                val paidIds = payments.map { it.billId }.toSet()
                bills.map { BillRow(it, it.id in paidIds) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- actions ----

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun shiftDay(days: Long) {
        _selectedDate.value = _selectedDate.value.plusDays(days)
    }

    fun shiftMonth(months: Long) {
        _selectedDate.value = _selectedDate.value.plusMonths(months)
    }

    fun importInbox() {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            val added = runCatching { SmsImporter.importInbox(getApplication(), dao) }
            _importing.value = false
            _status.value = added.fold(
                onSuccess = { count ->
                    when (count) {
                        0 -> "No new M-Pesa messages found"
                        1 -> "Added 1 transaction"
                        else -> "Added $count transactions"
                    }
                },
                onFailure = { "Couldn't read your messages. Check the SMS permission in Settings." }
            )
        }
    }

    fun rename(tx: Transaction, newName: String, applyToAll: Boolean) {
        val name = newName.trim().ifBlank { return }
        viewModelScope.launch {
            if (applyToAll) dao.renameAllFrom(tx.originalPayee, name) else dao.renamePayee(tx.code, name)
        }
    }

    fun setExcluded(tx: Transaction, excluded: Boolean) {
        viewModelScope.launch { dao.setExcluded(tx.code, excluded) }
    }

    fun saveBill(id: Long?, name: String, amount: Double, dueDay: Int) {
        viewModelScope.launch {
            val bill = Bill(
                id = id ?: 0L,
                name = name.trim(),
                amount = amount,
                dueDay = dueDay.coerceIn(1, 31)
            )
            if (id == null) dao.insertBill(bill) else dao.updateBill(bill)
        }
    }

    fun deleteBill(bill: Bill) {
        viewModelScope.launch {
            dao.deletePaymentsFor(bill.id)
            dao.deleteBill(bill.id)
            _status.value = "Deleted ${bill.name}"
        }
    }

    fun setBillPaid(bill: Bill, paid: Boolean) {
        val key = _selectedDate.value.monthKey()
        viewModelScope.launch {
            if (paid) {
                dao.markPaid(BillPayment(billId = bill.id, monthKey = key, paidAt = System.currentTimeMillis()))
            } else {
                dao.markUnpaid(bill.id, key)
            }
        }
    }

    fun clearStatus() {
        _status.value = null
    }

    /** Runs a silent import on first launch so the app opens with history already in it. */
    fun importIfEmpty() {
        viewModelScope.launch {
            if (dao.transactionCount() == 0) importInbox()
        }
    }
}

private fun LocalDate.startMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun LocalDate.monthKey(): String = "%04d-%02d".format(year, monthValue)
