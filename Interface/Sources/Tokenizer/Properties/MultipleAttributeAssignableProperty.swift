//
//  MultipleAttributeAssignableProperty.swift
//  LiveUI-iOS
//
//  Created by Matyáš Kříž on 05/06/2018.
//

#if canImport(UIKit)
import UIKit
#endif

#if canImport(SwiftCodeGen)
import SwiftCodeGen
#endif

/**
 * Typed property that is obtained from multiple XML attributes.
 * Its main usage is for `struct`s that need multiple `init` parameters.
 */
public struct MultipleAttributeAssignableProperty<T: TypedMultipleAttributeSupportedTypeFactory>: TypedProperty {
    public var namespace: [PropertyContainer.Namespace]
    public var name: String
    public var description: MultipleAttributeAssignablePropertyDescription<T>
    public var value: PropertyValue<T>

    public var attributeName: String {
        return namespace.resolvedAttributeName(name: name)
    }

    #if canImport(SwiftCodeGen)
    /**
     * - parameter context: property context to use
     * - returns: Swift `String` representation of the property application on the target
     */
    public func application(context: PropertyContext) -> Expression {
        return value.generate(context: context.child(for: value))
    }

    /**
     * - parameter target: UI element to be targetted with the property
     * - parameter context: property context to use
     * - returns: Swift `String` representation of the property application on the target
     */
    public func application(on target: String, context: PropertyContext) -> Statement {
        let namespacedTarget = namespace.resolvedSwiftName(target: target)

        return .assignment(target: .member(target: .constant(namespacedTarget), name: description.swiftName), expression: application(context: context))
    }
    #endif

    #if SanAndreas
    public func dematerialize(context: PropertyContext) -> XMLSerializableAttribute {
        return XMLSerializableAttribute(name: attributeName, value: value.dematerialize(context: context.child(for: value)))
    }
    #endif

    #if canImport(UIKit)
    /**
     * Try to apply the property on an object using the passed property context.
     * - parameter object: UI element to apply the property to
     * - parameter context: property context to use
     */
    public func apply(on object: AnyObject, context: PropertyContext) throws {
        let key = description.key
        let selector = Selector("set\(key.capitalizingFirstLetter()):")

        let target = try resolveTarget(for: object)

        guard target.responds(to: selector) else {
            throw LiveUIError(message: "!! Object `\(target)` doesn't respond to selector `\(key)` to set value `\(value)`")
        }
        guard let resolvedValue = try value.runtimeValue(context: context.child(for: value)) else {
            throw LiveUIError(message: "!! Value `\(value)` couldn't be resolved in runtime for key `\(key)`")
        }

        do {
            try catchException {
                _ = target.setValue(resolvedValue, forKey: key)
            }
        } catch {
            _ = target.perform(selector, with: resolvedValue)
        }

    }

    private func resolveTarget(for object: AnyObject) throws -> AnyObject {
        if namespace.isEmpty {
            return object
        } else {
            let keyPath = namespace.resolvedKeyPath
            guard let target = object.value(forKeyPath: keyPath) else {
                throw LiveUIError(message: "!! Object \(object) doesn't have keyPath \(keyPath) to resolve real target")
            }
            return target as AnyObject
        }
    }
    #endif
}
