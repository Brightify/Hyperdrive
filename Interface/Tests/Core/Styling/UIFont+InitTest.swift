//
//  UIFont+InitTest.swift
//  Reactant
//
//  Created by Filip Dolnik on 18.10.16.
//  Copyright © 2016 Brightify. All rights reserved.
//

import Quick
import Nimble
import HyperdriveInterface

class UIFontInitTest: QuickSpec {
    
    override func spec() {
        describe("UIFont init") {
            it("creates UIFont") {
                let font = UIFont(name: "HelveticaNeue", size: 12)
                
                expect(font?.fontName) == "HelveticaNeue"
                expect(font?.pointSize) == 12
            }
        }
    }
}
