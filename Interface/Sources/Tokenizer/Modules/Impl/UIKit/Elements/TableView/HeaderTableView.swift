//
//  HeaderTableView.swift
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
    public class HeaderTableView: View, ComponentDefinitionContainer {
        public override class var availableProperties: [PropertyDescription] {
            return Properties.headerTableView.allProperties
        }

        public override class var availableToolingProperties: [PropertyDescription] {
            return ToolingProperties.headerTableView.allProperties
        }

        public var cellType: String
        public var headerType: String
        public var cellDefinition: ComponentDefinition?
        public var headerDefinition: ComponentDefinition?
        public var options: TableView.TableViewOptions

        public var componentTypes: [String] {
            return (cellDefinition?.componentTypes ?? [cellType].compactMap { $0 }) + (headerDefinition?.componentTypes ?? [headerType].compactMap { $0 })
        }

        public var isAnonymous: Bool {
            return (cellDefinition?.isAnonymous ?? false) || (headerDefinition?.isAnonymous ?? false)
        }

        public var componentDefinitions: [ComponentDefinition] {
            return (cellDefinition?.componentDefinitions ?? []) + (headerDefinition?.componentDefinitions ?? [])
        }

        public override class var parentModuleImport: String {
            return "Hyperdrive"
        }

        public class override func runtimeType() -> String {
            return "ReactantTableView & UITableView"
        }

        public override func runtimeType(for platform: RuntimePlatform) throws -> RuntimeType {
            if let runtimeTypeOverride = runtimeTypeOverride {
                return runtimeTypeOverride
            }
            return RuntimeType(name: "HeaderTableView<\(headerType), \(cellType)>", module: "Hyperdrive")
        }

        #if canImport(SwiftCodeGen)
        public override func initialization(for platform: RuntimePlatform, context: ComponentContext) throws -> Expression {
            return .invoke(target: .constant(try runtimeType(for: platform).name), arguments: [
                MethodArgument(name: "style", value: options.initialization(kind: .style)),
                MethodArgument(name: "options", value: options.initialization(kind: .tableViewOptions)),
                MethodArgument(name: "cellFactory", value: .invoke(target: .constant(cellType), arguments: [])),
                MethodArgument(name: "headerFactory", value: .invoke(target: .constant(headerType), arguments: []))
            ])
        }
        #endif

        public required init(context: UIElementDeserializationContext, factory: UIElementFactory) throws {
            let node = context.element

            guard let cellType = node.value(ofAttribute: "cell") as String? else {
                throw TokenizationError(message: "cell for HeaderTableView was not defined.")
            }
            self.cellType = cellType

            guard let headerType = node.value(ofAttribute: "header") as String? else {
                throw TokenizationError(message: "header for HeaderTableView was not defined.")
            }
            self.headerType = headerType

            if let cellElement = try node.singleOrNoElement(named: "cell") {
                cellDefinition = try context.deserialize(element: cellElement, type: cellType)
            } else {
                cellDefinition = nil
            }

            if let headerElement = try node.singleOrNoElement(named: "header") {
                headerDefinition = try context.deserialize(element: headerElement, type: headerType)
            } else {
                headerDefinition = nil
            }
            
            self.options = try TableView.TableViewOptions(node: node)

            try super.init(context: context, factory: factory)
        }

        public override func serialize(context: DataContext) -> XMLSerializableElement {
            var element = super.serialize(context: context)
            element.attributes.append(XMLSerializableAttribute(name: "cell", value: cellType))
            element.attributes.append(XMLSerializableAttribute(name: "header", value: headerType))

            // FIXME serialize anonymous cell and header
            return element
        }

        #if canImport(UIKit)
        public override func initialize(context: ReactantLiveUIWorker.Context) throws -> UIView {
            let createCell = try context.componentInstantiation(named: cellType)
            let createHeader = try context.componentInstantiation(named: headerType)
            let sectionCount = ToolingProperties.headerTableView.sectionCount.get(from: self.toolingProperties)?.value ?? 5
            let itemCount = ToolingProperties.headerTableView.itemCount.get(from: self.toolingProperties)?.value ?? 5
            let tableView = HyperdriveInterface.HeaderTableView<CellWrapper, CellWrapper>(
                options: [],
                cellFactory: CellWrapper(wrapped: createCell()),
                headerFactory: CellWrapper(wrapped: createHeader()))

            tableView.state.items = .items(Array(repeating: SectionModel(model: EmptyState(), items: Array(repeating: EmptyState(), count: itemCount)), count: sectionCount))

            tableView.tableView.rowHeight = UITableView.automaticDimension
            tableView.tableView.sectionHeaderHeight = UITableView.automaticDimension

            return tableView
        }
        #endif
    }

    public class HeaderTableViewProperites: PropertyContainer {
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


    public class HeaderTableViewToolingProperties: PropertyContainer {
        public let sectionCount: StaticValuePropertyDescription<Int>
        public let itemCount: StaticValuePropertyDescription<Int>

        public required init(configuration: Configuration) {
            sectionCount = configuration.property(name: "tools:sectionCount")
            itemCount = configuration.property(name: "tools:exampleCount")

            super.init(configuration: configuration)
        }
    }
}
