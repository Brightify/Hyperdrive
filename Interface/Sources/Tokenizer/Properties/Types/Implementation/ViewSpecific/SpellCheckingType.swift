//
//  SpellCheckingType.swift
//  ReactantUI
//
//  Created by Matouš Hýbl on 15/08/2018.
//

public enum SpellCheckingType: String, EnumPropertyType {
    public static let enumName = "UITextSpellCheckingType"
    public static let typeFactory = EnumTypeFactory<SpellCheckingType>()

    case `default`
    case no
    case yes
}

#if canImport(UIKit)
import UIKit

extension SpellCheckingType {
    public func runtimeValue(context: SupportedPropertyTypeContext) -> Any? {
        switch self {
        case .`default`:
            return UITextSpellCheckingType.default.rawValue
        case .no:
            return UITextSpellCheckingType.no.rawValue
        case .yes:
            return UITextSpellCheckingType.yes.rawValue
        }
    }
}
#endif
