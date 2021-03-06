//
//  KeyboardType.swift
//  ReactantUI
//
//  Created by Matyáš Kříž on 21/06/2017.
//  Copyright © 2017 Brightify. All rights reserved.
//

public enum TextContentType: String, EnumPropertyType {
    public static let enumName = "UITextContentType"
    public static let typeFactory = EnumTypeFactory<TextContentType>()

    case none
    case URL
    case addressCity
    case addressCityAndState
    case addressState
    case countryName
    case creditCardNumber
    case emailAddress
    case familyName
    case fullStreetAddress
    case givenName
    case jobTitle
    case location
    case middleName
    case name
    case namePrefix
    case nameSuffix
    case nickname
    case organizationName
    case postalCode
    case streetAddressLine1
    case streetAddressLine2
    case sublocality
    case telephoneNumber
    case username
    case password
    case newPassword
}

#if canImport(UIKit)
import UIKit

extension TextContentType {
    public func runtimeValue(context: SupportedPropertyTypeContext) -> Any? {
        if #available(iOS 10.0, tvOS 10.0, *) {
            switch self {
            case .none:
                return nil
            case .URL:
                return UITextContentType.URL
            case .addressCity:
                return UITextContentType.addressCity
            case .addressCityAndState:
                return UITextContentType.addressCityAndState
            case .addressState:
                return UITextContentType.addressState
            case .countryName:
                return UITextContentType.countryName
            case .creditCardNumber:
                return UITextContentType.creditCardNumber
            case .emailAddress:
                return UITextContentType.emailAddress
            case .familyName:
                return UITextContentType.familyName
            case .fullStreetAddress:
                return UITextContentType.fullStreetAddress
            case .givenName:
                return UITextContentType.givenName
            case .jobTitle:
                return UITextContentType.jobTitle
            case .location:
                return UITextContentType.location
            case .middleName:
                return UITextContentType.middleName
            case .name:
                return UITextContentType.name
            case .namePrefix:
                return UITextContentType.namePrefix
            case .nameSuffix:
                return UITextContentType.nameSuffix
            case .nickname:
                return UITextContentType.nickname
            case .organizationName:
                return UITextContentType.organizationName
            case .postalCode:
                return UITextContentType.postalCode
            case .streetAddressLine1:
                return UITextContentType.streetAddressLine1
            case .streetAddressLine2:
                return UITextContentType.streetAddressLine2
            case .sublocality:
                return UITextContentType.sublocality
            case .telephoneNumber:
                return UITextContentType.telephoneNumber
            case .username:
                if #available(iOS 11.0, *) {
                    return UITextContentType.username
                } else {
                    return nil
                }
            case .password:
                if #available(iOS 11.0, *) {
                    return UITextContentType.password
                } else {
                    return nil
                }
            case .newPassword:
                if #available(iOS 12.0, *) {
                    return UITextContentType.newPassword
                } else {
                    return nil
                }
            }
        } else {
            return nil
        }
    }
}
#endif
