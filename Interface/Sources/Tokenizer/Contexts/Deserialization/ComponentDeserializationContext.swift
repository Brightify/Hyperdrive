//
//  ComponentDeserializationContext.swift
//  Tokenizer
//
//  Created by Tadeas Kriz on 03/06/2019.
//

public class ComponentDeserializationContext: DeserializationContext, HasParentContext, HasElementIdProvider {
    public let parentContext: DeserializationContext & HasUIElementFactoryRegistry & CanDeserializeDefinition
    public let type: String
    public let element: XMLElement
    public let elementIdProvider: ElementIdProvider

    public var platform: RuntimePlatform {
        return parentContext.platform
    }

    public init(parentContext: DeserializationContext & HasUIElementFactoryRegistry & CanDeserializeDefinition, element: XMLElement, type: String, elementIdProvider: ElementIdProvider) {
        self.parentContext = parentContext
        self.type = type
        self.element = element
        self.elementIdProvider = elementIdProvider
    }
}

extension ComponentDeserializationContext: CanDeserializeUIElement {
    public func deserialize(element: XMLElement) throws -> UIElement? {
        if let factory = parentContext.factory(for: element.name) {
            return try factory.create(context: child(element: element))
        } else {
            return nil
        }
    }

    private func child(element: XMLElement) -> UIElementDeserializationContext {
        return UIElementDeserializationContext(parentContext: self, element: element, elementIdProvider: elementIdProvider)
    }
}

extension ComponentDeserializationContext: HasUIElementFactoryRegistry {
    public func shouldIgnore(elementName: String) -> Bool {
        return parentContext.shouldIgnore(elementName: elementName)
    }

    public func factory(for elementName: String) -> UIElementFactory? {
        return parentContext.factory(for: elementName)
    }
}

extension ComponentDeserializationContext: CanDeserializeDefinition {
    public func deserialize(element: XMLElement, type: String) throws -> ComponentDefinition {
        return try parentContext.deserialize(element: element, type: type)
    }
}

extension ComponentDeserializationContext: CanDeserializeStyleElement {
    public func deserialize(element: XMLElement, groupName: String?) throws -> LazyStyle {
        return try LazyStyle(context: child(element: element, groupName: groupName))
    }

    private func child(element: XMLElement, groupName: String?) -> StyleDeserializationContext {
        return StyleDeserializationContext(parentContext: self, element: element, groupName: groupName)
    }
}
