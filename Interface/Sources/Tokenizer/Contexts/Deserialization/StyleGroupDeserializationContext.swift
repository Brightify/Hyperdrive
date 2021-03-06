//
//  StyleGroupDeserializationContext.swift
//  Tokenizer
//
//  Created by Tadeas Kriz on 03/06/2019.
//

public class StyleGroupDeserializationContext: DeserializationContext, HasParentContext {
    public let parentContext: DeserializationContext & HasUIElementFactoryRegistry
    public let element: XMLElement

    public var platform: RuntimePlatform {
        return parentContext.platform
    }

    public init(parentContext: DeserializationContext & HasUIElementFactoryRegistry, element: XMLElement) {
        self.parentContext = parentContext
        self.element = element
    }
}

extension StyleGroupDeserializationContext: HasUIElementFactoryRegistry {
    public func shouldIgnore(elementName: String) -> Bool {
        return parentContext.shouldIgnore(elementName: elementName)
    }

    public func factory(for elementName: String) -> UIElementFactory? {
        return parentContext.factory(for: elementName)
    }
}

extension StyleGroupDeserializationContext: CanDeserializeStyleElement {
    public func deserialize(element: XMLElement, groupName: String?) throws -> LazyStyle {
        return try LazyStyle(context: child(element: element, groupName: groupName))
    }

    private func child(element: XMLElement, groupName: String?) -> StyleDeserializationContext {
        return StyleDeserializationContext(parentContext: self, element: element, groupName: groupName)
    }
}
