//
//  StyleDeserializationContext.swift
//  Tokenizer
//
//  Created by Tadeas Kriz on 03/06/2019.
//

public class StyleDeserializationContext: DeserializationContext, HasParentContext {
    public let parentContext: DeserializationContext & HasUIElementFactoryRegistry
    public let element: XMLElement
    public let groupName: String?

    public var platform: RuntimePlatform {
        return parentContext.platform
    }

    public init(parentContext: DeserializationContext & HasUIElementFactoryRegistry, element: XMLElement, groupName: String?) {
        self.parentContext = parentContext
        self.element = element
        self.groupName = groupName
    }
}

extension StyleDeserializationContext: HasUIElementFactoryRegistry {
    public func shouldIgnore(elementName: String) -> Bool {
        return parentContext.shouldIgnore(elementName: elementName)
    }

    public func factory(for elementName: String) -> UIElementFactory? {
        return parentContext.factory(for: elementName)
    }
}

extension StyleDeserializationContext {
    public func child(element: XMLElement) -> StyleDeserializationContext {
        return StyleDeserializationContext(parentContext: self, element: element, groupName: groupName)
    }
}


