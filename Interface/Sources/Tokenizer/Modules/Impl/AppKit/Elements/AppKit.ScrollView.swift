//
//  AppKit.ScrollView.swift
//  Tokenizer
//
//  Created by Matyáš Kříž on 28/06/2019.
//

#if HyperdriveRuntime && canImport(AppKit)
import AppKit
#endif

extension Module.AppKit {
    public class ScrollView: Container {
        public override class var availableProperties: [PropertyDescription] {
            return Properties.scrollView.allProperties
        }

        public override class func runtimeType() throws -> String {
            return "NSScrollView"
        }

        public override func runtimeType(for platform: RuntimePlatform) throws -> RuntimeType {
            if let runtimeTypeOverride = runtimeTypeOverride {
                return runtimeTypeOverride
            }
            return RuntimeType(name: "NSScrollView", module: "AppKit")
        }

        #if HyperdriveRuntime && canImport(AppKit)
        public override func initialize(context: ReactantLiveUIWorker.Context) -> NSView {
            return NSScrollView()
        }
        #endif
    }

    public class ScrollViewProperties: ViewProperties {
        public let contentOffset: StaticAssignablePropertyDescription<Point>
        public let contentSize: StaticAssignablePropertyDescription<Size>
        public let contentInset: StaticAssignablePropertyDescription<EdgeInsets>
        public let isScrollEnabled: StaticAssignablePropertyDescription<Bool>
        public let isDirectionalLockEnabled: StaticAssignablePropertyDescription<Bool>
        public let isPagingEnabled: StaticAssignablePropertyDescription<Bool>
        public let bounces: StaticAssignablePropertyDescription<Bool>
        public let alwaysBounceVertical: StaticAssignablePropertyDescription<Bool>
        public let alwaysBounceHorizontal: StaticAssignablePropertyDescription<Bool>
        public let delaysContentTouches: StaticAssignablePropertyDescription<Bool>
        #warning("TODO Add a `ScrollViewDecelerationRate` type that'll have `default` so we don't have to say exactly `0.998`.")
        public let decelerationRate: StaticAssignablePropertyDescription<Double>
        public let scrollIndicatorInsets: StaticAssignablePropertyDescription<EdgeInsets>
        public let showsHorizontalScrollIndicator: StaticAssignablePropertyDescription<Bool>
        public let showsVerticalScrollIndicator: StaticAssignablePropertyDescription<Bool>
        public let zoomScale: StaticAssignablePropertyDescription<Double>
        public let maximumZoomScale: StaticAssignablePropertyDescription<Double>
        public let minimumZoomScale: StaticAssignablePropertyDescription<Double>
        public let bouncesZoom: StaticAssignablePropertyDescription<Bool>
        public let indicatorStyle: StaticAssignablePropertyDescription<ScrollViewIndicatorStyle>

        public required init(configuration: Configuration) {
            contentOffset = configuration.property(name: "contentOffset", defaultValue: .zero)
            contentSize = configuration.property(name: "contentSize", defaultValue: .zero)
            contentInset = configuration.property(name: "contentInset", defaultValue: .zero)
            isScrollEnabled = configuration.property(name: "isScrollEnabled", key: "scrollEnabled", defaultValue: true)
            isDirectionalLockEnabled = configuration.property(name: "isDirectionalLockEnabled", key: "directionalLockEnabled")
            isPagingEnabled = configuration.property(name: "isPagingEnabled", key: "pagingEnabled")
            bounces = configuration.property(name: "bounces", defaultValue: true)
            alwaysBounceVertical = configuration.property(name: "alwaysBounceVertical")
            alwaysBounceHorizontal = configuration.property(name: "alwaysBounceHorizontal")
            delaysContentTouches = configuration.property(name: "delaysContentTouches", defaultValue: true)
            decelerationRate = configuration.property(name: "decelerationRate", defaultValue: 0.998)
            scrollIndicatorInsets = configuration.property(name: "scrollIndicatorInsets", defaultValue: .zero)
            showsHorizontalScrollIndicator = configuration.property(name: "showsHorizontalScrollIndicator", defaultValue: true)
            showsVerticalScrollIndicator = configuration.property(name: "showsVerticalScrollIndicator", defaultValue: true)
            zoomScale = configuration.property(name: "zoomScale", defaultValue: 1)
            maximumZoomScale = configuration.property(name: "maximumZoomScale", defaultValue: 1)
            minimumZoomScale = configuration.property(name: "minimumZoomScale", defaultValue: 1)
            bouncesZoom = configuration.property(name: "bouncesZoom", defaultValue: true)
            indicatorStyle = configuration.property(name: "indicatorStyle", defaultValue: .default)

            super.init(configuration: configuration)
        }
    }
}
