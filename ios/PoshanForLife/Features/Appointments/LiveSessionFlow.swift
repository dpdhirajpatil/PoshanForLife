import SwiftUI

/// Owns the pre-call → call navigation as its own stack, independent of
/// whatever `NavigationStack` the presenting screen (patient or
/// practitioner) is using — presented full-screen from a "Join call" tap,
/// same shape as IOS-10's `CaptureView`.
struct LiveSessionFlow: View {

    let otherPartyName: String
    let onDismiss: () -> Void

    @State private var joined = false

    var body: some View {
        NavigationStack {
            PreCallView(
                otherPartyName: otherPartyName,
                onJoin: { joined = true },
                onCancel: onDismiss
            )
            .navigationDestination(isPresented: $joined) {
                LiveSessionView(otherPartyName: otherPartyName, onLeave: onDismiss)
            }
        }
    }
}
