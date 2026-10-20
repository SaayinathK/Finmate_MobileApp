# SWallet

Native Android app for daily accounts and work checklists. The app remains compatible with the existing application ID and upgrades existing Room version 5, 6 or 7 databases to version 8 without deleting their records.

## Male and female animation characters

In **Profile > Preferences > Animation gender**, choose **Male** or **Female**. Male is the default for existing and new installations. The selection is saved locally, updates the Profile preview, and applies to startup, in-app guides and achievement celebrations. Returning screens refresh their character, and the two guide atlases are cached separately.

The female character is based on the supplied face and wears a SWallet-blue blouse with **white trousers**. Existing male artwork remains available. Home's **Important today** card retains its static checklist icon. Both character choices respect reduced motion. Final artwork paths, imagegen prompts and previews are documented in `artifacts/FEMALE-CHARACTER.md`.

The **Today's tasks home-screen widget** also uses the selected character. It waves with no tasks, plans while tasks remain and gives a thumbs-up when today's tasks are complete. Changing the Profile selection refreshes placed widgets. Task/subtask controls remain available. Widget animation uses a native frame flipper with pauses and a still pose for reduced motion, evaluated at widget refresh. Implementation and launcher limitations: `artifacts/WIDGET-CHARACTER.md`.

## Launch page and launcher branding

The launch page uses the original full SWallet logo on a deep blue gradient with a glowing logo halo, headline, running panel and Money / Tasks / Goals labels. Its startup character wears a blue shirt matched to the logo, with the photo's white sarong, orange border, sandals and watch retained. Portrait, landscape and reduced motion are supported. Android 12+ starts with a matching system splash.

The launcher now uses a separate wallet-only symbol with padding for adaptive icon masks and API 33 themed icons. Small in-app header icons use the symbol too. The original full logo remains unchanged. Current artwork paths, final image edit prompts, sizing evidence and Android references are in `artifacts/LAUNCH-BRANDING.md`.

## Profile, subtasks and startup revision

- **Profile** in the bottom navigation now combines the profile editor, activity statistics, currency, theme, widget setup, budget, password, logout and deletion settings. Reports, Loans, Goals & achievements, Reminders and Excel are in its Tools section. The six pictured shortcut cards have been removed from Home. Existing Settings entry points open the same merged screen.
- Subtasks are compact list rows in the parent task details: the checkbox completes or undoes a subtask, and tapping its title area opens its full editor. Each row shows its own deadline and reminder details.
- In the **Today's tasks** home-screen widget, tap a parent task's title or chevron to expand/collapse its subtasks inline. Tap the separate checkbox to complete the parent or a due subtask. Expansion is saved separately for each widget. Expanded groups also show active future subtasks and their deadlines/reminders; those future rows do not increase today's due count and cannot be completed from the widget before they are due. If a subtask is due before its parent, the parent is shown as a group so its subtasks remain reachable.
- The startup screen has a running cartoon based on the latest pine-forest photo, wearing the SWallet-blue shirt, orange-bordered white sarong, brown sandals and wristwatch. It runs across the screen for 2.4 seconds while reminders prepare, then opens Home if signed in or Sign in otherwise. Reduced motion uses a still pose and skips the animation delay. Background navigation is deferred until the startup activity resumes. Portrait and landscape layouts are supported.

Running preview: `artifacts/SWallet-startup-preview.html`. Original atlas and complete generation prompt: `artifacts/RUNNER.md`. The preview demonstrates the motion and is not a device screenshot. Current verification is recorded at the top of `artifacts/VERIFICATION.md`.

## Cartoon character animation

A human cartoon guide based on the supplied face appears beside sign-in/sign-up branding, in the Home balance and checklist summary, task summaries and empty states, goal prompts, and achievement celebrations. It waves, checks a list, gives a thumbs-up and raises a trophy. The character plays a short gesture and settles into a still pose. Motion respects Android's animator setting and stops when the character is hidden, detached, or its screen stops. Text and controls remain accessible without the decorative artwork.

Open `artifacts/SWallet-character-preview.html` for a self-contained gesture preview with light/dark backgrounds and reduced motion. The source atlas, generation prompt, selected frame sequences and integration notes are documented in `artifacts/CHARACTER.md`. Device verification is still outstanding; the HTML preview is not an Android screenshot.

## Planner, profile and reminders update — 20 October 2026

- **Subtasks:** open a task and choose **Add subtask**, or **Save & add subtask** on a new task. Each subtask has its own deadline date/time, reminder day/time, recurrence, importance and completion. Open the parent to check off or edit its subtasks; they also appear in the main checklist with their parent name. One level of subtasks is supported. Archiving/restoring a parent applies to its subtasks, and deleting a parent removes its subtasks and their history.
- **On and after reminders:** choose **On date**, enable **Remind me**, select the reminder day/time, then choose **On and after selected day**. The reminder repeats daily until that one task is completed, including after its deadline. This has one completion record; it does not create a new daily task. Reminder day can precede the deadline. Completed overdue tasks leave later checklists, and their history remains available. Daily and selected-weekday routines keep separate completion records for each occurrence.
- **Goals & achievements:** available from Profile > Tools. Add a future target date, measurable target, unit and current progress. Update progress to the target to save an achievement and show a finite celebration animation. Android's animation setting is respected. Reduce progress to reopen a goal. Goals track your manually entered progress; they do not move money or automatically infer savings.
- **Profile:** the bottom navigation opens the merged profile and settings page with an initials avatar, name, headline, activity statistics and tools. **Edit profile** updates the saved name and headline. Password changes and other settings are on this same page. Earlier references to Settings mean this merged Profile destination.
- **Transaction suggestions:** Add transaction offers up to five repeated entries for the selected income/expense type, based on local transaction history. Matching descriptions/categories are grouped, a typical amount is selected from the median, and date intervals identify common daily/weekly/monthly patterns. Loan-linked, split, invalid and future entries are excluded. Tap to fill editable fields, review, and Save. Suggestions never save automatically.
- **Sound & tone:** Settings > Reminders includes notification sound, vibration, a system tone picker, a test notification and Android channel sound settings. Explicit sound/tone/vibration changes select a new notification channel because Android channel sound cannot be changed after creation. Device notification permission, silent mode and Do Not Disturb still apply. See [Android notification channel sound](https://developer.android.com/reference/android/app/NotificationChannel#setSound(android.net.Uri,android.media.AudioAttributes)).
- **Logo:** the supplied Glossy SWallet Growth Logo is used for full branding. The original is retained in `artifacts/Glossy SWallet Growth Logo.png`; its Android-safe copy is `app/src/main/res/drawable/swallet_logo.png`. The launcher and small headers use the separate wallet-only `drawable-nodpi/swallet_launcher_symbol.png`, with adaptive foreground padding to protect the artwork from icon masks.

The new Room 7→8 migration adds subtask/deadline/reminder fields with compatible defaults and creates the goals table. Existing reminders retain their previous schedules. Latest verification and the device-test limitation are recorded at the top of `artifacts/VERIFICATION.md`.

## Everyday use

- **Home:** Important today shows only tasks marked Important and due on the current date, independently of the money date filter. The dashboard also includes daily, Monday–Sunday weekly, and monthly summaries, monthly budget, today's checklist and recent transactions. Tools are available in Profile. The floating **+** above the bottom navigation adds a transaction.
- **Date filters:** use the Day / Week / Month segments and tap the calendar row to select a date. Month mode opens a month grid with an editable year selector; the arrows move to the previous or next period.
- **Transactions:** record income and expenses, search history, edit ordinary entries, and filter by period and type.
- **Splits:** split a transaction across categories with custom amounts or equal shares. Shares must sum to the original amount. For shared bills, enter the other people's names: the total is recorded once as an expense and their equal shares become receivables in Loans. Any remainder cents go to your share first.
- **Loans:** tap the floating **+** to open the form in loan mode. Borrowing adds income; lending adds an expense. Return or collect all or part of the outstanding balance. Repayments write the transaction and update the loan together. Fully settled loans remain available in the Settled tab. Linked entries are protected against independent edits that would break the loan balance.
- **Tasks:** Checklist, History and All tasks use compact rows. Tap a task row or its title area to open full details; the separate checkbox completes or undoes a scheduled occurrence. Edit fields, reminders, Archive/Restore and two-step Delete are inside task details. Completed-history rows open their saved completion details, including Undo and a link to the task. Mark **Important** in task details and Save to include it on Home when due. Existing tasks start unselected; the star identifies important tasks in the list. All tasks includes future and archived routines. The floating **+** creates a task. Each date has its own completion; history preserves the original title, notes and timestamp.

- **Reminders:** three daily money check-ins, three current-day checklist check-ins, a daily task-planning prompt, and individual task reminders. Times and categories can be changed or disabled. Android notification permission is required. Reminder settings can open Android's precise alarm access screen. With access, reminders use exact alarms; otherwise they use inexact alarms and may be delayed by the OS. Reboot, clock, timezone, app updates, and alarm access broadcasts restore schedules.
- **Reports:** cash flow, expense categories, cumulative period balance, task completion charts, and CSV exports for transactions or completed task history.

## Appearance, deletion and home screen tools

- Settings > Theme offers System, Light and Dark. The selection persists across restarts. Cards, inputs, date controls, navigation, dialogs and report labels follow the theme.
- Type a new category in the transaction form and save. It appears in the category dropdown next time, including custom split categories. Categories remain available after deleting transactions.
- Settings > Delete data contains separate actions for all transactions and all tasks. Open a task and choose Delete task to remove just that task. Each deletion requires Continue, then typing DELETE and pressing Delete. Canceling either step changes nothing. This is deletion confirmation, as requested, rather than sign-in verification.
- Clearing transactions preserves outstanding loan balances and budgets; future loan repayments still work. Clearing tasks removes their completion history and cancels their individual reminder alarms. The general daily checklist/planning reminders are controlled separately in Reminders.
- Add the **Today's tasks** widget from Profile, or long-press the Android home screen > Widgets > SWallet. Tap a parent's title/chevron to expand its subtasks; use the separate checkboxes to complete or undo due work. Leaf task rows also toggle completion when tapped. Tap + for a new task popup, or the header/footer for the full checklist. Expand parent groups and scroll inside the widget to reach the full list; there is no five-task preview limit. Expanded future subtasks show their own deadlines/reminders, with completion disabled until due. Titles wrap. Widget data refreshes on task changes, clock/date changes and periodic Android updates; Android may defer periodic refreshes.
- Long-press the SWallet app icon for Add task, Add transaction, Add loan and Today's tasks. New-entry shortcuts open floating forms over the launcher in a separate task; Save or Back closes the popup. Normal entry points inside the app keep their existing full-screen forms. If signed out, sign in first and the requested shortcut continues. Shortcut visibility and widget pinning depend on launcher support.

Android references: [shortcuts](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts), [widgets](https://developer.android.com/develop/ui/views/appwidgets/overview), [dark themes](https://developer.android.com/develop/ui/views/theming/darktheme).

## Excel import

Export the Google Sheet as `.xlsx`, then choose **Settings → Excel import & export**. Supported inputs:

1. SWallet transaction export: `Title, Amount, Category, Date, Type` headers.
2. The supplied side-by-side account layout: two groups of `Date, Subject, Amount`, with income on the left and expenses on the right. Blank date cells inherit the previous valid date on their respective side. Extra leading columns and header rows are supported.

The app previews the number of additions, existing entries, and invalid entries before saving. Existing transactions are kept. Duplicate detection preserves multiple identical purchases while preventing the same workbook from adding them again. Side-by-side entries receive the Other category for later classification. Text slash dates use month/day/year first, matching the supplied screenshot; ISO dates and actual Excel date cells avoid ambiguity. Dates must include a year.

Excel export contains transactions, not a full backup of profiles, loans, task routines, or completion records. It must not be used to reconstruct loan balances. Legacy loans retain their existing outstanding amount; old repayments cannot be reliably reconstructed because the previous schema did not link them to loans. Database upgrades from versions older than 5 require separate migration work rather than silently deleting those databases.

## Development and verification

Build and run JVM tests:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --offline
```

The offline switch uses dependencies already cached on this machine; omit it when dependencies need downloading. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

Android integration tests cover version 5 migration, both loan directions, overpayment rollback, completion uniqueness and snapshots, and screen recreation. To run on a particular emulator without installing onto other attached devices:

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w com.example.financetracker.test/androidx.test.runner.AndroidJUnitRunner
```

The supplied colour logo is copied unchanged to `app/src/main/res/drawable/swallet_logo.png`. Screens use native Material controls, matching transparent wallet and checklist illustrations, and consistent line icons. Motion includes staggered card entrances, button press feedback, activity transitions, active navigation feedback, and task completion feedback. View animations respect Android's animator setting, use finite durations, and cancel when detached. Window transitions follow Android's window animation setting. No additional production dependencies were added. Generated asset paths and final prompts are documented in `artifacts/ILLUSTRATIONS.md`.

The scrollable task widget uses RemoteCollectionItems on Android 12+ and a permission-protected RemoteViewsService on Android 8-11. Due work is filtered by the current local date, including daily and matching weekday routines. Active future subtasks are also shown inside expanded parent groups; unrelated future tasks and archived families are excluded. Separate row/checkbox actions use collection fill-in intents. See [Android collection widgets](https://developer.android.com/develop/ui/views/appwidgets/collections) and [RemoteViews click actions](https://developer.android.com/reference/android/widget/RemoteViews#setOnClickFillInIntent(int,android.content.Intent)).
