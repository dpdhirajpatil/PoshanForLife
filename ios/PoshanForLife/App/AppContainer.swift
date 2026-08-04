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
final class AppContainer: ObservableObject {

    let tokenStore: TokenStore
    let apiClient: APIClient
    let authRepository: AuthRepository
    let dashboardRepository: DashboardRepository

    init(tokenStore: TokenStore = KeychainTokenStore()) {
        self.tokenStore = tokenStore
        let client = APIClient(tokenStore: tokenStore)
        self.apiClient = client
        self.authRepository = AuthRepositoryImpl(client: client, tokenStore: tokenStore)
        self.dashboardRepository = DashboardRepositoryImpl(client: client)
    }

    /// Refresh failed and the session is gone — `AuthViewModel` listens and
    /// drops to the login screen.
    var sessionExpired: AnyPublisher<Void, Never> {
        apiClient.sessionExpired.eraseToAnyPublisher()
    }

    /// Everything in memory — for previews and tests.
    static func preview() -> AppContainer {
        AppContainer(tokenStore: InMemoryTokenStore())
    }
}
