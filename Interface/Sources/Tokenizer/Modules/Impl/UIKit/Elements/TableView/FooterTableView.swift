//
//  FooterTableView.swift
//  ReactantUI
//
//  Created by Matous Hybl on 06/09/2017.
//  Copyright © 2017 Brightify. All rights reserved.
//

#if canImport(SwiftCodeGen)
import SwiftCodeGen
#endif

#if canImport(UIKit)
import UIKit
import HyperdriveInterface
//import RxDataSources
#endif

extension Module.UIKit {
    public class FooterTableView: View, ComponentDefinitionContainer {
        public override class var availableProperties: [PropertyDescription] {
            return Properties.footerTableView.allProperties
        }
        
        public override class var availableToolingProperties: [PropertyDescription] {
            return ToolingProperties.footerTableView.allProperties
        }
        
        public var cellType: String
        public var footerType: String
        public var cellDefinition: ComponentDefinition?
        public var footerDefinition: ComponentDefinition?
        public var options: TableView.TableViewOptions

        public var componentTypes: [String] {
            return (cellDefinition?.componentTypes ?? [cellType].compactMap { $0 }) + (footerDefinition?.componentTypes ?? [footerType].compactMap { $0 })
        }
        
        public var isAnonymous: Bool {
            return (cellDefinition?.isAnonymous ?? false) || (footerDefinition?.isAnonymous ?? false)
        }
        
        public var componentDefinitions: [ComponentDefinition] {
            return (cellDefinition?.componentDefinitions ?? []) + (footerDefinition?.componentDefinitions ?? [])
        }
        
        public override class var parentModuleImport: String {
            return "Hyperdrive"
        }
        
        public class override func runtimeType() throws -> String {
            return "ReactantTableView & UITableView"
        }
        
        public override func runtimeType(for platform: RuntimePlatform) throws -> RuntimeType {
            if let runtimeTypeOverride = runtimeTypeOverride {
                return runtimeTypeOverride
            }
            return RuntimeType(name: "FooterTableView<\(cellType), \(footerType)>", module: "Hyperdrive")
        }
        
        #if canImport(SwiftCodeGen)
        public override func initialization(for platform: RuntimePlatform, context: ComponentContext) throws -> Expression {
            return .invoke(target: .constant(try runtimeType(for: platform).name), arguments: [
                MethodArgument(name: "style", value: options.initialization(kind: .style)),
                MethodArgument(name: "options", value: options.initialization(kind: .tableViewOptions)),
                MethodArgument(name: "cellFactory", value: .invoke(target: .constant(cellType), arguments: [])),
                MethodArgument(name: "footerFactory", value: .invoke(target: .constant(footerType), arguments: []))
            ])
        }
        #endif
        
        public required init(context: UIElementDeserializationContext, factory: UIElementFactory) throws {
            let node = context.element
            guard let cellType = node.value(ofAttribute: "cell") as String? else {
                throw TokenizationError(message: "cell for FooterTableView was not defined.")
            }
            self.cellType = cellType

            guard let footerType = node.value(ofAttribute: "footer") as String? else {
                throw TokenizationError(message: "Footer for FooterTableView was not defined.")
            }
            self.footerType = footerType
            
            if let cellElement = try node.singleOrNoElement(named: "cell") {
                cellDefinition = try context.deserialize(element: cellElement, type: cellType)
            } else {
                cellDefinition = nil
            }
            
            if let footerElement = try node.singleOrNoElement(named: "footer") {
                footerDefinition = try context.deserialize(element: footerElement, type: footerType)
            } else {
                footerDefinition = nil
            }

            self.options = try TableView.TableViewOptions(node: node)
            
            try super.init(context: context, factory: factory)
        }
        
        public override func serialize(context: DataContext) -> XMLSerializableElement {
            var element = super.serialize(context: context)

            element.attributes.append(XMLSerializableAttribute(name: "cell", value: cellType))
            element.attributes.append(XMLSerializableAttribute(name: "footer", value: footerType))
            
            // FIXME serialize footer and cell definition
            return element
        }
        
        #if canImport(UIKit)
        public override func initialize(context: ReactantLiveUIWorker.Context) throws -> UIView {
            let createCell = try context.componentInstantiation(named: cellType)
            let createFooter = try context.componentInstantiation(named: footerType)
            let sectionCount = ToolingProperties.footerTableView.sectionCount.get(from: self.toolingProperties)?.value ?? 5
            let itemCount = ToolingProperties.footerTableView.itemCount.get(from: self.toolingProperties)?.value ?? 5
            let tableView = HyperdriveInterface.FooterTableView<CellWrapper, CellWrapper>(
                options: [],
                cellFactory: CellWrapper(wrapped: createCell()),
                footerFactory: CellWrapper(wrapped: createFooter()))

            tableView.state.items = .items(Array(repeating: SectionModel(model: EmptyState(), items: Array(repeating: EmptyState(), count: itemCount)), count: sectionCount))
            
            tableView.tableView.rowHeight = UITableView.automaticDimension
            tableView.tableView.sectionFooterHeight = UITableView.automaticDimension
            
            return tableView
        }
        #endif
    }
    
    public class FooterTableViewProperites: PropertyContainer {
        public let tableViewProperties: TableViewProperties
        public let emptyLabelProperties: LabelProperties
        public let loadingIndicatorProperties: ActivityIndicatorProperties
        
        public required init(configuration: Configuration) {
            tableViewProperties = configuration.namespaced(in: "tableView", TableViewProperties.self)
            emptyLabelProperties = configuration.namespaced(in: "emptyLabel", LabelProperties.self)
            loadingIndicatorProperties = configuration.namespaced(in: "loadingIndicator", ActivityIndicatorProperties.self)
            
            super.init(configuration: configuration)
        }
    }
    
    public class FooterTableViewToolingProperties: PropertyContainer {
        public let sectionCount: StaticValuePropertyDescription<Int>
        public let itemCount: StaticValuePropertyDescription<Int>
        
        public required init(configuration: Configuration) {
            sectionCount = configuration.property(name: "tools:sectionCount")
            itemCount = configuration.property(name: "tools:exampleCount")
            
            super.init(configuration: configuration)
        }
    }
}
