# Poshan for Life — iOS

Native SwiftUI client. Swift, MVVM, `URLSession` + async/await, iOS 16.0
minimum. No third-party dependencies.

## Status

Written: **IOS-01** (scaffold, networking, DI, Keychain), the **SETUP theme
prompt** (built early because IOS-02 depends on it), **IOS-02** (auth,
role-based navigation, theme selection, token refresh), **IOS-03** (patient
dashboard), **IOS-04** (health tracking, reminders, goals), **IOS-05** (InBody
report list, detail, Swift Charts trends), **IOS-06** (programmes, sessions and
challenges with check-in).

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
| PATIENT | TabView, 5 tabs | Patient | Home · Track · Programmes · Reports · Profile |
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

| Account | Purpose |
| --- | --- |
| `ios6.patient@example.com` | one programme, two sessions (one past, one future), one challenge |
| `ios6.empty@example.com` | no assignments at all — the empty state |

Both use `Ios6Test@123` and were created via `POST /users` as admin. Note that
route does **not** create a `patient_profiles` row, so weight logged by these
accounts stays local and never reaches `health_records` — an artifact of the
fixture, not a sync bug.

`testProgrammesListAndDetail` checks in to the challenge, so re-running it needs
the progress reset first:

```sql
delete from challenge_progress cp using patient_programmes pp
 where cp.patient_programme_id = pp.id
   and pp.patient_id = (select id from users where email = 'ios6.patient@example.com');
```
