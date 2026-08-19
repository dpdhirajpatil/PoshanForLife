import BackgroundTasks
import Foundation

/// `BGAppRefreshTask` registration + scheduling for `HealthKitManager.syncNow()`.
///
/// Opportunistic, not guaranteed — iOS decides if and when this actually
/// runs based on usage patterns, charging state, and its own judgment, which
/// is exactly why the Track tab also has a manual "Sync now" button that
/// doesn't depend on any of this. This exists to keep the local cache roughly
/// current between opens, not to be the only way a sync happens.
enum HealthKitBackgroundSync {

    static let taskIdentifier = "com.poshanforlife.ios.healthkit-sync"

    /// Must run before `application(_:didFinishLaunchingWithOptions:)`
    /// returns — `BGTaskScheduler` requires every launch handler registered
    /// before then, so this can't wait for `AppContainer` to exist.
    /// `HealthKitManager.shared` is reachable regardless, for the same
    /// reason `PushCoordinator.shared` is.
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            handle(task as! BGAppRefreshTask)
        }
    }

    /// Call whenever the app backgrounds — a completed run reschedules
    /// itself too, but the first request has to come from somewhere.
    static func scheduleNext() {
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        // Matches Android's WorkManager cadence for the equivalent Health
        // Connect sync (`HealthConnectSyncScheduler`, every 4 hours) — iOS
        // treats this as a floor, not a promise, but it's the same intent.
        request.earliestBeginDate = Date(timeIntervalSinceNow: 4 * 3600)
        try? BGTaskScheduler.shared.submit(request)
    }

    private static func handle(_ task: BGAppRefreshTask) {
        // Queue the next attempt immediately — if this run gets killed by the
        // expiration handler below, the system shouldn't stop trying.
        scheduleNext()

        let syncTask = Task { @MainActor in
            await HealthKitManager.shared.syncNow()
            task.setTaskCompleted(success: true)
        }
        task.expirationHandler = {
            syncTask.cancel()
        }
    }
}
