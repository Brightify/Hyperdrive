//
//  XSDResolver.swift
//  reactant-ui
//
//  Created by Matouš Hýbl on 21/03/2018.
//

import Tokenizer

class XSDResolver {
    private var file = XSDFile()

    func resolve(factories: [String: UIElementFactory]) -> XSDFile {
        var viewGroup = XSDGroup(name: "viewGroup", elements: [])
        var stylesType = XSDComplexChoiceType(name: "stylesType", elements: [])

        for (name, elementFactory) in factories {
            let xsdElement = resolve(elementFactory: elementFactory, named: name)
            viewGroup.elements.insert(xsdElement)
            stylesType.elements.insert(XSDElement(name: name + "Style",
                                                  isContainer: false,
                                                  attributeGroups: attributeGroupsForStyles(attributeGroups: xsdElement.attributeGroups)))
        }

        file.groups.insert(viewGroup)

        file.complexTypes.insert(stylesType)

        file.simpleTypes.insert(XSDSimpleType(name: "rectEdge", type: RectEdge.xsdType))

        var styleAttributes = XSDAttributeGroup(name: "styleAttributes", attributes: [])
        styleAttributes.attributes.insert(XSDAttribute(name: "name", typeName: BuiltinXSDType.string.xsdName))
        styleAttributes.attributes.insert(XSDAttribute(name: "extend", typeName: BuiltinXSDType.string.xsdName))

        file.attributeGroups.insert(styleAttributes)

        return file
    }

    private func resolve(elementFactory: UIElementFactory, named name: String) -> XSDElement {
        var xsdElement = XSDElement(name: name, isContainer: elementFactory.isContainer, attributeGroups: [])
        var attributes = XSDAttributeGroup(name: name + "Attributes", attributes: [])
        xsdElement.attributeGroups.insert(name + "Attributes")

        for property in elementFactory.availableProperties {
            let propertyName = property.namespace.resolvedAttributeName(name: property.name)
            let typeName: String
            switch property.anyTypeFactory.xsdType {
            case .builtin(let builtin):
                typeName = builtin.xsdName
            case .enumeration(let enumeration):
                typeName = enumeration.name
                let type = XSDSimpleType(name: typeName, type: property.anyTypeFactory.xsdType)

                file.simpleTypes.insert(type)
            case .pattern(let pattern):
                typeName = pattern.name
                let type = XSDSimpleType(name: typeName, type: property.anyTypeFactory.xsdType)

                file.simpleTypes.insert(type)
            case .union(let union):
                typeName = union.name
                let type = XSDSimpleType(name: typeName, type: property.anyTypeFactory.xsdType)

                for type in union.memberTypes {
                    // We don't want to create a simple type for builtin types
                    if case .builtin = type {
                        continue
                    } else {
                        let typeName = type.name
                        let type = XSDSimpleType(name: typeName, type: type)
                        file.simpleTypes.insert(type)
                    }
                }

                file.simpleTypes.insert(type)
            }

            if property is ControlStatePropertyDescriptionMarker {
                var variations: Set<Set<ControlState>> = []
                for variationClass in 1..<ControlState.allCases.count {
                    variations.formUnion(ControlState.allCases.variations(class: variationClass).map(Set.init))
                }

                for variation in variations {
                    attributes.attributes.insert(XSDAttribute(name: "\(propertyName).\(variation.name)", typeName: typeName))
                }
            }

            attributes.attributes.insert(XSDAttribute(name: propertyName, typeName: typeName))
        }

        #warning("FIXME: Add the element name in this case")
//        if element is ComponentReference.Type {
//            attributes.attributes.insert(XSDAttribute(name: "type", typeName: BuiltinXSDType.string.xsdName))
//            // FIXME figure out how anonymous components are handled
//        }

        xsdElement.attributeGroups.insert("layout:layoutAttributes")

        attributes.attributes.insert(XSDAttribute(name: "field", typeName: BuiltinXSDType.string.xsdName))
        attributes.attributes.insert(XSDAttribute(name: "style", typeName: BuiltinXSDType.string.xsdName))

        file.attributeGroups.insert(attributes)

        return xsdElement
    }

    private func attributeGroupsForStyles(attributeGroups: Set<String>) -> Set<String> {
        var newAttributeGroups = attributeGroups
        newAttributeGroups.insert("styleAttributes")
        newAttributeGroups.remove("layout:layoutAttributes")
        return newAttributeGroups
    }
    
}
