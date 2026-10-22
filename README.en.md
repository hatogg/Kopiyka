<p align="center">
  <img src="docs/banner-en.png" alt="Kopiyka — expense and income planner" width="100%">
</p>

<p align="center">
  <a href="https://github.com/hatogg/Kopiyka/releases/latest"><img alt="Release" src="https://img.shields.io/github/v/release/hatogg/Kopiyka?style=flat-square&label=release&color=0F6E5E"></a>
  <a href="https://github.com/hatogg/Kopiyka/actions/workflows/build.yml"><img alt="Build" src="https://img.shields.io/github/actions/workflow/status/hatogg/Kopiyka/build.yml?branch=main&style=flat-square&label=build"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
  <img alt="Kotlin and Jetpack Compose" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/badge/license-MIT-FFC857?style=flat-square"></a>
</p>

<p align="center"><a href="README.md">Українська</a> &nbsp;|&nbsp; <b>English</b></p>

**Kopiyka** ("kopek", the Ukrainian penny) is an expense and income planner for Android. Log a purchase in three taps, keep money in several accounts and currencies, set a monthly budget and Kopiyka tells you every day how much you can still spend. Recurring payments record themselves, goals show how much to put aside, and charts show where the money really goes. No ads, no sign-up, no internet.

<p align="center">
  <a href="https://github.com/hatogg/Kopiyka/releases/latest"><img alt="Download APK" src="https://img.shields.io/badge/Download-APK-0F6E5E?style=for-the-badge&logo=android&logoColor=white"></a>
</p>

<p align="center">
  <img src="docs/screens/en-light-home.png" width="24%" alt="Overview: balance, accounts, daily allowance">
  <img src="docs/screens/en-light-add.png" width="24%" alt="New expense with the calculator keypad">
  <img src="docs/screens/en-light-budgets.png" width="24%" alt="Budgets by category">
  <img src="docs/screens/en-light-stats.png" width="24%" alt="Statistics: where the money goes">
</p>
<p align="center">
  <img src="docs/screens/en-light-history.png" width="24%" alt="Transaction history by day">
  <img src="docs/screens/en-light-goals.png" width="24%" alt="Savings goals">
  <img src="docs/screens/en-light-recurring.png" width="24%" alt="Recurring payments">
  <img src="docs/screens/en-dark-home.png" width="24%" alt="Overview in the dark theme">
</p>

<sub>Real screenshots: GitHub Actions runs the app in an Android 14 emulator and walks through the screens with demo data.</sub>

## Features

| | |
| --- | --- |
| ⚡ **Fast entry** | A big calculator keypad: type "25+8" and get 33. Your most-used categories come first. Date, account and note are one tap away. |
| 💳 **Accounts & currencies** | Cards, cash, a savings jar, a deposit — in hryvnias, dollars, euros, zlotys and more. Transfers between accounts, including currency exchange. The total balance is shown in your main currency. |
| 🎯 **Budgets** | An overall monthly limit plus limits for groceries, cafes, fun. "You can still spend $16 today", an end-of-month forecast and alerts at 80 % and 100 %. The month can start on payday. |
| 🔁 **Recurring payments** | Rent, utilities, subscriptions, salary: daily, weekly, monthly, yearly or every N. They record themselves, or remind you so you tap "Pay". |
| 🐷 **Savings goals** | A trip, a laptop, an emergency fund: progress, a deadline and how much to save each month. A goal can follow the balance of a savings account. |
| 📊 **Statistics** | A category donut, six months of income vs expenses, spending pace compared with last month, average per day, the biggest expense. |
| 🔎 **History** | Transactions by day with daily totals, search by note, category or amount, filters by type and account. Swipe left to delete — with Undo. |
| 💾 **Backup & export** | One-file backup (to Google Drive or Files) and restore. CSV export for Excel or Google Sheets. |
| 📱 **Widget & shortcuts** | A widget with the balance, today's spending and "− Expense" / "+ Income" buttons. Long-press the app icon to jump straight to a new expense. |
| 🎨 **Material You** | Wallpaper colours on Android 12+, light and dark themes, a "hide amounts" mode. Ukrainian or English, following the phone's language. |

## Install

1. On your phone, open the [latest release](https://github.com/hatogg/Kopiyka/releases/latest) and download `Kopiyka-<version>.apk`.
2. Open the downloaded file. Android will ask to allow installs from your browser or file manager — allow it.
3. **Install** → **Open**.

Requires Android 8.0 or newer. New versions install over the old one and keep your data.

**Widget:** long-press the home screen → **Widgets** → **Kopiyka** → "Kopiyka: balance".

## Getting started

1. **More → Accounts** — rename "Card" and "Cash", set what's on them now, add others (in other currencies too).
2. **Budgets → Budget** — set an overall monthly limit. If you're paid on, say, the 25th, pick 25 in **More → Month starts on**.
3. **More → Recurring payments** — add rent, subscriptions and salary so they record themselves.
4. Then just tap **Add** after each purchase — or the widget button.

## Privacy

The app **has no internet permission** — it sends nothing and shows no ads. You enter exchange rates yourself. All data lives in a single file in the app's storage on your phone; it can only leave it as a backup — one you saved yourself, or Android's system backup if you have it turned on.

## For developers

Kotlin, Jetpack Compose, Material 3, Glance (widget), WorkManager, kotlinx.serialization. No database and no third-party services.

```
app/src/main/java/ua/x44to/kopiyka/
├── MainActivity.kt        ← tabs Overview · History · Budgets · Stats · More, screens on top
├── data/
│   ├── Models.kt          ← Account, Category, Tx, Budget, Recurring, Goal, Settings
│   ├── Store.kt           ← JSON file storage, all changes, recurring payments, backup, CSV
│   ├── Calc.kt            ← periods, balances, budgets, chart series
│   ├── Money.kt           ← amounts in minor units, currencies, formatting, calculator
│   └── Strings.kt         ← dates and plurals in Ukrainian and English
├── ui/                    ← screens, calculator keypad, Canvas charts, theme
├── widget/                ← balance widget (Glance)
└── work/                  ← daily recurring-payment check and notifications
```

- **Build:** Android Studio or `./gradlew assembleRelease` (JDK 17, Android SDK 35).
- **GitHub Actions** ([build.yml](.github/workflows/build.yml)) builds the APK on every push to `main`, checks it has no internet permission and publishes it to the `v<versionName>` release.
- **Screenshots** ([screenshots.yml](.github/workflows/screenshots.yml)) come from an Android 14 emulator (Pixel 7): a test walks the screens in Ukrainian and English, light and dark, and commits the PNGs to `docs/screens`.
- **Signing:** the `signing/kopiyka.jks` key is in the repo so every build installs over the previous one. For Google Play, replace it with your own and pass the passwords as `KP_STORE_PASS` / `KP_KEY_PASS` secrets.

## License

[MIT](LICENSE) © x44to
