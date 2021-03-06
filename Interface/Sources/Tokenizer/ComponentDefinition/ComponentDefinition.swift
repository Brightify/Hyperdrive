//
//  Element+Root.swift
//  ReactantUI
//
//  Created by Matous Hybl.
//  Copyright © 2017 Brightify. All rights reserved.
//

#if canImport(UIKit)
import UIKit
#endif

/**
 * Contains the structure of a Component's file.
 */
public struct ComponentDefinition: UIContainer, UIElementBase, StyleContainer, ComponentDefinitionContainer {
    public var type: String
    public var isRootView: Bool
    public var styles: [LazyStyle]
    public var stylesName: String
    public var templates: [Template]
    public var templatesName: String
    public var children: [UIElement]
    public var edgesForExtendedLayout: [RectEdge]
    public var isAnonymous: Bool
    public var modifier: AccessModifier
    public var isFinal: Bool
    public var handledActions: [HyperViewAction]
    public var properties: [Property]
    public var toolingProperties: [String: Property]
    public var overrides: [Override]
    public var stateDescription: StateDescription
    public var navigationItem: NavigationItem?
    public var rxDisposeBags: DisposeBagDescription

    public static var parentModuleImport: String {
        return "Hyperdrive"
    }

    public var requiredImports: Set<String> {
        let imports = Set(arrayLiteral: "Hyperdrive").union(children.flatMap { $0.requiredImports })
        if rxDisposeBags.items.isEmpty {
            return imports
        } else {
            return imports.union(["RxSwift"])
        }
    }

    public var componentTypes: [String] {
        return [type] + ComponentDefinition.componentTypes(in: children)
    }

    public var componentDefinitions: [ComponentDefinition] {
        return [self] + ComponentDefinition.componentDefinitions(in: children)
    }

    public var addSubviewMethod: String {
        return "addSubview"
    }

    #if canImport(UIKit)
    /**
     * **[LiveUI]** Adds a `UIView` to the passed self.
     * - parameter subview: view to be added as a subview
     * - parameter toInstanceOfSelf: parent to which the view should be added
     */
    public func add(subview: UIView, toInstanceOfSelf: UIView) {
        toInstanceOfSelf.addSubview(subview)
    }
    #endif

    public init(context: ComponentDeserializationContext) throws {
        let node = context.element
        type = context.type
        styles = try node.singleOrNoElement(named: "styles")?.xmlChildren.compactMap { try context.deserialize(element: $0, groupName: nil) } ?? []
        stylesName = try node.singleOrNoElement(named: "styles")?.attribute(by: "name")?.text ?? "Styles"
        templates = try node.singleOrNoElement(named: "templates")?.xmlChildren.compactMap { try $0.value() as Template } ?? []
        templatesName = try node.singleOrNoElement(named: "templates")?.attribute(by: "name")?.text ?? "Templates"
        children = try node.xmlChildren.compactMap(context.deserialize(element:))
        isRootView = node.value(ofAttribute: "rootView") ?? false
        if isRootView {
            edgesForExtendedLayout = (node.attribute(by: "extend")?.text).map(RectEdge.parse) ?? []
        } else {
            if node.attribute(by: "extend") != nil {
                Logger.instance.warning("Using `extend` without specifying `rootView=true` is redundant.")
            }
            edgesForExtendedLayout = []
        }
        isAnonymous = node.value(ofAttribute: "anonymous") ?? false
        if let modifier = node.value(ofAttribute: "accessModifier") as String? {
            self.modifier = AccessModifier(rawValue: modifier) ?? .internal
        } else {
            self.modifier = .internal
        }
        isFinal = try node.value(ofAttribute: "final", defaultValue: true)
        handledActions = try node.allAttributes.compactMap { _, value in
            try HyperViewAction(attribute: value)
        }

        let toolingPropertyDescriptions: [PropertyDescription]
        let propertyDescriptions: [PropertyDescription]
        switch context.platform {
        case .iOS, .tvOS:
            toolingPropertyDescriptions = Module.UIKit.ToolingProperties.componentDefinition.allProperties
            propertyDescriptions = Module.UIKit.View.availableProperties
        case .macOS:
            toolingPropertyDescriptions = Module.AppKit.ToolingProperties.componentDefinition.allProperties
            propertyDescriptions = Module.AppKit.View.availableProperties
        }
        toolingProperties = try PropertyHelper.deserializeToolingProperties(properties: toolingPropertyDescriptions, in: node)
        properties = try PropertyHelper.deserializeSupportedProperties(properties: propertyDescriptions, in: node)
        overrides = try node.singleOrNoElement(named: "overrides")?.allAttributes.values.map(Override.init) ?? []
        stateDescription = try StateDescription(element: node.singleOrNoElement(named: "state"))
        navigationItem = try node.singleOrNoElement(named: "navigationItem").map(NavigationItem.init(element:))
        rxDisposeBags = try DisposeBagDescription(element: node.singleOrNoElement(named: "rx:disposeBags"))

        handledActions.append(contentsOf: navigationItem?.allItems.map { item in
            HyperViewAction(name: item.id, eventName: BarButtonItemTapAction.primaryName(for: item), parameters: [])
        } ?? [])

        // here we gather all the constraints' fields that do not have a condition and check if any are duplicate
        // in that case we warn the user about it, because it's probably not what they intended
        let fields = children.flatMap { $0.layout.constraints.compactMap { return $0.condition == nil ? $0.field : nil } }.sorted()
        for (index, field) in fields.enumerated() {
            let nextIndex = index + 1
            guard nextIndex < fields.count else { break }
            if field == fields[nextIndex] {
                Logger.instance.warning("Duplicate constraint names for name \"\(field)\". The project will be compilable, but the behavior might be unexpected.")
            }
        }
    }
}

extension ComponentDefinition {
    public func supportedActions(context: ComponentContext) throws -> [UIElementAction] {
//        let resolvedActions = try context.resolve(actions: providedActions)
//
//        let actions = resolvedActions.map { action in
//            ComponentDefinitionAction(action: action)
//        }
        let navigationItemActions = navigationItem?.allItems.map(BarButtonItemTapAction.init) ?? []
        return [
            ViewTapAction(),
        ] + navigationItemActions
    }
}

extension ComponentDefinition {
    static func componentTypes(in elements: [UIElement]) -> [String] {
        return elements.flatMap { element -> [String] in
            switch element {
            case let container as ComponentDefinitionContainer:
                return container.componentTypes
            case let container as UIContainer:
                return componentTypes(in: container.children)
            default:
                return []
            }
        }
    }

    static func componentDefinitions(in elements: [UIElement]) -> [ComponentDefinition] {
        return elements.flatMap { element -> [ComponentDefinition] in
            switch element {
            case let container as ComponentDefinitionContainer:
                return container.componentDefinitions
            case let container as UIContainer:
                return componentDefinitions(in: container.children)
            default:
                return []
            }
        }
    }
}

public final class ComponentDefinitionToolingProperties: PropertyContainer {
    public let preferredSize: StaticValuePropertyDescription<PreferredSize>

    public required init(configuration: Configuration) {
        preferredSize = configuration.property(name: "tools:preferredSize", defaultValue: PreferredSize(width: .fill, height: .wrap))
        super.init(configuration: configuration)
    }
}
