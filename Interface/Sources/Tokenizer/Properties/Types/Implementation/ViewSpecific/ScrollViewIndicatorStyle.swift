//
//  ScrollViewIndicatorStyle.swift
//  ReactantUI
//
//  Created by Matouš Hýbl on 28/04/2017.
//  Copyright © 2017 Brightify. All rights reserved.
//

public enum ScrollViewIndicatorStyle: String, EnumPropertyType {
    public static let enumName = "UIScrollView.IndicatorStyle"
    public static let typeFactory = EnumTypeFactory<ScrollViewIndicatorStyle>()

    case `default`
    case black
    case white
}

#if canImport(UIKit)
import UIKit

extension ScrollViewIndicatorStyle {
    public func runtimeValue(context: SupportedPropertyTypeContext) -> Any? {
        switch self {
        case .default:
            return UIScrollView.IndicatorStyle.default.rawValue
        case .black:
            return UIScrollView.IndicatorStyle.black.rawValue
        case .white:
            return UIScrollView.IndicatorStyle.white.rawValue
        }
    }
}
#endif
