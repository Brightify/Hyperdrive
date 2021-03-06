//
//  ReactantLiveUIConfiguration.swift
//  ReactantUI
//
//  Created by Tadeas Kriz on 4/25/17.
//  Copyright © 2017 Brightify. All rights reserved.
//

import UIKit
import HyperdriveInterface

public protocol ReactantLiveUIConfiguration {
    var scanDirs: [String] { get }
    var applicationDescriptionPath: String? { get }
    var componentTypes: [String: (HyperViewBase.Type, () -> HyperViewBase)] { get }
    var commonStylePaths: [String] { get }
    var resourceBundle: Bundle { get }
}
