//
//  Layout.swift
//  ReactantUI
//
//  Created by Tadeas Kriz.
//  Copyright © 2017 Brightify. All rights reserved.
//

/**
 * Layout information deserialized from XML element. Contains constraints, compression and hugging priorities as well as the layout ID
 * which is exclusive to ReactantUI.
 * - NOTE: Conditions inside constraints in the layout are evaluated independently on each other.
 */
public struct Layout: XMLElementDeserializable {

    static let nonConstraintables = ["layout:id",
                                     "layout:compressionPriority.vertical",
                                     "layout:compressionPriority.horizontal",
                                     "layout:compressionPriority",
                                     "layout:huggingPriority.vertical",
                                     "layout:huggingPriority.horizontal",
                                     "layout:huggingPriority"]

    public var contentCompressionPriorityHorizontal: ConstraintPriority?
    public var contentCompressionPriorityVertical: ConstraintPriority?
    public var contentHuggingPriorityHorizontal: ConstraintPriority?
    public var contentHuggingPriorityVertical: ConstraintPriority?
    public var constraints: [Constraint]
    public var hasConditions: Bool
    
    init(contentCompressionPriorityHorizontal: ConstraintPriority?,
         contentCompressionPriorityVertical: ConstraintPriority?,
         contentHuggingPriorityHorizontal: ConstraintPriority?,
         contentHuggingPriorityVertical: ConstraintPriority?,
         constraints: [Constraint] = []) {

        self.constraints = constraints
        self.contentCompressionPriorityHorizontal = contentCompressionPriorityHorizontal
        self.contentCompressionPriorityVertical = contentCompressionPriorityVertical
        self.contentHuggingPriorityHorizontal = contentHuggingPriorityHorizontal
        self.contentHuggingPriorityVertical = contentHuggingPriorityVertical
        self.hasConditions = constraints.firstIndex(where: { $0.condition != nil }) != nil
    }

    /**
     * Get all layout information from the passed XML element.
     * - parameter node: XML element to parse
     * - returns: constructed layout information in form of `Layout`
     */
    public static func deserialize(_ node: XMLElement) throws -> Layout {
        let layoutAttributes = node.allAttributes
            .filter { $0.key.hasPrefix("layout:") && !nonConstraintables.contains($0.key) }
            .map { ($0.replacingOccurrences(of: "layout:", with: ""), $1) }

        var contentCompressionPriorityHorizontal: ConstraintPriority?
        var contentCompressionPriorityVertical: ConstraintPriority?
        var contentHuggingPriorityHorizontal: ConstraintPriority?
        var contentHuggingPriorityVertical: ConstraintPriority?

        if let compressionPriority = node.value(ofAttribute: "layout:compressionPriority") as String? {
            let priority = try ConstraintPriority(compressionPriority)
            contentCompressionPriorityHorizontal = priority
            contentCompressionPriorityVertical = priority
        }

        if let verticalCompressionPriority = node.value(ofAttribute: "layout:compressionPriority.vertical") as String? {
            contentCompressionPriorityVertical = try ConstraintPriority(verticalCompressionPriority)
        }

        if let horizontalCompressionPriority = node.value(ofAttribute: "layout:compressionPriority.horizontal") as String? {
            contentCompressionPriorityHorizontal = try ConstraintPriority(horizontalCompressionPriority)
        }

        if let huggingPriority = node.value(ofAttribute: "layout:huggingPriority") as String? {
            let priority = try ConstraintPriority(huggingPriority)
            contentHuggingPriorityHorizontal = priority
            contentHuggingPriorityVertical = priority
        }

        if let verticalHuggingPriority = node.value(ofAttribute: "layout:huggingPriority.vertical") as String? {
            contentHuggingPriorityVertical = try ConstraintPriority(verticalHuggingPriority)
        }

        if let horizontalHuggingPriority = node.value(ofAttribute: "layout:huggingPriority.horizontal") as String? {
            contentHuggingPriorityHorizontal = try ConstraintPriority(horizontalHuggingPriority)
        }

        return try Layout(
            contentCompressionPriorityHorizontal: contentCompressionPriorityHorizontal,
            contentCompressionPriorityVertical: contentCompressionPriorityVertical,
            contentHuggingPriorityHorizontal: contentHuggingPriorityHorizontal,
            contentHuggingPriorityVertical: contentHuggingPriorityVertical,
            constraints: layoutAttributes.flatMap(Constraint.constraints(name:attribute:)))
    }
}
