package ke.mpesa.tracker.parser

import ke.mpesa.tracker.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Safaricom changes message wording from time to time. If a payment stops showing up in the
 * app, paste the exact SMS here as a new test, run the tests, then adjust MpesaParser until
 * it passes. Run with:  ./gradlew test
 */
class MpesaParserTest {

    private fun parse(body: String) = MpesaParser.parse(body, 0L)

    @Test fun `pay bill with account`() {
        val tx = parse(
            "TFF9XYZ12A Confirmed. Ksh1,000.00 sent to KPLC PREPAID for account 12345678901 " +
                "on 15/6/25 at 10:23 AM New M-PESA balance is Ksh5,000.00. Transaction cost, Ksh0.00."
        )!!
        assertEquals(TxType.PAYBILL, tx.type)
        assertEquals(1000.0, tx.amount, 0.001)
        assertEquals("KPLC PREPAID", tx.payee)
        assertEquals("12345678901", tx.account)
        assertTrue(tx.isExpense)
    }

    @Test fun `buy goods till`() {
        val tx = parse(
            "TFF2ABC33B Confirmed. Ksh500.00 paid to NAIVAS SUPERMARKET. on 15/6/25 at 1:10 PM." +
                "New M-PESA balance is Ksh2,000.00. Transaction cost, Ksh0.00."
        )!!
        assertEquals(TxType.TILL, tx.type)
        assertEquals("NAIVAS SUPERMARKET", tx.payee)
    }

    @Test fun `send money strips the phone number and keeps the fee`() {
        val tx = parse(
            "TFF3DEF44C Confirmed. Ksh1,500.00 sent to JOHN DOE 0722123456 on 15/6/25 at 3:00 PM. " +
                "New M-PESA balance is Ksh500.00. Transaction cost, Ksh23.00."
        )!!
        assertEquals("JOHN DOE", tx.payee)
        assertEquals(23.0, tx.cost, 0.001)
        assertEquals(1523.0, tx.total, 0.001)
    }

    @Test fun `withdrawal`() {
        val tx = parse(
            "TFF4GHI55D Confirmed.on 15/6/25 at 4:00 PM Withdraw Ksh1,000.00 from 123456 - " +
                "Kimathi Street Agent New M-PESA balance is Ksh200.00. Transaction cost, Ksh29.00"
        )!!
        assertEquals(TxType.WITHDRAW, tx.type)
        assertEquals(1000.0, tx.amount, 0.001)
    }

    @Test fun `airtime for another number`() {
        val tx = parse(
            "TFF6MNO77F confirmed.You bought Ksh50.00 of airtime for 0733987654 on 15/6/25 " +
                "at 5:30 PM.New M-PESA balance is Ksh50.00."
        )!!
        assertEquals(TxType.AIRTIME, tx.type)
        assertEquals("Airtime for 0733987654", tx.payee)
    }

    @Test fun `money received is not an expense`() {
        val tx = parse(
            "TFF7PQR88G Confirmed.You have received Ksh2,000.00 from JANE WANJIKU 254712345678 " +
                "on 16/6/25 at 6:00 PM New M-PESA balance is Ksh2,050.00."
        )!!
        assertEquals(TxType.RECEIVED, tx.type)
        assertEquals("JANE WANJIKU", tx.payee)
        assertTrue(!tx.isExpense)
    }

    @Test fun `phone-shaped account numbers survive`() {
        val tx = parse(
            "SGH1VWX10I Confirmed. KES 2,400.00 sent to SAFARICOM DATA BUNDLES for account " +
                "0712000111 on 1/7/26 at 11:59 PM New M-PESA balance is KES 300.00. Transaction cost, KES 0.00."
        )!!
        assertEquals("0712000111", tx.account)
    }

    @Test fun `balance and promo messages are ignored`() {
        assertNull(parse("Your M-PESA balance was Ksh1,234.00 on 15/6/25 at 10:00 AM"))
        assertNull(parse("Dear customer, buy Okoa Jahazi today and stay connected!"))
    }

    // ---------------------------------------------------------------
    // Real messages captured August 2026. These are the current live
    // formats — if one of these ever fails, Safaricom changed something.
    // ---------------------------------------------------------------

    @Test fun `card payment lifts the merchant out of the account field`() {
        val tx = parse(
            "UHOL3472YY Confirmed. Ksh1,555.38 sent to M-PESA CARD for account ANTHROPIC                " +
                "+14152360599 US on 24/8/26 at 2:40 AM New M-PESA balance is Ksh419.46. Transaction cost, " +
                "Ksh0.00.Amount you can transact within the day is 496,889.24. Download My OneApp on https://saf.cx/kWQpy"
        )!!
        assertEquals(TxType.CARD, tx.type)
        assertEquals("ANTHROPIC", tx.payee)
        assertEquals(1555.38, tx.amount, 0.001)
    }

    @Test fun `card merchant whose name ends in a country word`() {
        val tx = parse(
            "UHNL3437J8 Confirmed. Ksh1,343.53 sent to M-PESA CARD for account TELLO US                 " +
                "8663770294   US on 23/8/26 at 12:09 AM New M-PESA balance is Ksh6,587.98. Transaction cost, Ksh0.00."
        )!!
        assertEquals("TELLO US", tx.payee)
    }

    @Test fun `masked sender phone number is stripped`() {
        val tx = parse(
            "UHMEB4177Z Confirmed.You have received Ksh450.00 from Samuel  Okari 0705***905 on 22/8/26 " +
                "at 5:18 AM  New M-PESA balance is Ksh11,336.58. Download My OneApp on https://saf.cx/lPKcC"
        )!!
        assertEquals("Samuel Okari", tx.payee)
        assertTrue(!tx.isExpense)
    }

    @Test fun `till name containing a hyphen and digit survives`() {
        val tx = parse(
            "UHML342UTG Confirmed. Ksh800.00 paid to ECCOY TRUST ENTERPRISES-2. on 22/8/26 at 9:39 PM." +
                "New M-PESA balance is Ksh8,002.13. Transaction cost, Ksh4.40. Amount you can transact " +
                "within the day is 496,687.95. Download My OneApp on https://saf.cx/lPKcC"
        )!!
        assertEquals(TxType.TILL, tx.type)
        assertEquals("ECCOY TRUST ENTERPRISES-2", tx.payee)
        assertEquals(4.40, tx.cost, 0.001)
    }

    @Test fun `paybill whose account is a full phone number`() {
        val tx = parse(
            "UHML33Z3V8 confirmed. Ksh450.00 sent to AIRTEL MONEY  for account 254788359534 on 22/8/26 " +
                "at 5:21 AM New M-PESA balance is Ksh10,879.58. Transaction cost, Ksh7.00."
        )!!
        assertEquals("AIRTEL MONEY", tx.payee)
        assertEquals("254788359534", tx.account)
    }

    @Test fun `paybill with an alphanumeric account`() {
        val tx = parse(
            "UHAL32P2FG Confirmed. Ksh3,780.00 sent to SAVO CONNECT LTD for account A75397 on 10/8/26 " +
                "at 10:39 PM New M-PESA balance is Ksh7,370.60. Transaction cost, Ksh34.00."
        )!!
        assertEquals("SAVO CONNECT LTD", tx.payee)
        assertEquals("A75397", tx.account)
        assertEquals(3814.0, tx.total, 0.001)
    }

    @Test fun `mixed case till names are kept as written`() {
        val tx = parse(
            "UHNL346FDZ Confirmed. Ksh1,652.00 paid to Naivas Westside. on 23/8/26 at 7:46 PM." +
                "New M-PESA balance is Ksh1,640.98. Transaction cost, Ksh0.00. Amount you can transact " +
                "within the day is 493,709.47. Download My OneApp on https://saf.cx/lPKcC"
        )!!
        assertEquals("Naivas Westside", tx.payee)
    }

    @Test fun `promo tail is not stored in the raw message`() {
        val tx = parse(
            "UHNL345JNZ Confirmed. Ksh1,195.00 paid to CLEANSHELF SUPERMARKET NAKURU. on 23/8/26 at 5:26 PM." +
                "New M-PESA balance is Ksh5,392.98. Transaction cost, Ksh0.00. Amount you can transact " +
                "within the day is 497,461.47. Download My OneApp on https://saf.cx/lPKcC"
        )!!
        assertTrue(!tx.raw.contains("OneApp"))
        assertTrue(!tx.raw.contains("Amount you can transact"))
    }
}
