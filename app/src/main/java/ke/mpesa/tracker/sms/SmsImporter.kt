package ke.mpesa.tracker.sms

import android.content.Context
import android.provider.Telephony
import ke.mpesa.tracker.data.AppDao
import ke.mpesa.tracker.parser.MpesaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsImporter {

    private val SENDERS = listOf("MPESA", "M-PESA", "SAFARICOM")

    fun isMpesaSender(address: String?): Boolean {
        val a = address?.uppercase()?.replace(" ", "") ?: return false
        return SENDERS.any { a.contains(it.replace("-", "")) || a.contains(it) }
    }

    /**
     * Reads the whole SMS inbox, keeps anything from M-Pesa that the parser understands,
     * and writes it to the database. Safe to run repeatedly — the M-Pesa code is the
     * primary key, so existing rows are skipped rather than duplicated.
     *
     * @return number of newly added transactions
     */
    suspend fun importInbox(context: Context, dao: AppDao): Int = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        var added = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val addressCol = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                if (!isMpesaSender(cursor.getString(addressCol))) continue
                val body = cursor.getString(bodyCol) ?: continue
                val tx = MpesaParser.parse(body, cursor.getLong(dateCol)) ?: continue
                if (dao.insert(tx) != -1L) added++
            }
        }
        added
    }
}
