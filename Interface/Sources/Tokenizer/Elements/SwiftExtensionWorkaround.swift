//
//  SwiftExtensionWorkaround.swift
//  hyperdrive
//
//  Created by Matyáš Kříž on 26/06/2019.
//

//#if canImport(SwiftCodeGen) && canImport(UIKit)
//public protocol SwiftExtensionWorkaround: ProvidesCodeInitialization, CanInitializeUIKitView { }
//#elseif canImport(SwiftCodeGen) && HyperdriveRuntime && canImport(AppKit)
//public protocol SwiftExtensionWorkaround: ProvidesCodeInitialization, CanInitializeAppKitView { }
//#else
//#elseif canImport(UIKit) && HyperdriveRuntime
//public protocol SwiftExtensionWorkaround: CanInitializeUIKitView { }
//#elseif canImport(AppKit) && HyperdriveRuntime
//public protocol SwiftExtensionWorkaround: CanInitializeAppKitView { }

#if canImport(SwiftCodeGen)
public protocol SwiftExtensionWorkaround: ProvidesCodeInitialization { }
#else
public protocol SwiftExtensionWorkaround { }
#endif
