# Poshan for Life — iOS

Native SwiftUI client. Swift, MVVM, `URLSession` + async/await, iOS 16.0
minimum. **Firebase Cloud Messaging (via Swift Package Manager) is the one
deliberate third-party dependency**, added for IOS-08's push notifications —
see that section below for why it can't be avoided and what's still blocked
on a manual step.

## Status

Written: **IOS-01** (scaffold, networking, DI, Keychain), the **SETUP theme
prompt** (built early because IOS-02 depends on it), **IOS-02** (auth,
role-based navigation, theme selection, token refresh), **IOS-03** (patient
dashboard), **IOS-04** (health tracking, reminders, goals), **IOS-05** (InBody
report list, detail, Swift Charts trends), **IOS-06** (programmes, sessions and
challenges with check-in), **IOS-07** (appointments — patient booking and
practitioner schedule), **IOS-08** (push notifications — code complete;
blocked on one manual Firebase console step, see below).

**The app builds and runs on the Simulator.** Xcode 26.6 (iOS 26.5 SDK).

> `xcode-select -p` still points at the Command Line Tools, so prefix commands
> with `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer`, or run
> `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer` once.

Verification status:

- All 34 Swift files **type-check clean** against the macOS SDK
  (`swiftc -typecheck -target arm64-apple-macos13.0 -swift-version 5`).
  Three files need 9 lines shimmed for that run only — `insetGrouped`,
  `keyboardType`, `textContentType`, `textInputAutocapitalization`,
  `scrollDismissesKeyboard` are iOS-only. The committed sources keep the
  iOS-correct calls.
- Envelope, Keychain, and Endpoint logic **verified at runtime**, including
  against the live backend on `localhost:8080` — real 401 and real 422
  responses decode correctly, with validation field messages intact.
- Token refresh **verified against a stubbed transport**: retry-after-refresh,
  refresh-failure sign-out, no-refresh-token, no refresh on a public-endpoint
  401, and six concurrent 401s collapsing into exactly one refresh call.
- Dashboard date/currency logic and all four of its endpoints **verified live**,
  including that health records come back ascending (so the newest is `.last`)
  and that the documents API rejects `status=pending`.
- **Rendered on Simulator**: login → patient dashboard → Track (water, sleep,
  weight, goals) → all five tabs, driven by the `PoshanForLifeUITests` XCUITest
  target (see "Driving the UI" below).
- IOS-04 logic verified against the live backend: weight reaches
  `health_records` as `patient_manual` and upserts by day, water/nutrition/sleep
  never queue for a sync they can't complete, and the backend correctly rejects
  both `source=manual` and a foreign `patientId` from a patient-role caller.
- Still unverified: behaviour on a **physical device** — the LAN base URL, and
  the Keychain `ThisDeviceOnly` accessibility flag under a real lock screen.

## Navigation by role

| Role (wire) | Structure | Theme | Destinations |
| --- | --- | --- | --- |
| PATIENT | TabView, 4 + More | Patient | Home · Track · Programmes · Reports · More (Appointments, Profile) |
| LEAD | TabView, 4 tabs | Lead | Home · Track · Goals · Profile |
| DOCTOR | TabView, 4 + More | Staff | Patients · Leads · Upload · Schedule · More (Orders, Products, Settings) |
| ADMIN | NavigationStack + List | Staff | Dashboard · Patients · Leads · Orders · Transactions · Products · Settings |

Admin is a settings-style List, not a tab bar — the iOS-native answer to the
"Admin needs a drawer" question Android solved with `RoleScaffoldDrawer`. Seven
destinations don't fit a tab bar, and each is visited occasionally.

The login screen is wrapped in **StaffTheme**: the role isn't known until login
succeeds, so the neutral theme is the only honest choice. It re-themes the
instant `state` becomes `.loggedIn`.

Themes are injected with `.appTheme(PatientTheme.self)` rather than
`.environment(\.appTheme, PatientTheme())`. The latter captures one appearance
at construction and never updates, which would freeze the whole app in light
mode. See `UI/Theme/AppTheme.swift`.

## Getting it running

```sh
brew install xcodegen
cd ios
xcodegen generate
open PoshanForLife.xcodeproj
```

The `.xcodeproj` is generated and gitignored — `project.yml` is the source of
truth. Regenerate after adding files.

## Backend URL

Resolved at build time by `Core/Networking/BaseURL.swift`:

| Build | Target | URL |
| --- | --- | --- |
| Debug | Simulator | `http://localhost:8080` |
| Debug | Device | `http://$(DEV_LAN_HOST):8080` |
| Release | — | `https://api.poshanforlife.com` |

The Simulator shares the host's network stack, so `localhost` reaches a backend
running on this Mac — unlike Android, whose emulator needs `10.0.2.2`.

For a physical device, set your Mac's LAN IP in `Config/Local.xcconfig`
(gitignored, so it won't follow you onto anyone else's machine):

```
DEV_LAN_HOST = 192.168.1.87
```

## App Transport Security

Debug and Release use **separate Info.plist files**, selected by
`INFOPLIST_FILE` in the xcconfigs. This is not a style preference: ATS is read
from Info.plist by CFNetwork at connection time, so no `#if DEBUG` or runtime
check can gate it. Keeping the exception out of Release requires it to live in
a file Release never reads.

The Debug exception is scoped, not blanket — `NSAllowsLocalNetworking` for the
LAN IP plus a `localhost` exception domain, rather than
`NSAllowsArbitraryLoads`. Mirrors
`android/app/src/debug/res/xml/network_security_config.xml`.

## Layout

```
Config/                 xcconfigs (Base / Debug / Release, + gitignored Local)
PoshanForLife/
  App/                  entry point, AppContainer, RootView
  Core/
    Networking/         APIClient, Endpoint, APIResponse, APIError, BaseURL
    Data/               repository implementations
    Domain/             domain models
    Keychain/           token storage
  Features/             Auth, Patient, Practitioner, Admin, Lead
PoshanForLifeUITests/   XCUITest target that drives the app on a Simulator
  UI/
    Theme/              BrandColors, fonts, AppTheme, TrapeziumShape
    Components/         shared views
```

`AppContainer` lives in `App/`, following the prompt's directory tree; the
prompt's DI section calls it `Core/AppContainer.swift`. One or the other — the
tree won.

## Notes carried over from the Android build

- `Features/Practitioner` is the **DOCTOR** role on the wire. Translation lives
  in `UserRole` and nowhere else.
- `User.email` is optional. Phone-OTP accounts have a verified phone instead,
  and the backend guarantees only that every user has at least one of the two.
  A non-optional email crashed the first phone signup on Android.
- `APIError.details` is a field → message **map**, not a string. The backend
  types it as `Object` and bean-validation failures fill it in.


## Driving the UI

`simctl` has no touch injection, and AppleScript keystrokes need an
Accessibility grant the CLI doesn't have. The working route is the XCUITest
target, which types, taps and saves screenshots as attachments:

```sh
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
xcodebuild test -project PoshanForLife.xcodeproj -scheme PoshanForLife \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  -resultBundlePath /tmp/Result.xcresult
xcrun xcresulttool export attachments \
  --path /tmp/Result.xcresult --output-path /tmp/shots
```

`DashboardUITests` needs the backend on `localhost:8080` and valid patient
credentials, so it's an integration smoke test — not something to put in CI
unchanged. It signs out first if a previous run left a session in the Keychain.

Note iOS excludes `SecureField` content from screen captures, so a filled
password field looks empty in screenshots. That's the platform, not a bug.


## What syncs, and what doesn't (IOS-04)

`health_records` stores **body composition only**. There is no column for water
intake, calories or sleep, and no endpoint that accepts them — `bodyWaterL` is
an InBody measurement, not drinking water.

So of everything on the Track tab, only **weight** reaches the server. The rest
is local-only by design, and `HealthEntry.syncState` says so explicitly with a
`localOnly` case. That case matters: without it every water tap would sit
forever in a pending queue, retrying an upload that has nowhere to land.

Writing weight as a patient has two rules worth remembering, both enforced
server-side and both verified in the tests:

- `source` **must** be `patient_manual` or `wearable_sync`. Sending `manual`
  (the staff value) is a 422.
- `patientId` **must not** be sent. A patient may only write their own record,
  and naming one — even their own — is rejected.

## Local cache: iOS 16 vs 17

SwiftData would be the obvious choice and is deliberately not used: it needs
iOS 17 and this app targets iOS 16. The alternatives were GRDB, which buys real
querying at the cost of the zero-dependency rule, or a JSON file. The data is a
few hundred small rows read wholesale on one screen, so `JSONFileStore` is
enough. **If the deployment target ever rises to 17, delete that file and use
SwiftData** — the repository is the only thing that touches it.


## Reports & trends (IOS-05)

Three contract details that are easy to get wrong, all verified against the
running backend:

- The `fields` param on `health-records` accepts only `weight`, `bodyFat`,
  `bmi`, `skeletalMuscleMass`, `visceralFat`, `bodyWater`, `protein`,
  `mineral`, `bmr`. An unknown name is **silently ignored** and the metric
  comes back null — so a typo produces an empty chart with no error. Note the
  query name (`bodyFat`) differs from the JSON field (`bodyFatPct`).
- `ReportType` and `ReportStatus` are **lowercase** on the wire (`"inbody"`).
  The backend uppercases query values, so `type=INBODY` works as a filter — but
  comparing a response against `"INBODY"` will not.
- `GET /reports` returns `{reports: [...], stats: {...}}`, not a bare array.

`confidence` is only on the report *detail*, never the list, so the
low-confidence warning lives on the detail screen — putting a badge in the list
would mean fetching every report's detail just to draw it.

There is **no segmental (arm/trunk/leg) data** in the backend, so there is no
Segmental Lean Analysis section. "Goals & control" takes its place — target
weight and the weight/fat/muscle control figures, which are real InBody outputs
that had nowhere else to appear.

Deltas shown next to each trend are the backend's own pre-computed values, not
recomputed here: the client only holds a windowed slice, so it often doesn't
have the reading immediately before the first point on screen.


## Programmes & sessions (IOS-06)

`GET /patients/{id}/programmes` returns every field the detail screen shows, so
tapping a row pushes the model rather than re-fetching it.

**There is no service description, and there cannot be one.** The prompt asks
the detail screen for a full description; `ServiceRefDto` carries only id, name,
serviceCode and a duration, and the catalogue endpoint that holds the
description (`/catalogue/{type}`) is class-level `@AdminOrDoctor` — a patient
gets `403 INSUFFICIENT_ROLE`, verified live. Android doesn't show one either.

The three service types measure different things, so each gets its own
treatment rather than one shared progress bar:

| Type | Progress shown | Source |
| --- | --- | --- |
| Programme | elapsed / total calendar days | computed from start+end |
| Challenge | check-ins completed | server's `percentComplete` + streak |
| Session | Completed · Today · In N days | derived from `startDate` |

Challenges deliberately do **not** use calendar progress, which the prompt
implies. A challenge measures what you did, not what day it is — elapsed time
would tell someone who has never checked in that they're 80% done. The cost is
an N+1: the list endpoint carries no check-in data, so each challenge row needs
its own `/progress` call. Bounded by only fetching for `challenge` rows (asking
for a programme or session is a **404**, not an empty result) and by running
them concurrently.

Sessions are single-day — `endDate == startDate` — and their *assignment
status* stays `active` long after the appointment has happened, so "has it
happened yet" is derived from the date, never from `status`.

Ordering is re-done client-side. The backend sorts by `createdAt` descending,
which is data-entry order and puts a finished consultation above the 12-week
programme the patient is halfway through.

### The keyboard-over-the-tab-bar bug (found by these tests)

Returning from Goals to Track brought the keyboard back up unprompted, and its
`ToolbarItemGroup(placement: .keyboard)` Done button renders **over the tab
bar** — so the next tap on Reports or Profile hit the keyboard and silently
typed a digit into the weight field instead of switching tabs (the field read
`70.53` after a test typed `70.5`).

Clearing `@FocusState` does not fix it: tried on disappear, on appear, and on
the next runloop pass, and the keyboard stayed up every time. `TrackView` now
also calls `resignFirstResponder` directly. `DashboardUITests` asserts the
keyboard is down before it changes tabs, so a recurrence names itself instead
of surfacing as "Reports tab never appeared".

### Test fixtures

`ProgrammesUITests` needs two accounts that the seed data doesn't provide:

| Account | Password | Purpose |
| --- | --- | --- |
| `testpatient1@example.com` | `Admin@123` | one programme, two sessions (one past, one future), one challenge; also the Dashboard and Appointments tests |
| `empty.patient@example.com` | `Empty@1234` | no assignments at all — the empty state |
| `testdoc1@example.com` | `Admin@123` | the practitioner side; already linked to testpatient1 in `doctor_patients` |

The dev database is reseeded from outside this repo from time to time, which
deletes any account created with `POST /users`. If the empty-state test starts
failing on login, recreate `empty.patient@example.com` as admin. Note that route
does **not** create a `patient_profiles` row, so weight logged by such an
account stays local and never reaches `health_records` — an artifact of the
fixture, not a sync bug.

`AppointmentsUITests` seeds and deletes its own appointment through the API, so
it leaves the database as it found it.

## Appointments (IOS-07)

### The bookable window is in UTC, and that leaks

`AppointmentService` hardcodes working hours as 09:00–17:00 and builds every
slot with `ZoneOffset.UTC`, so a slot returned as `"09:00:00"` means 09:00
**UTC**. In IST that is 2:30 PM, and the bookable day reads 2:30 PM–10:00 PM —
an odd-looking clinic day, but a faithful rendering of what the backend
actually offers. Fixing the window properly is a backend change (per-clinic or
per-practitioner hours); there is no per-practitioner schedule config today.

Everything user-facing renders in the **device's** time zone, including the
slot buttons. That consistency is the point: the Android client prints the raw
UTC `LocalTime` in `BookAppointmentScreen` while `AppointmentsListScreen`
converts with `ZoneId.systemDefault()`, so on Android you pick "09:00" and the
booking then appears as 2:30 PM. `AppointmentTime` exists to prevent exactly
that, and the UI test asserts the slot label and the list row agree.

### Other contract details, all verified live

- `AppointmentStatus` has **four** values — `no_show` as well as
  scheduled/completed/cancelled.
- Slot times serialise as `"HH:mm:ss"`, not the `"HH:mm"` the DTO comment says.
- `GET /appointments/practitioners` is the only way a patient can discover who
  they may book with; booking with anyone not in `doctor_patients` is refused.
- A patient may only touch `scheduledAt` and `status=cancelled`. Notes and any
  other status are `INSUFFICIENT_ROLE`.
- **Rescheduling a cancelled appointment silently revives it** to `scheduled`.
  That is why Reschedule and Cancel are offered only on scheduled, not-yet-past
  rows — a swipe action on a cancelled row would quietly un-cancel it.
- Touching someone else's appointment is a **403**, not the 404 that reports
  and programmes return for the same mistake.

### Where it lives in the nav

Patient tabs are Home · Track · Programmes · Reports · **More**, with
Appointments and Profile inside More. SwiftUI's `TabView` silently collapses
anything past five tabs into a *system-generated* overflow list that ignores
the app theme — adding Appointments as a sixth tab did exactly that, burying
Profile and the real More screen in an unthemed system list. Five is a hard
ceiling; the fifth is ours.

Practitioner reaches the same view through its existing Schedule tab.

## Push notifications (IOS-08)

### Blocked on one manual step — Firebase console app registration

The prompt's SETUP section asks to "Add the iOS app to the SAME Firebase
project already created for Android" via the console, then download
`GoogleService-Info.plist`. **That step is not done.** It needs an interactive
browser session against `poshan-for-life-1` (the real project — see
`android/app/google-services.json`), which isn't available here, and the
backend's `firebase-service-account.json` is an Admin SDK key scoped for
server-side sending, not the Firebase Management API that registers new apps —
using it for that would be reaching outside what that credential was
provisioned for.

**To finish this**: Firebase console → `poshan-for-life-1` → Add app → iOS →
bundle ID `com.poshanforlife.ios` → download the resulting
`GoogleService-Info.plist` → drop it into `ios/PoshanForLife/` (picked up
automatically by the existing `sources:` glob in `project.yml`, no project.yml
change needed) → also add an APNs auth key or certificate in the same console,
under the app's Cloud Messaging settings.

**Everything else works today, verified live, without that file.** The app
never crashes on its absence — see `FirebaseBootstrap.swift`'s doc comment for
why that guard is load-bearing, not optional: `FirebaseApp.configure()`
terminates the process outright on a missing/malformed plist, so shipping this
feature unguarded would have broken every existing screen's tests the moment
it landed. Confirmed by literally uninstalling the app, launching cold with no
plist present, and running the full existing UI test suite against it — zero
regressions.

### What's real and independently verified

- **In-app notification list + bell**, pure REST, no Firebase involved:
  `GET /notifications` returns `{notifications, unreadCount}` (an envelope,
  not a bare array — same convention as reports/programmes), `PATCH
  /notifications` marks everything read (there is **no per-notification**
  mark-read — the backend's own doc comment says so). Verified against the
  live backend, 12/12 checks, including that `PATCH /users/me` **is not a
  route** (400 — `id` fails UUID binding) despite the prompt's exact spec;
  the real route is `PATCH /users/{id}` with the caller's own id.
- **The full permission chain**, on-device: the rationale sheet → the real
  system `UNUserNotificationCenter` prompt (handled via
  `addUIInterruptionMonitor` in `PushNotificationsUITests`, tapping the
  actual OS "Allow" button, not a stand-in) → granted → back to a fully
  functional app. Screenshotted at each step.
- **Foreground push delivery**, via `xcrun simctl push` — this delivers
  through the real OS notification pipeline into
  `UNUserNotificationCenterDelegate` without touching Firebase/APNs at all,
  since that delegate doesn't care where a notification originated. A
  simulated "New appointment booked" push while the app sat foregrounded on
  a placeholder screen produced a real banner, proving
  `willPresent` correctly opts back into `.banner/.sound/.badge` (iOS
  suppresses foreground banners unless a delegate explicitly asks for them).

### What's NOT independently verified

Registering with FCM and receiving a real token — `didRegisterForRemoteNotificationsWithDeviceToken`
→ `Messaging.apnsToken` → `MessagingDelegate.didReceiveRegistrationToken` — needs
a configured `FirebaseApp`, which needs the plist above. This is the one part
of the feature that can only be exercised once that step is done.

### Deep links exist as plumbing, not as working navigation — on purpose

Every `relatedEntityType` the backend actually sends (`patient`, `lead`,
`report`, `badge` — grep-verified against all ten `notificationService.create`
call sites) targets an iOS screen that **does not exist yet**: Practitioner's
Patients/Leads tabs are still placeholders, there's no report-detail screen
for a doctor recipient, and there's no Badges screen at all. `DeepLinkRouter`
correctly captures the target from both a tapped push and a tapped in-app row
— that part is real and testable — but nothing consumes it into an actual
navigation today, and building fake destinations to paper over that gap would
be worse than being honest about it. Wiring a real case in is a one-line
addition the day any of those screens lands; see `DeepLinkRouter.swift`.

### The one `.shared` singleton in this codebase

`PushCoordinator.shared` bridges `MessagingDelegate` and
`UNUserNotificationCenterDelegate` — both `@objc` protocols `AppDelegate` sets
itself as the target for — into the rest of the app. Everything else here is
constructor-injected through `AppContainer`, but `AppDelegate` is instantiated
by `@UIApplicationDelegateAdaptor` with no DI entry point SwiftUI exposes, so
there's no normal way to hand it a dependency built from `AppContainer`. See
`PushCoordinator.swift`'s doc comment.

### XcodeGen gotcha: hand-editing the entitlements file doesn't stick

`PoshanForLife.entitlements` is generated by `xcodegen generate` from
`project.yml`'s `entitlements.properties` on every run — a hand-authored
`aps-environment` key gets silently stomped back to an empty `<dict/>` the
next time anyone regenerates. It's gitignored for the same reason `.xcodeproj`
is; the source of truth is `project.yml`, which sets `aps-environment` to
`$(APS_ENVIRONMENT)`, resolved per build configuration in
`Config/Debug.xcconfig` (`development`) / `Config/Release.xcconfig`
(`production`).

## Running the UI tests

`PushNotificationsUITests.testRationaleSheetThenSystemPrompt` needs a
genuinely fresh app container — the "have we shown the rationale sheet" flag
lives in UserDefaults, which `simctl uninstall` wipes. Run it on its own,
right after a fresh install, not as part of the full suite (where an earlier
test's sign-in/out dance has already set the flag):

```sh
xcrun simctl uninstall <device> com.poshanforlife.ios
xcodebuild test -project PoshanForLife.xcodeproj -scheme PoshanForLife \
  -destination 'platform=iOS Simulator,id=<device>' \
  -only-testing:PoshanForLifeUITests/PushNotificationsUITests/testRationaleSheetThenSystemPrompt
```

The other two `PushNotificationsUITests` methods have no such requirement and
run fine as part of the full suite.

`testProgrammesListAndDetail` checks in to the challenge, so re-running it needs
the progress reset first:

```sql
delete from challenge_progress cp using patient_programmes pp
 where cp.patient_programme_id = pp.id
   and pp.patient_id = (select id from users where email = 'testpatient1@example.com');
```
