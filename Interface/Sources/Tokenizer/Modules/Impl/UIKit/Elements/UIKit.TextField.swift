//
//  TextField.swift
//  ReactantUI
//
//  Created by Matous Hybl.
//  Copyright © 2017 Brightify. All rights reserved.
//

#if canImport(UIKit)
import UIKit
import HyperdriveInterface
#endif

extension Module.UIKit {
    public class HyperTextField: View {
        public override class var availableProperties: [PropertyDescription] {
            return Properties.hyperTextField.allProperties
        }

        public override class var parentModuleImport: String {
            return "HyperdriveInterface"
        }

        public class override func runtimeType() -> String {
            return "HyperdriveInterface.HyperTextField"
        }

        public override func supportedActions(context: ComponentContext) throws -> [UIElementAction] {
            return try super.supportedActions(context: context) + ControlEventAction.allEditingEvents
        }

        public override func runtimeType(for platform: RuntimePlatform) throws -> RuntimeType {
            if let runtimeTypeOverride = runtimeTypeOverride {
                return runtimeTypeOverride
            }
            return RuntimeType(name: "HyperTextField", module: "HyperdriveInterface")
        }

        #if canImport(UIKit)
        public override func initialize(context: ReactantLiveUIWorker.Context) -> UIView {
            return HyperdriveInterface.HyperTextField()
        }
        #endif
    }

    public class HyperTextFieldProperties: PropertyContainer {
        public let textFieldProperties: BaseTextFieldProperties

        public let placeholder: StaticAssignablePropertyDescription<TransformedText?>
        public let contentEdgeInsets: StaticAssignablePropertyDescription<EdgeInsets>
        public let placeholderColor: StaticAssignablePropertyDescription<UIColorPropertyType?>
        public let placeholderFont: StaticAssignablePropertyDescription<Font?>

        public required init(configuration: Configuration) {
            textFieldProperties = configuration.namespaced(swiftNamespace: "textField", BaseTextFieldProperties.self)

            placeholder = configuration.property(name: "placeholder")
            contentEdgeInsets = configuration.property(name: "contentEdgeInsets")
            placeholderColor = configuration.property(name: "placeholderColor")
            placeholderFont = configuration.property(name: "placeholderFont")

            super.init(configuration: configuration)
        }
    }
}

extension Module.UIKit {
    public class TextField: View {
        public override class var availableProperties: [PropertyDescription] {
            return Properties.textField.allProperties
        }

        public override class var parentModuleImport: String {
            return "UIKit"
        }

        public class override func runtimeType() -> String {
            return "UITextField"
        }

        public override func supportedActions(context: ComponentContext) throws -> [UIElementAction] {
            return try super.supportedActions(context: context) + ControlEventAction.allEditingEvents
        }

        public override func runtimeType(for platform: RuntimePlatform) throws -> RuntimeType {
            if let runtimeTypeOverride = runtimeTypeOverride {
                return runtimeTypeOverride
            }
            return RuntimeType(name: "UITextField", module: "UIKit")
        }

        #if canImport(UIKit)
        public override func initialize(context: ReactantLiveUIWorker.Context) -> UIView {
            return UITextField()
        }
        #endif
    }

    // HyperTextField has its own placeholder and we need to honor its priority, that's why it's declared here as a lonely boi.
    public class TextFieldProperties: BaseTextFieldProperties {
        public let placeholder: StaticAssignablePropertyDescription<TransformedText?>

        public required init(configuration: PropertyContainer.Configuration) {
            placeholder = configuration.property(name: "placeholder")

            super.init(configuration: configuration)
        }
    }

    public class BaseTextFieldProperties: ControlProperties {
        public let text: StaticAssignablePropertyDescription<TransformedText?>
        public let attributedText: StaticElementAssignablePropertyDescription<Module.Foundation.AttributedText?>
        public let attributedPlaceholder: StaticElementAssignablePropertyDescription<Module.Foundation.AttributedText?>
        public let font: StaticAssignablePropertyDescription<Font?>
        public let textColor: StaticAssignablePropertyDescription<UIColorPropertyType>
        public let textAlignment: StaticAssignablePropertyDescription<TextAlignment>
        public let adjustsFontSizeToWidth: StaticAssignablePropertyDescription<Bool>
        public let minimumFontSize: StaticAssignablePropertyDescription<Float>
        public let clearsOnBeginEditing: StaticAssignablePropertyDescription<Bool>
        public let clearsOnInsertion: StaticAssignablePropertyDescription<Bool>
        public let allowsEditingTextAttributes: StaticAssignablePropertyDescription<Bool>
        public let background: StaticAssignablePropertyDescription<Image?>
        public let disabledBackground: StaticAssignablePropertyDescription<Image?>
        public let borderStyle: StaticAssignablePropertyDescription<TextBorderStyle>
        public let clearButtonMode: StaticAssignablePropertyDescription<TextFieldViewMode>
        public let leftViewMode: StaticAssignablePropertyDescription<TextFieldViewMode>
        public let rightViewMode: StaticAssignablePropertyDescription<TextFieldViewMode>
        public let isSecureTextEntry: StaticAssignablePropertyDescription<Bool>
        public let keyboardType: StaticAssignablePropertyDescription<KeyboardType>
        public let keyboardAppearance: StaticAssignablePropertyDescription<KeyboardAppearance>
        public let contentType: StaticAssignablePropertyDescription<TextContentType?>
        public let returnKey: StaticAssignablePropertyDescription<ReturnKeyType>
        public let enablesReturnKeyAutomatically: StaticAssignablePropertyDescription<Bool>
        public let autocapitalizationType: StaticAssignablePropertyDescription<AutocapitalizationType>
        public let autocorrectionType: StaticAssignablePropertyDescription<AutocorrectionType>
        public let spellCheckingType: StaticAssignablePropertyDescription<SpellCheckingType>
        public let smartQuotesType: StaticAssignablePropertyDescription<SmartQuotesType>
        public let smartDashesType: StaticAssignablePropertyDescription<SmartDashesType>
        public let smartInsertDeleteType: StaticAssignablePropertyDescription<SmartInsertDeleteType>

        public required init(configuration: PropertyContainer.Configuration) {
            text = configuration.property(name: "text", defaultValue: .text(""))
            attributedText = configuration.property(name: "attributedText")
            attributedPlaceholder = configuration.property(name: "attributedPlaceholder")
            font = configuration.property(name: "font")
            textColor = configuration.property(name: "textColor", defaultValue: .black)
            textAlignment = configuration.property(name: "textAlignment", defaultValue: .natural)
            adjustsFontSizeToWidth = configuration.property(name: "adjustsFontSizeToWidth")
            minimumFontSize = configuration.property(name: "minimumFontSize", defaultValue: 0)
            clearsOnBeginEditing = configuration.property(name: "clearsOnBeginEditing")
            clearsOnInsertion = configuration.property(name: "clearsOnInsertion")
            allowsEditingTextAttributes = configuration.property(name: "allowsEditingTextAttributes")
            background = configuration.property(name: "background")
            disabledBackground = configuration.property(name: "disabledBackground")
            borderStyle = configuration.property(name: "borderStyle", defaultValue: .none)
            clearButtonMode = configuration.property(name: "clearButtonMode", defaultValue: .never)
            leftViewMode = configuration.property(name: "leftViewMode", defaultValue: .never)
            rightViewMode = configuration.property(name: "rightViewMode", defaultValue: .never)
            isSecureTextEntry = configuration.property(name: "secure", swiftName: "isSecureTextEntry", key: "secureTextEntry")
            keyboardType = configuration.property(name: "keyboardType", defaultValue: .default)
            keyboardAppearance = configuration.property(name: "keyboardAppearance", defaultValue: .default)
            contentType = configuration.property(name: "contentType", swiftName: "textContentType")
            returnKey = configuration.property(name: "returnKey", swiftName: "returnKeyType", defaultValue: .default)
            enablesReturnKeyAutomatically = configuration.property(name: "enablesReturnKeyAutomatically")
            autocapitalizationType = configuration.property(name: "autocapitalizationType", defaultValue: .sentences)
            autocorrectionType = configuration.property(name: "autocorrectionType", defaultValue: .default)
            spellCheckingType = configuration.property(name: "spellCheckingType", defaultValue: .default)
            smartQuotesType = configuration.property(name: "smartQuotesType", defaultValue: .default)
            smartDashesType = configuration.property(name: "smartDashesType", defaultValue: .default)
            smartInsertDeleteType = configuration.property(name: "smartInsertDeleteType", defaultValue: .default)

            super.init(configuration: configuration)
        }
    }
}
