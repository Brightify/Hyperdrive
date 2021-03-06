//
//  Shadow.swift
//  LiveUI-iOS
//
//  Created by Matyáš Kříž on 05/06/2018.
//

#if canImport(SwiftCodeGen)
import SwiftCodeGen
#endif

public struct Shadow: TypedSupportedType, HasStaticTypeFactory {
    public static let typeFactory = TypeFactory()

    public let offset: Size
    public let blurRadius: Float
    public let color: UIColorPropertyType?

    #if canImport(SwiftCodeGen)
    public func generate(context: SupportedPropertyTypeContext) -> Expression {
        let generatedOffset = offset.generate(context: context.child(for: offset))
        let generatedBlurRadius = blurRadius.generate(context: context.child(for: blurRadius))
        let generatedColor = color.map { $0.generate(context: context.child(for: $0)) } ?? .constant("nil")

        return .invoke(target: .constant("NSShadow"), arguments: [
            .init(name: "offset", value: generatedOffset),
            .init(name: "blurRadius", value: generatedBlurRadius),
            .init(name: "color", value: generatedColor),
        ])
    }
    #endif

    #if SanAndreas
    // TODO
    public func dematerialize(context: SupportedPropertyTypeContext) -> String {
        fatalError("Implement me!")
    }
    #endif

    public class TypeFactory: TypedMultipleAttributeSupportedTypeFactory {
        public typealias BuildType = Shadow

        public var xsdType: XSDType {
            return .builtin(.decimal)
        }

        public init() { }

        public func typedMaterialize(from attributes: [String : String]) throws -> Shadow {
            let offset = try attributes["offset"].map(Size.typeFactory.typedMaterialize) ?? Size.zero
            let blurRadius = try attributes["blurRadius"].map(Float.typeFactory.typedMaterialize) ?? 0
            let color = try attributes["color"].map(UIColorPropertyType.typeFactory.typedMaterialize)

            return Shadow(offset: offset, blurRadius: blurRadius, color: color)
        }

        public func runtimeType(for platform: RuntimePlatform) -> RuntimeType {
            return RuntimeType(name: "NSShadow", module: "Foundation")
        }
    }
}

#if canImport(UIKit)
import UIKit

extension Shadow {
    public func runtimeValue(context: SupportedPropertyTypeContext) -> Any? {
        let offsetValue = offset.runtimeValue(context: context.child(for: offset)).flatMap { $0 as? CGSize }
        let colorValue = color.flatMap { $0.runtimeValue(context: context.child(for: $0)) }.flatMap { $0 as? UIColor }

        let shadow = NSShadow()
        if let offset = offsetValue {
            shadow.shadowOffset = offset
        }
        if let color = colorValue {
            shadow.shadowColor = color
        }
        shadow.shadowBlurRadius = CGFloat(blurRadius)

        return shadow
    }
}
#endif
