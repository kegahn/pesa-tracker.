package ke.mpesa.tracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ke.mpesa.tracker.data.AppDatabase
import ke.mpesa.tracker.parser.MpesaParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Catches M-Pesa messages as they arrive so spending updates without opening the app. */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        // Long M-Pesa messages arrive split across several parts; join them back together.
        val address = parts.first().originatingAddress
        if (!SmsImporter.isMpesaSender(address)) return
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val receivedAt = parts.first().timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

        val tx = MpesaParser.parse(body, receivedAt) ?: return

        val pending = goAsync()
        scope.launch {
            try {
                AppDatabase.get(context).dao().insert(tx)
            } finally {
                pending.finish()
            }
        }
    }
}
