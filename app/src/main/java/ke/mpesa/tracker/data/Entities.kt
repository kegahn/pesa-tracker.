package ke.mpesa.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class TxType {
    PAYBILL, TILL, CARD, SEND, WITHDRAW, AIRTIME, RECEIVED, OTHER;

    val label: String
        get() = when (this) {
            PAYBILL -> "Pay bill"
            TILL -> "Buy goods"
            CARD -> "Card payment"
            SEND -> "Sent"
            WITHDRAW -> "Withdrawal"
            AIRTIME -> "Airtime"
            RECEIVED -> "Received"
            OTHER -> "Other"
        }
}

/**
 * One M-Pesa transaction. The M-Pesa confirmation code is the primary key, so
 * re-importing the inbox can never create duplicates.
 */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val code: String,
    val amount: Double,
    val cost: Double,
    /** Editable — what you call this payee. */
    val payee: String,
    /** What M-Pesa called it. Kept so you can always see the original. */
    val originalPayee: String,
    val account: String?,
    val type: TxType,
    val isExpense: Boolean,
    val timestamp: Long,
    val raw: String,
    /** Left out of daily and monthly totals. */
    val excluded: Boolean = false
) {
    val total: Double get() = amount + cost
}

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    /** Day of the month it falls due, 1–31. */
    val dueDay: Int,
    val archived: Boolean = false
)

/** A bill marked done for one month. Deleting the row un-marks it. */
@Entity(
    tableName = "bill_payments",
    indices = [Index(value = ["billId", "monthKey"], unique = true)]
)
data class BillPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    /** "2026-08" */
    val monthKey: String,
    val paidAt: Long
)

class Converters {
    @TypeConverter fun toTxType(value: String): TxType =
        runCatching { TxType.valueOf(value) }.getOrDefault(TxType.OTHER)

    @TypeConverter fun fromTxType(type: TxType): String = type.name
}
