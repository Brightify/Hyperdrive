//
//  ElementAssignablePropertyDescription.swift
//  Tokenizer
//
//  Created by Matyáš Kříž on 04/06/2018.
//

public typealias StaticElementAssignablePropertyDescription<T> = ElementAssignablePropertyDescription<T.TypeFactory> where T: SupportedPropertyType & HasStaticTypeFactory, T.TypeFactory: TypedElementSupportedTypeFactory

/**
 * Property description describing a property using a single XML element.
 */
public struct ElementAssignablePropertyDescription<T: TypedElementSupportedTypeFactory>: TypedPropertyDescription {
    public let namespace: [PropertyContainer.Namespace]
    public let name: String
    public let swiftName: String
    public let key: String
    public let defaultValue: T.BuildType
    public let typeFactory: T

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
        var property: ElementAssignableProperty<T>
        if let storedProperty = getProperty(from: properties) {
            property = storedProperty
        } else {
            property = ElementAssignableProperty(namespace: namespace, name: name, description: self, value: value)
        }
        property.value = value
        setProperty(property, to: &properties)
    }

    /**
     * Gets a property from the **[name: property]** dictionary passed or nil.
     * - parameter dictionary: properties dictionary
     * - returns: found property or nil
     */
    private func getProperty(from dictionary: [String: Property]) -> ElementAssignableProperty<T>? {
        return dictionary[dictionaryKey()] as? ElementAssignableProperty<T>
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

import Foundation

extension ElementAssignablePropertyDescription: ElementPropertyDescription where T: TypedElementSupportedTypeFactory {
    public func materialize(element: XMLElement) throws -> Property {
        let materializedValue: PropertyValue<T>
        if let elementText = element.text, elementText.starts(with: "$"), !elementText.contains(" ") {
            materializedValue = .state(String(elementText.dropFirst()), factory: typeFactory)
        } else {
            materializedValue = .value(try typeFactory.typedMaterialize(from: element))
        }

        return ElementAssignableProperty(namespace: namespace, name: name, description: self, value: materializedValue)
    }
}
