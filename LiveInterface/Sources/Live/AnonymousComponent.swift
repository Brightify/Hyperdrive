//
//  AnonymousComponent.swift
//  ReactantUI
//
//  Created by Tadeas Kriz on 4/25/17.
//  Copyright © 2017 Brightify. All rights reserved.
//

import UIKit
import HyperdriveInterface

internal typealias AnonymousComponent = AnonymousLiveComponent

public protocol Anonymous {}

public class AnonymousLiveComponent: LiveHyperViewBase, Anonymous {
    fileprivate var _properties: [String: Any] = [:]
    fileprivate var _selectionStyle: UITableViewCell.SelectionStyle = .default
    fileprivate var _focusStyle: UITableViewCell.FocusStyle = .default

    public override func stateProperty(named name: String) -> LiveKeyPath? {
        return LiveKeyPath(
            getValue: {
                self._properties["$\(name)"]
            },
            setValue: { newValue in
                self._properties["$\(name)"] = newValue
            })
    }

    public override func conforms(to aProtocol: Protocol) -> Bool {
        return super.conforms(to: aProtocol)
    }

    public override func value(forUndefinedKey key: String) -> Any? {
        return _properties[key]
    }

    public override func setValue(_ value: Any?, forUndefinedKey key: String) {
        _properties[key] = value
    }

    public override var description: String {
        return "AnonymousComponent: \(typeName)"
    }

    public override var debugDescription: String {
        return description
    }
}

extension AnonymousComponent: TableViewCell {
    public var selectionStyle: UITableViewCell.SelectionStyle {
        get {
            return _selectionStyle
        }
        set {
            _selectionStyle = newValue
        }
    }

    public var focusStyle: UITableViewCell.FocusStyle {
        get {
            return _focusStyle
        }
        set {
            _focusStyle = newValue
        }
    }
}
