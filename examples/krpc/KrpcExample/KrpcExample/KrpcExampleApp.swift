import SwiftUI
import KrpcExampleKit

@main
struct KrpcExampleApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self)
    var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        print("Your code here")

//        let service = ExampleServiceKt.makeClient()
//        service.strlen(parameter: "yo", completionHandler: { result, error in
//            print("Result", result, "error", error)
//        })

        ExampleServiceKt.runClient { r, e in
            print("R", r, "E", e)
        }

        return true
    }
}
