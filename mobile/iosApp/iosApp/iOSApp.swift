import ComposeApp
import SwiftUI

@main
struct iOSApp: App {
    init() {
        KoinHelperKt.doInitKoin(config: nil)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
