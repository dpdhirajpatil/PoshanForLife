# Poshan for Life — iOS

Native SwiftUI client. Swift, MVVM, `URLSession` + async/await, iOS 16.0
minimum. No third-party dependencies.

## Status

Written: **IOS-01** (scaffold, networking, DI, Keychain), the **SETUP theme
prompt** (built early because IOS-02 depends on it), **IOS-02** (auth,
role-based navigation, theme selection, token refresh), **IOS-03** (patient
dashboard).

The Xcode project itself has **not** been generated — this machine has Command
Line Tools only, no Xcode, so there is no iOS SDK, no Simulator, and no
`xcodebuild`.

What that means in practice:

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
- Not yet verified: anything needing the iOS SDK — that the app launches, that
  the Simulator reaches `localhost`, that the Keychain accessibility flag
  behaves on device, that the two Info.plists wire up, and every question about
  how the UI actually *looks*. No screen has been rendered.

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

## Getting it running (once Xcode is installed)

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
  UI/
    Theme/              IOS-03 fills this (BrandColors, fonts, AppTheme)
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
