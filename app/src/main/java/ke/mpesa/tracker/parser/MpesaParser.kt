package ke.mpesa.tracker.parser

import ke.mpesa.tracker.data.Transaction
import ke.mpesa.tracker.data.TxType
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Turns a raw M-Pesa SMS body into a [Transaction].
 *
 * Safaricom has changed wording several times over the years, so every rule below is
 * written against a real message shape. Anything that does not match a rule (balance
 * checks, promos, Fuliza notices) is dropped by returning null.
 */
object MpesaParser {

    private val ic = setOf(RegexOption.IGNORE_CASE)

    /** Money as it appears in the SMS: Ksh1,234.00 or KES 1,234.00 */
    private const val AMT = """(?:Ksh|KES)\.?\s?([\d,]+(?:\.\d{1,2})?)"""

    private val codeRe = Regex("""^\s*([A-Z0-9]{8,12})\s+confirmed""", ic)
    private val dateRe = Regex("""on\s+(\d{1,2})/(\d{1,2})/(\d{2,4})\s+at\s+(\d{1,2}):(\d{2})\s*([AP])\.?M""", ic)
    private val costRe = Regex("""transaction\s+cost[,.:]?\s*(?:Ksh|KES)\.?\s?([\d,]+(?:\.\d{1,2})?)""", ic)

    // Money in
    private val receivedRe = Regex("""(?:you have )?received\s+$AMT\s+from\s+(.+?)\s+on\s+\d{1,2}/""", ic)
    private val reversalRe = Regex("""reversal.*?$AMT""", ic + RegexOption.DOT_MATCHES_ALL)

    // Money out
    private val paybillSentRe = Regex("""$AMT\s+sent to\s+(.+?)\s+for account\s+(.+?)\s+on\s+\d{1,2}/""", ic)
    private val paybillPaidRe = Regex("""$AMT\s+paid to\s+(.+?)\s+for account\s+(.+?)\s+on\s+\d{1,2}/""", ic)
    private val tillRe = Regex("""$AMT\s+paid to\s+(.+?)\.?\s+on\s+\d{1,2}/""", ic)
    private val sendRe = Regex("""$AMT\s+sent to\s+(.+?)\s+on\s+\d{1,2}/""", ic)
    private val withdrawRe = Regex("""withdraw\s+$AMT\s+from\s+(.+?)\s+New\s+M-?PESA""", ic)
    private val airtimeRe = Regex("""bought\s+$AMT\s+of\s+airtime(?:\s+for\s+((?:\+?254|0)\d{8,9}))?""", ic)

    /**
     * A phone number at the end of a name. Covers plain (0722123456), international-prefixed
     * (254712345678) and the masked form M-Pesa now uses for senders (0705***905).
     */
    private val trailingPhoneRe = Regex("""\s+\+?(?:254|0)?[\d*]{6,}\s*$""")
    private val leadingCodeRe = Regex("""^\d{5,9}\s*-\s*""")

    /** Marketing text Safaricom appends. Carries no transaction data. */
    private val promoTailRe = Regex(
        """\s*(?:Amount you can transact within the day is [\d,.]+\.?|Download My OneApp on \S+)\s*""",
        ic
    )

    /** Virtual card payments arrive as "sent to M-PESA CARD", which tells you nothing useful. */
    private val cardPayeeRe = Regex("""^M-?PESA\s+CARD$""", ic)

    /**
     * @param body full SMS text (multipart messages must already be joined)
     * @param receivedAt fallback timestamp if the message has no readable date
     */
    fun parse(body: String, receivedAt: Long): Transaction? {
        val text = body.replace('\u00A0', ' ')
            .replace(promoTailRe, " ")
            .trim()
        val code = codeRe.find(text)?.groupValues?.get(1) ?: return null

        val cost = costRe.find(text)?.groupValues?.get(1)?.toMoney() ?: 0.0
        val time = extractTime(text) ?: receivedAt

        fun tx(amount: Double, payee: String, type: TxType, expense: Boolean, account: String? = null) =
            Transaction(
                code = code,
                amount = amount,
                cost = if (expense) cost else 0.0,
                payee = payee,
                originalPayee = payee,
                account = account,
                type = type,
                isExpense = expense,
                timestamp = time,
                raw = text
            )

        receivedRe.find(text)?.let { m ->
            return tx(m.money(1), m.name(2), TxType.RECEIVED, expense = false)
        }

        paybillSentRe.find(text)?.let { m ->
            val payee = m.name(2)
            val account = m.account(3)
            // "sent to M-PESA CARD for account ANTHROPIC   +14152360599 US" — the merchant is
            // buried in the account field, so lift it out and use it as the payee instead.
            if (cardPayeeRe.matches(payee) && account != null) {
                // Note: merchant is lifted from the *raw* group — account() collapses the
                // run of spaces that separates the merchant from the number.
                return tx(m.money(1), cardMerchant(m.groupValues[3]), TxType.CARD, expense = true, account = account)
            }
            return tx(m.money(1), payee, TxType.PAYBILL, expense = true, account = account)
        }

        paybillPaidRe.find(text)?.let { m ->
            return tx(m.money(1), m.name(2), TxType.PAYBILL, expense = true, account = m.account(3))
        }

        tillRe.find(text)?.let { m ->
            return tx(m.money(1), m.name(2), TxType.TILL, expense = true)
        }

        sendRe.find(text)?.let { m ->
            return tx(m.money(1), m.name(2), TxType.SEND, expense = true)
        }

        withdrawRe.find(text)?.let { m ->
            return tx(m.money(1), "Cash withdrawal — ${m.name(2)}", TxType.WITHDRAW, expense = true)
        }

        airtimeRe.find(text)?.let { m ->
            val forNumber = m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
            val label = if (forNumber != null) "Airtime for $forNumber" else "Airtime"
            return tx(m.money(1), label, TxType.AIRTIME, expense = true)
        }

        reversalRe.find(text)?.let { m ->
            return tx(m.money(1), "Reversal", TxType.OTHER, expense = false)
        }

        return null
    }

    private fun extractTime(text: String): Long? {
        val m = dateRe.find(text) ?: return null
        return runCatching {
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val rawYear = m.groupValues[3].toInt()
            val year = if (rawYear < 100) 2000 + rawYear else rawYear
            val rawHour = m.groupValues[4].toInt() % 12
            val minute = m.groupValues[5].toInt()
            val hour = if (m.groupValues[6].uppercase() == "P") rawHour + 12 else rawHour
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun MatchResult.money(group: Int) = groupValues[group].toMoney()

    private fun MatchResult.name(group: Int) = groupValues[group].cleanName()

    /**
     * Pulls the merchant out of a card-payment account field. The merchant name is separated
     * from the phone number and country code by a run of spaces, so take the first chunk and
     * fall back to trimming a trailing number + country code if it was single-spaced.
     */
    internal fun cardMerchant(rawAccount: String): String {
        val chunk = rawAccount.split(Regex("""\s{2,}"""))
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: rawAccount.trim()
        val stripped = chunk
            .replace(Regex("""\s+\+?[\d*]{6,}(?:\s+[A-Za-z]{2})?\s*$"""), "")
            .trim()
        return stripped.ifBlank { chunk }.ifBlank { "Card payment" }
    }

    /**
     * Account numbers are often phone numbers (data bundles, airtime top-ups), so they get a
     * lighter clean than payee names — trimming only, never phone-stripping.
     */
    private fun MatchResult.account(group: Int) = groupValues[group]
        .replace('\n', ' ')
        .trim()
        .trim('.', ',', ' ')
        .replace(Regex("""\s{2,}"""), " ")
        .ifBlank { null }

    private fun String.toMoney(): Double = replace(",", "").trim().toDoubleOrNull() ?: 0.0

    /** Strips trailing phone numbers, agent codes, stray punctuation and repeated spaces. */
    private fun String.cleanName(): String = this
        .replace('\n', ' ')
        .replace(trailingPhoneRe, "")
        .replace(leadingCodeRe, "")
        .trim()
        .trim('.', ',', '-', ' ')
        .replace(Regex("""\s{2,}"""), " ")
        .ifBlank { "Unknown" }
}
