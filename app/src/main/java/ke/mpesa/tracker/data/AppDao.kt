package ke.mpesa.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // ---- transactions ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: Transaction): Long

    @Query("SELECT * FROM transactions WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    fun transactionsBetween(start: Long, end: Long): Flow<List<Transaction>>

    @Query(
        "SELECT COALESCE(SUM(amount + cost), 0) FROM transactions " +
            "WHERE isExpense = 1 AND excluded = 0 AND timestamp >= :start AND timestamp < :end"
    )
    fun spendBetween(start: Long, end: Long): Flow<Double>

    @Query("UPDATE transactions SET payee = :payee WHERE code = :code")
    suspend fun renamePayee(code: String, payee: String)

    /** Applies a rename to every past transaction from the same original payee. */
    @Query("UPDATE transactions SET payee = :payee WHERE originalPayee = :originalPayee")
    suspend fun renameAllFrom(originalPayee: String, payee: String)

    @Query("UPDATE transactions SET excluded = :excluded WHERE code = :code")
    suspend fun setExcluded(code: String, excluded: Boolean)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun transactionCount(): Int

    // ---- bills ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: Bill): Long

    @Update
    suspend fun updateBill(bill: Bill)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteBill(id: Long)

    @Query("DELETE FROM bill_payments WHERE billId = :id")
    suspend fun deletePaymentsFor(id: Long)

    @Query("SELECT * FROM bills WHERE archived = 0 ORDER BY dueDay ASC, name ASC")
    fun bills(): Flow<List<Bill>>

    @Query("SELECT * FROM bill_payments WHERE monthKey = :monthKey")
    fun paymentsForMonth(monthKey: String): Flow<List<BillPayment>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markPaid(payment: BillPayment): Long

    @Query("DELETE FROM bill_payments WHERE billId = :billId AND monthKey = :monthKey")
    suspend fun markUnpaid(billId: Long, monthKey: String)
}
