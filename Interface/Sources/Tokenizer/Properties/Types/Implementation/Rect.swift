//
//  Rect.swift
//  Hyperdrive
//
//  Created by Matouš Hýbl on 23/04/2017.
//  Copyright © 2017 Brightify. All rights reserved.
//

#if canImport(UIKit)
import UIKit
#endif

#if canImport(SwiftCodeGen)
import SwiftCodeGen
#endif

public struct Rect: TypedSupportedType, HasStaticTypeFactory {
    public static let zero = Rect()
    public static let typeFactory = TypeFactory()

    public let origin: Point
    public let size: Size

    #if canImport(SwiftCodeGen)
    public func generate(context: SupportedPropertyTypeContext) -> Expression {
        return .constant("CGRect(origin: CGPoint(x: \(origin.x.cgFloat), y: \(origin.y.cgFloat)), size: CGSize(width: \(size.width.cgFloat), height: \(size.height.cgFloat)))")
    }
    #endif

    public init(origin: Point, size: Size) {
        self.origin = origin
        self.size = size
    }

    public init(x: Double = 0, y: Double = 0, width: Double = 0, height: Double = 0) {
        self.init(origin: Point(x: x, y: y), size: Size(width: width, height: height))
    }
    
    #if SanAndreas
    public func dematerialize(context: SupportedPropertyTypeContext) -> String {
        return "x: \(origin.x), y: \(origin.y), width: \(size.width), height: \(size.height)"
    }
    #endif
}

extension Rect {
    public final class TypeFactory: TypedAttributeSupportedTypeFactory, HasZeroArgumentInitializer {
        public typealias BuildType = Rect

        public var xsdType: XSDType {
            return .builtin(.string)
        }

        public init() { }

        public func typedMaterialize(from value: String) throws -> Rect {
            let dimensions = try DimensionParser(tokens: Lexer.tokenize(input: value)).parse()
            guard dimensions.count == 4 else {
                throw PropertyMaterializationError.unknownValue(value)
            }
            let x = (dimensions.first(where: { $0.identifier == "x" }) ?? dimensions[0]).value
            let y = (dimensions.first(where: { $0.identifier == "y" }) ?? dimensions[1]).value
            let width = (dimensions.first(where: { $0.identifier == "width" }) ?? dimensions[2]).value
            let height = (dimensions.first(where: { $0.identifier == "height" }) ?? dimensions[3]).value
            return Rect(x: x, y: y, width: width, height: height)
        }

        public func runtimeType(for platform: RuntimePlatform) -> RuntimeType {
            return RuntimeType(name: "CGRect", module: "CoreGraphics")
        }
    }
}

#if canImport(UIKit)
extension Rect {
    public func runtimeValue(context: SupportedPropertyTypeContext) -> Any? {
        let origin = CGPoint(x: self.origin.x.cgFloat, y: self.origin.y.cgFloat)
        let size = CGSize(width: self.size.width.cgFloat, height: self.size.height.cgFloat)
        return CGRect(origin: origin, size: size)
    }
}
#endif
