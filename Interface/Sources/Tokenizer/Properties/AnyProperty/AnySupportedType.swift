//
//  AnySupportedType.swift
//  Tokenizer
//
//  Created by Matyáš Kříž on 04/07/2019.
//

#if canImport(SwiftCodeGen)
import SwiftCodeGen
#endif

public struct AnySupportedType: SupportedPropertyType {
    public let factory: SupportedTypeFactory
    public let requiresTheme: Bool
    public let isOptional: Bool

    public func isOptional(context: SupportedPropertyTypeContext) -> Bool {
        return isOptional
    }

    public func requiresTheme(context: DataContext) -> Bool {
        return requiresTheme
    }

    #if canImport(SwiftCodeGen)
    public let generateValue: (SupportedPropertyTypeContext) -> Expression

    public init(factory: SupportedTypeFactory, requiresTheme: Bool = false, isOptional: Bool = false, generateValue: @escaping (SupportedPropertyTypeContext) -> Expression) {
        self.factory = factory
        self.requiresTheme = requiresTheme
        self.isOptional = isOptional
        self.generateValue = generateValue
    }

    public func generate(context: SupportedPropertyTypeContext) -> Expression {
        return generateValue(context)
    }
    #endif

    #if HyperdriveRuntime
    public let resolveValue: (SupportedPropertyTypeContext) throws -> Any?

    public init(factory: SupportedTypeFactory, requiresTheme: Bool = false, isOptional: Bool = false, resolveValue: @escaping (SupportedPropertyTypeContext) throws -> Any?) {
        self.factory = factory
        self.requiresTheme = requiresTheme
        self.isOptional = isOptional
        self.resolveValue = resolveValue
    }
    #endif

    #if SanAndreas
    public func dematerialize(context: SupportedPropertyTypeContext) -> String {
        fatalError("Not implemented")
    }
    #endif

    #if HyperdriveRuntime
    public func runtimeValue(context: SupportedPropertyTypeContext) throws -> Any? {
        return try resolveValue(context)
    }
    #endif
}


