import Foundation

/// Collapses concurrent refresh attempts into one.
///
/// Without this, a screen that fires five requests on appear gets five 401s and
/// five simultaneous `POST /auth/refresh` calls. With refresh-token rotation
/// that's not just wasteful — the first response invalidates the token the
/// other four are using, so four of them fail and sign the user out mid-session.
/// Everyone who arrives while a refresh is in flight awaits the same result.
///
/// This is the piece OkHttp's `Authenticator` provides for free on Android;
/// `URLSession` has no equivalent, so it's explicit here.
actor TokenRefreshCoordinator {

    private var inFlight: Task<Bool, Never>?

    func refresh(using perform: @escaping () async -> Bool) async -> Bool {
        if let existing = inFlight {
            return await existing.value
        }
        let task = Task { await perform() }
        inFlight = task
        let succeeded = await task.value
        inFlight = nil
        return succeeded
    }
}
