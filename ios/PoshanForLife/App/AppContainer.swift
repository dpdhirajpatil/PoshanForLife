import Combine
import Foundation

/// Composition root. Holds one instance of each long-lived dependency and hands
/// them to whoever asks — no Swinject/Factory/Resolver.
///
/// SwiftUI already does the two things a DI framework would be here for:
/// constructor injection via `init` parameters, and scoped lookup via
/// `@EnvironmentObject`. Adding a container framework on top would buy
/// registration syntax and nothing else at this app's size.
///
/// `ObservableObject` purely so it can travel through `.environmentObject`; it
/// publishes nothing itself, since the dependencies never change after launch.
///
/// `@MainActor` because it constructs the UI-facing stores (health tracking,
/// goals, reminders), which are main-actor isolated so views can read them
/// without hopping. It's only ever built from `App.body`, which is already on
/// the main actor.
@MainActor
final class AppContainer: ObservableObject {

    let tokenStore: TokenStore
    let apiClient: APIClient
    let authRepository: AuthRepository
    let dashboardRepository: DashboardRepository
    let reportsRepository: ReportsRepository
    let programmesRepository: ProgrammesRepository
    let patientsRepository: PatientsRepository
    let appointmentsRepository: AppointmentsRepository
    let notificationsRepository: NotificationsRepository
    let userRepository: UserRepository

    /// The one thing this container reaches outside its own constructor
    /// graph for — see `PushCoordinator`'s doc comment for why `AppDelegate`
    /// can't be handed a dependency the normal way.
    let pushCoordinator = PushCoordinator.shared
    var deepLinkRouter: DeepLinkRouter { pushCoordinator.deepLinkRouter }

    /// These three are `ObservableObject`s held for the app's lifetime rather
    /// than per-screen: the dashboard reads reminders that the Track tab
    /// writes, so a second instance would show stale data.
    let healthTracking: HealthTrackingRepository
    let goalsStore: GoalsStore
    let reminderScheduler: ReminderScheduler

    init(tokenStore: TokenStore = KeychainTokenStore()) {
        self.tokenStore = tokenStore
        let client = APIClient(tokenStore: tokenStore)
        self.apiClient = client
        self.authRepository = AuthRepositoryImpl(client: client, tokenStore: tokenStore)
        self.dashboardRepository = DashboardRepositoryImpl(client: client)
        self.reportsRepository = ReportsRepositoryImpl(client: client)
        self.programmesRepository = ProgrammesRepositoryImpl(client: client)
        self.patientsRepository = PatientsRepositoryImpl(client: client)
        self.appointmentsRepository = AppointmentsRepositoryImpl(client: client)
        self.notificationsRepository = NotificationsRepositoryImpl(client: client)
        self.userRepository = UserRepositoryImpl(client: client)
        self.healthTracking = HealthTrackingRepository(client: client)
        self.goalsStore = GoalsStore()
        self.reminderScheduler = ReminderScheduler()
    }

    /// Refresh failed and the session is gone — `AuthViewModel` listens and
    /// drops to the login screen.
    var sessionExpired: AnyPublisher<Void, Never> {
        apiClient.sessionExpired.eraseToAnyPublisher()
    }

    /// Sends the current FCM token (if Firebase has minted one yet) to the
    /// backend for `userId`, and keeps sending it if a rotation happens later
    /// while this session is signed in. Safe to call every time a session
    /// becomes active — `PushCoordinator.onTokenChange` fires immediately
    /// with the cached token if there is one, so a login *after* the token
    /// arrived and a login *before* it arrived both end up registered.
    func registerPushToken(for userId: String) {
        let userRepository = self.userRepository
        pushCoordinator.onTokenChange { token in
            Task { _ = await userRepository.updateFcmToken(userId: userId, token: token) }
        }
    }

    /// Everything in memory — for previews and tests.
    static func preview() -> AppContainer {
        AppContainer(tokenStore: InMemoryTokenStore())
    }
}
