//
//  VisualEffectView.swift
//  ReactantUI
//
//  Created by Matous Hybl.
//  Copyright © 2017 Brightify. All rights reserved.
//

#if canImport(UIKit)
import UIKit
#endif

extension Module.UIKit {
    public class VisualEffectView: Container {
        public override class var availableProperties: [PropertyDescription] {
            return Properties.visualEffectView.allProperties
        }

        public override var addSubviewMethod: String {
            return "contentView.addSubview"
        }

        public override func runtimeType(for platform: RuntimePlatform) throws -> RuntimeType {
            if let runtimeTypeOverride = runtimeTypeOverride {
                return runtimeTypeOverride
            }
            return RuntimeType(name: "UIVisualEffectView", module: "UIKit")
        }

        #if canImport(UIKit)
        public override func initialize(context: ReactantLiveUIWorker.Context) -> UIView {
        return UIVisualEffectView()
        }

        public override func add(subview: UIView, toInstanceOfSelf: UIView) {
            guard let visualEffectView = toInstanceOfSelf as? UIVisualEffectView else {
                return super.add(subview: subview, toInstanceOfSelf: toInstanceOfSelf)
            }
            visualEffectView.contentView.addSubview(subview)
        }
        #endif
    }

    public class VisualEffectViewProperties: ViewProperties {
        public let effect: StaticAssignablePropertyDescription<VisualEffect?>

        public required init(configuration: Configuration) {
            effect = configuration.property(name: "effect")

            super.init(configuration: configuration)
        }
    }
}
