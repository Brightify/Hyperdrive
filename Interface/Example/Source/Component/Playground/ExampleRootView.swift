//
//  ExampleView.swift
//  ReactantUIPrototypeTest
//
//  Created by Tadeas Kriz on 3/28/17.
//  Copyright © 2017 Brightify. All rights reserved.
//

//import Hyperdrive
////import HyperdriveLiveUI
//import RxSwift
//import UIKit
//
//final class ExampleRootView: ViewBase<(test: String, Int, test2: (a: Int, b: String)), Void> {
//    let django = UILabel()
//    let email = UITextField()
//    let send = UIButton()
//    let label = UILabel()
//
//    override func update() {
//        django.attributedText = ReactantTemplates.superTemplate(_name9: "super name", foo: "super foo")
////        send.setAttributedTitle(ReactantTemplates.superTemplate(name: "super name", foo: "super foo"), for: .normal)
////        let b = ReactantTemplates.superTemplate(name: "foo", foo: "lol")
//        // Test that constraints work
////        if let testConstraint = rui.constraints.testConstraint {
////            testConstraint.update(offset: 100)
////        } else {
//////            ReactantLiveUIManager.shared.logError("Either `testConstraint` is missing in the XML or LiveApplier doesn't set it properly!", in: rui.xmlPath)
////        }
//
//
//        /*
//        let state: Any = componentState
//
//        if let test2 = Mirror(reflecting: state).children.first(where: { $0.label == "test2" }) {
//            print(Mirror(reflecting: test2).children.first(where: { $0.label == "b" })?.value)
//        }
//
//        print(Mirror(reflecting: state).children.first?.value)
//        print((state as AnyObject))
//
//        print(state_accessor["test"])
//        print(state_accessor["$1"])
//        print(state_accessor["test2"])
//        print(state_accessor["test2.a"])
//        print(state_accessor["test2.b"])*/
//    }
//}
///*
//protocol StateAccessor {
//    subscript(name: String) -> Any { get }
//}
//
//extension ExampleRootView {
//    var state_accessor: StateAccessor {
//        return ExampleRootView_StateAccessor(self)
//    }
//
//    private class ExampleRootView_StateAccessor: StateAccessor {
//        subscript(name: String) -> Any {
//            get {
//                let state = view.componentState
//                switch name {
//                case "test":
//                    return state.test
//                case "$1":
//                    return state.1
//                case "test2":
//                    return state.test2
//                case "test2.a":
//                    return state.test2.a
//                case "test2.b":
//                    return state.test2.b
//                default:
//                    fatalError("unknown property \(name)")
//                }
//            }
//        }
//
//        private let view: ExampleRootView
//
//        init(_ view: ExampleRootView) {
//            self.view = view
//        }
//    }
//}
//*/
//// FIXME We should put this into ReactantUI
//extension UIButton {
//
//    @objc(setBackgroundColor:forState:)
//    public func setBackgroundColor(_ color: UIColor, for state: UIControl.State) {
//        let rectangle = CGRect(origin: CGPoint.zero, size: CGSize(width: 1, height: 1))
//        UIGraphicsBeginImageContext(rectangle.size)
//
//        let context = UIGraphicsGetCurrentContext()
//        context?.setFillColor(color.cgColor)
//        context?.fill(rectangle)
//
//        let image = UIGraphicsGetImageFromCurrentImageContext()
//        UIGraphicsEndImageContext()
//
//        setBackgroundImage(image!, for: state)
//    }
//}
