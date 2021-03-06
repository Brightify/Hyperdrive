//
//  ValuePropertyDescription.swift
//  ReactantUI
//
//  Created by Matous Hybl on 05/09/2017.
//  Copyright © 2017 Brightify. All rights reserved.
//

#if canImport(UIKit)
import UIKit
#endif

public typealias StaticValuePropertyDescription<T> = ValuePropertyDescription<T.TypeFactory> where T: SupportedPropertyType & HasStaticTypeFactory, T.TypeFactory: TypedAttributeSupportedTypeFactory

/**
 * Property description describing a property using a single XML attribute.
 */
public struct ValuePropertyDescription<T: TypedAttributeSupportedTypeFactory>: TypedPropertyDescription {
    public let namespace: [PropertyContainer.Namespace]
    public let name: String
    public let defaultValue: T.BuildType
    public var typeFactory: T

    /**
     * Get a property using the dictionary passed.
     * - parameter properties: **[name: property]** dictionary to search in
     * - returns: found property's value if found, nil otherwise
     */
    public func get(from properties: [String: Property]) -> PropertyValue<T>? {
        let property = getProperty(from: properties)
        return property?.value
    }

    /**
     * Set a property's value from the dictionary passed.
     * A new property is created if no property is found.
     * - parameter value: value to be set to the property
     * - parameter properties: **[name: property]** dictionary to search in
     */
    public func set(value: PropertyValue<T>, to properties: inout [String: Property]) {
        var property: ValueProperty<T>
        if let storedProperty = getProperty(from: properties) {
            property = storedProperty
        } else {
            property = ValueProperty(namespace: namespace, name: name, description: self, value: value)
        }
        property.value = value
        setProperty(property, to: &properties)
    }

    /**
     * Gets a property from the **[name: property]** dictionary passed or nil.
     * - parameter dictionary: properties dictionary
     * - returns: found property or nil
     */
    private func getProperty(from dictionary: [String: Property]) -> ValueProperty<T>? {
        return dictionary[dictionaryKey()] as? ValueProperty<T>
    }

    /**
     * Inserts the property passed into the dictionary of properties.
     * - parameter property: property to insert
     * - parameter dictionary: **[name: property]** dictionary to insert into
     */
    private func setProperty(_ property: Property, to dictionary: inout [String: Property]) {
        dictionary[dictionaryKey()] = property
    }

    private func dictionaryKey() -> String {
        return namespace.resolvedAttributeName(name: name)
    }
}

extension ValuePropertyDescription: AttributePropertyDescription /*where T: AttributeSupportedPropertyType*/ {
    public func materialize(attributeName: String, value: String) throws -> Property {
        let materializedValue: PropertyValue<T>
        if value.starts(with: "$") {
            materializedValue = .state(String(value.dropFirst()), factory: typeFactory)
        } else {
            materializedValue = .value(try typeFactory.typedMaterialize(from: value))
        }

        return ValueProperty(namespace: namespace, name: name, description: self, value: materializedValue)
    }
}

