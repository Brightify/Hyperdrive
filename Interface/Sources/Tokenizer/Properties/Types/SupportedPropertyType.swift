//
//  SupportedPropertyType.swift
//  ReactantUI
//
//  Created by Tadeas Kriz.
//  Copyright © 2017 Brightify. All rights reserved.
//

#if canImport(SwiftCodeGen)
import SwiftCodeGen
#endif

public struct RuntimeType: CustomStringConvertible, Equatable {
    public var name: String
    public var modules: Set<String>

    public var description: String {
        return name
    }

    public init(name: String) {
        self.name = name
        self.modules = []
    }

    public init(name: String, module: String) {
        self.name = name
        self.modules = [module]
    }

    public init<S: Sequence>(name: String, modules: S) where S.Element == String {
        self.name = name
        self.modules = Set(modules)
    }

    public static let unsupported = RuntimeType(name: "1$\\'0")
}

public protocol SupportedTypeFactory {
    var xsdType: XSDType { get }

    var isNullable: Bool { get }

    func runtimeType(for platform: RuntimePlatform) -> RuntimeType

    #if canImport(SwiftCodeGen)
    func generate(stateName: String) -> Expression
    #endif
}

public extension SupportedTypeFactory {
    var isNullable: Bool {
        return false
    }

    #if canImport(SwiftCodeGen)
    func generate(stateName: String) -> Expression {
        return .constant(stateName)
    }
    #endif
}

public protocol TypedSupportedTypeFactory: SupportedTypeFactory {
    associatedtype BuildType: TypedSupportedType
}

public enum ValueOrState<T: SupportedPropertyType> {
    case value(T)
    case state(name: String)
}

public protocol SupportedPropertyType {
    var factory: SupportedTypeFactory { get }

    var stateProperties: Set<String> { get }

    func requiresTheme(context: DataContext) -> Bool

    func isOptional(context: SupportedPropertyTypeContext) -> Bool

    #if canImport(SwiftCodeGen)
    func generate(context: SupportedPropertyTypeContext) -> Expression
    #endif

    #if SanAndreas
    func dematerialize(context: SupportedPropertyTypeContext) -> String
    #endif

    #if !GeneratingInterface
    func runtimeValue(context: SupportedPropertyTypeContext) throws -> Any?
    #endif

//    static func runtimeType(for platform: RuntimePlatform) -> RuntimeType
//
//    // FIXME Has to be put into `AttributeSupportedPropertyType`
//    static var xsdType: XSDType { get }
//
//    static var isNullable: Bool { get }
}

public extension SupportedPropertyType {
    var stateProperties: Set<String> {
        return []
    }

    func isOptional(context: SupportedPropertyTypeContext) -> Bool {
        return false
    }

    func requiresTheme(context: DataContext) -> Bool {
        return false
    }
}

public protocol TypedSupportedType: SupportedPropertyType where FactoryType.BuildType == Self {
    associatedtype FactoryType: TypedSupportedTypeFactory

    var typedFactory: FactoryType { get }
}

public extension TypedSupportedType {
    var factory: SupportedTypeFactory {
        return typedFactory
    }
}

public protocol HasZeroArgumentInitializer {
    init()
}

public protocol HasStaticTypeFactory where TypeFactory.BuildType == Self {
    associatedtype TypeFactory: TypedSupportedTypeFactory

    static var typeFactory: TypeFactory { get }
}

public extension SupportedPropertyType where Self: HasStaticTypeFactory {
    var factory: SupportedTypeFactory {
        return Self.typeFactory
    }
}

public extension TypedSupportedType where Self: HasStaticTypeFactory {
    var typedFactory: TypeFactory {
        return Self.typeFactory
    }
}

public protocol HasDefaultValue {
    static var defaultValue: Self { get }
}

extension Optional: TypedSupportedType & SupportedPropertyType & HasStaticTypeFactory & HasDefaultValue where Wrapped: HasStaticTypeFactory {
    public final class TypeFactory: TypedSupportedTypeFactory, HasZeroArgumentInitializer {
        public typealias BuildType = Optional<Wrapped>

        public var xsdType: XSDType {
            return Wrapped.typeFactory.xsdType
        }

        public var isNullable: Bool {
            return true
        }

        public init() { }

        public func runtimeType(for platform: RuntimePlatform) -> RuntimeType {
            let wrappedRuntimeType = Wrapped.typeFactory.runtimeType(for: platform)
            return RuntimeType(name: wrappedRuntimeType.name + "?", modules: wrappedRuntimeType.modules)
        }
    }

    public var stateProperties: Set<String> {
        return self?.stateProperties ?? []
    }

    public static var typeFactory: TypeFactory {
        return TypeFactory()
    }
    public var factory: SupportedTypeFactory {
        return typedFactory
    }
    public var typedFactory: TypeFactory {
        return Optional<Wrapped>.typeFactory
    }

    #if canImport(SwiftCodeGen)
    public func generate(context: SupportedPropertyTypeContext) -> Expression {
        return self?.generate(context: context) ?? .constant("nil")
    }
    #endif

    public static var defaultValue: Optional<Wrapped> {
        return nil
    }

    public func requiresTheme(context: DataContext) -> Bool {
        return self?.requiresTheme(context: context) ?? false
    }

    public func isOptional(context: SupportedPropertyTypeContext) -> Bool {
        return true
    }

    #if !GeneratingInterface
    public func runtimeValue(context: SupportedPropertyTypeContext) throws -> Any? {
        if let wrapped = self {
            return try wrapped.runtimeValue(context: context.child(for: wrapped))
        } else {
            return nil
        }
    }
    #endif
}

public protocol AttributeSupportedTypeFactory: SupportedTypeFactory {
    func materialize(from value: String) throws -> SupportedPropertyType
}

public protocol TypedAttributeSupportedTypeFactory: AttributeSupportedTypeFactory & TypedSupportedTypeFactory {
    func typedMaterialize(from value: String) throws -> BuildType
}

public extension TypedAttributeSupportedTypeFactory {
    public func materialize(from value: String) throws -> SupportedPropertyType {
        return try typedMaterialize(from: value)
    }
}

//public extension AttributeSupportedTypeFactory where Self: TypedSupportedTypeFactory, BuildType: AttributeSupportedPropertyType {
//    func materialize(from value: String) throws -> AttributeSupportedPropertyType {
//        return try BuildType.materialize(from: value)
//    }
//
//    func materialize(from value: String) throws -> BuildType {
//        return try BuildType.materialize(from: value)
//    }
//}

//public protocol AttributeSupportedPropertyType: SupportedPropertyType {
//    static func materialize(from value: String) throws -> Self
//}

//public protocol TypedAttributeSupportedPropertyType: AttributeSupportedPropertyType & TypedSupportedType { }

//public extension AttributeSupportedPropertyType {
//    func requiresTheme(context: DataContext) -> Bool {
//        return false
//    }
//}

//extension Optional: AttributeSupportedPropertyType where Wrapped: AttributeSupportedPropertyType, Wrapped: HasStaticTypeFactory {
//    public static func materialize(from value: String) throws -> Optional<Wrapped> {
//        return try Wrapped.materialize(from: value)
//    }
//}

extension Optional.TypeFactory: AttributeSupportedTypeFactory where Wrapped: HasStaticTypeFactory, Wrapped.TypeFactory: AttributeSupportedTypeFactory {
    public func materialize(from value: String) throws -> SupportedPropertyType {
        return try Wrapped.typeFactory.materialize(from: value)
    }
}

extension Optional.TypeFactory: TypedAttributeSupportedTypeFactory where Wrapped: HasStaticTypeFactory, Wrapped.TypeFactory: TypedAttributeSupportedTypeFactory {
    public func typedMaterialize(from value: String) throws -> Wrapped? {
        return try Wrapped.typeFactory.typedMaterialize(from: value)
    }
}

//extension Optional.TypeFactory: HasZeroArgumentInitializer where Wrapped: HasStaticTypeFactory, Wrapped.TypeFactory: HasZeroArgumentInitializer {
//
//}

public protocol ElementSupportedTypeFactory: SupportedTypeFactory {
    func materialize(from element: XMLElement) throws -> SupportedPropertyType
}

public protocol TypedElementSupportedTypeFactory: ElementSupportedTypeFactory & TypedSupportedTypeFactory {
    func typedMaterialize(from element: XMLElement) throws -> BuildType
}

extension TypedElementSupportedTypeFactory {
    public func materialize(from element: XMLElement) throws -> SupportedPropertyType {
        return try typedMaterialize(from: element)
    }
}

//public extension ElementSupportedTypeFactory where Self: TypedSupportedTypeFactory, BuildType: ElementSupportedPropertyType {
//    func materialize(from element: XMLElement) throws -> ElementSupportedPropertyType {
//        return try BuildType.materialize(from: element)
//    }
//
//    func materialize(from element: XMLElement) throws -> BuildType {
//        return try BuildType.materialize(from: element)
//    }
//}

//public protocol ElementSupportedPropertyType: SupportedPropertyType {
//    static func materialize(from element: XMLElement) throws -> Self
//}

//public extension ElementSupportedPropertyType {
//    func requiresTheme(context: DataContext) -> Bool {
//        return false
//    }
//}

//extension Optional: ElementSupportedPropertyType where Wrapped: ElementSupportedPropertyType, Wrapped: HasStaticTypeFactory {
//    public static func materialize(from element: XMLElement) throws -> Optional<Wrapped> {
//        return try Wrapped.materialize(from: element)
//    }
//}

extension Optional.TypeFactory: ElementSupportedTypeFactory where Wrapped: HasStaticTypeFactory, Wrapped.TypeFactory: ElementSupportedTypeFactory {
    public func materialize(from element: XMLElement) throws -> SupportedPropertyType {
        return try Wrapped.typeFactory.materialize(from: element)
    }
}

extension Optional.TypeFactory: TypedElementSupportedTypeFactory where Wrapped: HasStaticTypeFactory, Wrapped.TypeFactory: TypedElementSupportedTypeFactory {
    public func typedMaterialize(from element: XMLElement) throws -> Wrapped? {
        return try Wrapped.typeFactory.typedMaterialize(from: element)
    }
}

public protocol MultipleAttributeSupportedTypeFactory: SupportedTypeFactory {
    func materialize(from attributes: [String: String]) throws -> SupportedPropertyType
}

public protocol TypedMultipleAttributeSupportedTypeFactory: MultipleAttributeSupportedTypeFactory & TypedSupportedTypeFactory {
    func typedMaterialize(from attributes: [String: String]) throws -> BuildType
}

public extension TypedMultipleAttributeSupportedTypeFactory {
    func materialize(from attributes: [String: String]) throws -> SupportedPropertyType {
        return try typedMaterialize(from: attributes)
    }
}

//public extension MultipleAttributeSupportedTypeFactory where Self: TypedSupportedTypeFactory, BuildType: MultipleAttributeSupportedPropertyType {
//    func materialize(from attributes: [String: String]) throws -> MultipleAttributeSupportedPropertyType {
//        return try BuildType.materialize(from: attributes)
//    }
//
//    func materialize(from attributes: [String: String]) throws -> BuildType {
//        return try BuildType.materialize(from: attributes)
//    }
//}

//public protocol MultipleAttributeSupportedPropertyType: SupportedPropertyType {
//    static func materialize(from attributes: [String: String]) throws -> Self
//}

//public extension MultipleAttributeSupportedPropertyType {
//    func requiresTheme(context: DataContext) -> Bool {
//        return false
//    }
//}

//extension Optional: MultipleAttributeSupportedPropertyType where Wrapped: MultipleAttributeSupportedPropertyType, Wrapped: HasStaticTypeFactory {
//    public static func materialize(from attributes: [String: String]) throws -> Optional<Wrapped> {
//        return try Wrapped.materialize(from: attributes)
//    }
//}

extension Optional.TypeFactory: MultipleAttributeSupportedTypeFactory where Wrapped: HasStaticTypeFactory, Wrapped.TypeFactory: MultipleAttributeSupportedTypeFactory {
    public func materialize(from attributes: [String: String]) throws -> SupportedPropertyType {
        return try Wrapped.typeFactory.materialize(from: attributes)
    }
}

extension Optional.TypeFactory: TypedMultipleAttributeSupportedTypeFactory where Wrapped: HasStaticTypeFactory, Wrapped.TypeFactory: TypedMultipleAttributeSupportedTypeFactory {
    public func typedMaterialize(from attributes: [String: String]) throws -> Wrapped? {
        return try Wrapped.typeFactory.typedMaterialize(from: attributes)
    }
}

//extension ElementSupportedTypeFactory where Self: AttributeSupportedTypeFactory {
//    func materialize(from element: XMLElement) throws -> BuiltType {
//        let text = element.text ?? ""
//        return try materialize(from: text)
//    }
//}

//extension ElementSupportedPropertyType where Self: AttributeSupportedPropertyType {
//    static func materialize(from element: XMLElement) throws -> Self {
//        let text = element.text ?? ""
//        return try materialize(from: text)
//    }
//}
