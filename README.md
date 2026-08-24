# Pesa Tracker

A private Android app that reads M-Pesa confirmation SMS on your phone, turns them into a daily
spending log, and keeps a checklist of your monthly bills.

Everything stays on the device. The app has **no internet permission**, so nothing can leave the
phone even by accident.

---

## Getting the APK onto your phone

Pick whichever path matches what you already have installed.

### A. Android Studio (best if you'll keep tweaking it)

You need [Android Studio](https://developer.android.com/studio); it bundles the JDK and SDK.

1. **File → Open** → pick the `MpesaTracker` folder.
2. Wait for the first Gradle sync — it downloads Gradle, SDK pieces and libraries.
   Expect 5–15 minutes and a few GB on a first run.
3. Plug in the phone with USB debugging on, press **Run** (▶). It installs directly.

### B. Command line (if you already have the Android SDK)

```
chmod +x gradlew
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the phone and open it.

If Gradle can't find your SDK, create a `local.properties` file in this folder containing:

```
sdk.dir=/path/to/Android/Sdk
```

### C. GitHub Actions (no local setup at all)

Useful if you don't want to install several GB of Android tooling. Free: unlimited on public
repos, 2,000 minutes/month on private ones. This build takes ~5 minutes, so you won't run out.

1. Create a GitHub repo (public or private — there are no secrets in this code).
2. Push this folder to it:
   ```
   git init && git add . && git commit -m "Pesa Tracker"
   git branch -M main
   git remote add origin git@github.com:YOUR-NAME/pesa-tracker.git
   git push -u origin main
   ```
3. Open the **Actions** tab. *Build APK* runs automatically.
4. When it's done, go to the **Releases** section of the repo (right-hand sidebar). The newest
   release has `pesa-tracker-buildN.apk` attached.

**Do step 4 from your phone's browser.** The release asset is a direct `.apk` link — tap it and
Android downloads it straight to the device, ready to install. That's the whole point of
publishing a Release rather than a plain artifact: artifacts download as a **zip** and need you to
be logged in, which is painful on mobile. On a public repo the release link needs no login at all.

The workflow runs the parser tests before building, so a broken parser fails the build instead of
shipping you a bad APK. Each run bumps the build number, so you always know which APK is which.

### Other build services

Codemagic, Bitrise and Appcircle all do the same job with a nicer UI. One catch worth knowing:
Codemagic's free tier is 500 minutes on **macOS machines only** — Linux and Windows builds have no
free minutes. Their macOS images do include the Android SDK, so an Android-only build does work on
the free tier, but it's an odd fit. Bitrise's free Hobby tier is credit-based rather than
minute-based. For one Android app, GitHub Actions is simpler and costs nothing.

### Installing it

Copy the APK to the phone and tap it. Android will ask you to allow installing from unknown
sources for whichever app you used to open it (Files, Chrome, WhatsApp) — that's expected for a
sideloaded app.

On first launch it asks for SMS access, then scans your inbox and pulls in every M-Pesa message it
can read. **If you deny the permission**, grant it later at *Settings → Apps → Pesa Tracker →
Permissions → SMS*, then reopen the app and hit refresh.

The APK is signed with the standard debug key. That's fine for personal use, but if you rebuild on
a different machine later, the signature won't match and you'll need to uninstall before
reinstalling — which wipes the database, so don't switch machines casually.

---

## How it works

| Piece | File |
|---|---|
| SMS text → transaction | `parser/MpesaParser.kt` |
| One-off inbox scan | `sms/SmsImporter.kt` |
| Live message capture | `sms/SmsReceiver.kt` |
| Storage (Room/SQLite) | `data/` |
| Screens | `ui/` |

**Duplicates are impossible.** The M-Pesa confirmation code (e.g. `TFF9XYZ12A`) is the database
primary key, so rescanning your inbox never double-counts anything.

**Message formats handled:** pay bill (with account number), buy goods / till, virtual card
payments, send money, withdrawal, airtime, and money received. Balance checks and promos are
ignored. Verified against real August 2026 messages — see the test file.

**Card payments name the real merchant.** M-Pesa sends these as `sent to M-PESA CARD for account
ANTHROPIC   +1415... US`, which would file every card payment under one meaningless name. The app
lifts the merchant out of the account field, so it shows as `ANTHROPIC`, `TELLO US` and so on. The
full account text is kept and shown under the payee.

**Masked sender numbers are handled.** Incoming payments now arrive as `Samuel Okari 0705***905`;
the app strips the masked number and shows `Samuel Okari`.

**Safaricom's promo tail is dropped.** The `Amount you can transact within the day is...` and
`Download My OneApp on...` lines are stripped before storing, so the raw message you see when
editing a transaction is just the transaction.

**Transaction fees are counted.** A Ksh 1,500 send with a Ksh 23 fee shows as Ksh 1,500 in the list
with the fee below it, and Ksh 1,523 in your daily total — that's what actually left your account.

---

## Using it

**Spending tab.** Arrows move between days. The green card shows what you spent that day and the
running total for the month. Tap any transaction to:

- rename the payee (e.g. `KPLC PREPAID` → `Electricity`)
- tick *Rename every payment to this payee* to apply that name to all past and future payments
  from the same till or paybill
- tick *Leave out of my totals* for things that aren't really spending

**Bills tab.** Add each recurring bill with its amount and the day of the month it falls due.
Tick it off when you pay it. Ticks are stored per month, so every new month starts fresh, and you
can page back with the arrows to see what you paid in June.

---

## When Safaricom changes their wording

Sooner or later a message format will change and a payment will stop appearing. There's a test file
at `app/src/test/java/ke/mpesa/tracker/parser/MpesaParserTest.kt`. Paste the exact SMS in as a new
test, run `./gradlew test`, and adjust the matching regex in `MpesaParser.kt` until it passes.

---

## A note on the SMS permission

Google Play won't publish apps that use `READ_SMS` without a very narrow justification, so this is
built as a personal sideloaded app rather than something for the Play Store. That's fine for your
own phone — it just means you install the APK yourself rather than through the store.
