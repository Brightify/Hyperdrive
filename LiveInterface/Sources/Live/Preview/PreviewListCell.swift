//
//  PreviewListCell.swift
//  ReactantUI
//
//  Created by Tadeas Kriz on 4/25/17.
//  Copyright © 2017 Brightify. All rights reserved.
//

import HyperdriveInterface
import UIKit

final class PreviewListCell: HyperViewBase, HyperView {
    final class State: HyperViewState {
        fileprivate weak var owner: PreviewListCell? { didSet { resynchronize() } }

        var title: String? { didSet { notifyTitleChanged() } }

        func apply(from otherState: State) {
            title = otherState.title
        }

        func resynchronize() {
            notifyTitleChanged()
        }

        private func notifyTitleChanged() {
            owner?.name.text = title
        }
    }
    enum Action {

    }

    static let triggerReloadPaths: Set<String> = []

    public var state: State = State() {
        willSet { state.owner = nil }
        didSet { state.owner = self }
    }
    let actionPublisher: ActionPublisher<Action>

    private let name = UILabel()

    init(initialState: State = State(), actionPublisher: ActionPublisher<Action> = ActionPublisher()) {
        state = initialState
        self.actionPublisher = actionPublisher

        super.init()

        loadView()

        setupConstraints()

        state.owner = self
    }

    private func loadView() {
        [
            name
        ].forEach(addSubview(_:))

        name.numberOfLines = 0
    }

    private func setupConstraints() {
        name.snp.makeConstraints { make in
            make.leading.equalToSuperview().inset(20)
            make.trailing.equalToSuperview().inset(20)
            make.top.greaterThanOrEqualToSuperview().inset(10)
            make.bottom.lessThanOrEqualToSuperview().inset(10)
            make.centerY.equalToSuperview()
        }
    }
}
