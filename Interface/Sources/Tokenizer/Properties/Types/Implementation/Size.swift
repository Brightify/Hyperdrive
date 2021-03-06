//
//  Size.swift
//  Hyperdrive
//
//  Created by Matouš Hýbl on 23/04/2017.
//  Copyright © 2017 Brightify. All rights reserved.
//

import Foundation

#if canImport(SwiftCodeGen)
import SwiftCodeGen
#endif

public struct Size: TypedSupportedType, HasStaticTypeFactory {
    public static let zero = Size(width: 0, height: 0)
    public static let typeFactory = TypeFactory()

    public let width: Double
    public let height: Double

    public init(width: Double, height: Double) {
        self.width = width
        self.height = height
    }

    #if canImport(SwiftCodeGen)
    public func generate(context: SupportedPropertyTypeContext) -> Expression {
        return .constant("CGSize(width: \(CGFloat(width)), height: \(CGFloat(height)))")
    }
    #endif
    
    #if SanAndreas
    public func dematerialize(context: SupportedPropertyTypeContext) -> String {
        return "width: \(width), height: \(height)"
    }
    #endif

    public final class TypeFactory: TypedAttributeSupportedTypeFactory, HasZeroArgumentInitializer {
        public typealias BuildType = Size

        public var xsdType: XSDType {
            return .builtin(.decimal)
        }

        public init() { }

        public func typedMaterialize(from value: String) throws -> Size {
            let dimensions = try DimensionParser(tokens: Lexer.tokenize(input: value)).parse()
            if let singleDimension = dimensions.first, dimensions.count == 1 {
                return Size(width: singleDimension.value, height: singleDimension.value)
            } else if dimensions.count == 2 {
                let width = (dimensions.first(where: { $0.identifier == "width" }) ?? dimensions[0]).value
                let height = (dimensions.first(where: { $0.identifier == "height" }) ?? dimensions[1]).value
                return Size(width: width, height: height)
            } else {
                throw PropertyMaterializationError.unknownValue(value)
            }
        }

        public func runtimeType(for platform: RuntimePlatform) -> RuntimeType {
            return RuntimeType(name: "CGSize", module: "CoreGraphics")
        }
    }
}

#if canImport(UIKit)
import UIKit

extension Size {
    public func runtimeValue(context: SupportedPropertyTypeContext) -> Any? {
        return CGSize(width: width.cgFloat, height: height.cgFloat)
    }
}
#endif
