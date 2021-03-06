//
//  AffineTransformation.swift
//  ReactantUI
//
//  Created by Matyáš Kříž on 23/06/2017.
//
//

#if canImport(UIKit)
import UIKit
#endif

#if canImport(SwiftCodeGen)
import SwiftCodeGen
#endif

public struct AffineTransformation: TypedSupportedType, HasStaticTypeFactory {
    public static let typeFactory = TypeFactory()
    
    public let transformations: [TransformationModifier]
    
    #if canImport(SwiftCodeGen)
    public func generate(context: SupportedPropertyTypeContext) -> Expression {
        let expressions = transformations.map { $0.generated }
        return Expression.join(expressions: expressions, operator: "+") ?? TransformationModifier.identity.generated
    }
    #endif
    
    #if SanAndreas
    public func dematerialize(context: SupportedPropertyTypeContext) -> String {
        return transformations.map { $0.dematerialize() }.joined(separator: " ")
    }
    #endif
    
    #if canImport(UIKit)
    public func runtimeValue(context: SupportedPropertyTypeContext) -> Any? {
        var cgTransform = CGAffineTransform.identity
        for transformation in transformations {
            switch transformation {
            case .identity:
                continue
            case .rotate(by: let degrees):
                cgTransform = cgTransform.rotated(by: CGFloat((.pi/180) * degrees))
            case .scale(byX: let x, byY: let y):
                cgTransform = cgTransform.scaledBy(x: CGFloat(x), y: CGFloat(y))
            case .translate(byX: let x, byY: let y):
                cgTransform = cgTransform.translatedBy(x: CGFloat(x), y: CGFloat(y))
            }
        }
        return cgTransform
    }
    #endif

    public final class TypeFactory: TypedAttributeSupportedTypeFactory, HasZeroArgumentInitializer {
        public typealias BuildType = AffineTransformation

        public var xsdType: XSDType {
            return .builtin(.string)
        }

        public init() { }

        public func typedMaterialize(from value: String) throws -> AffineTransformation {
            let tokens = Lexer.tokenize(input: value)
            let modifiers = try TransformationParser(tokens: tokens).parse()
            return AffineTransformation(transformations: modifiers)
        }

        public func runtimeType(for platform: RuntimePlatform) -> RuntimeType {
            return RuntimeType(name: "CGAffineTransform", module: "CoreGraphics")
        }
    }
}
