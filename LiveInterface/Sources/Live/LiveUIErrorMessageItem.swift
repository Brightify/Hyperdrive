//
//  LiveUIErrorMessageItem.swift
//  ReactantUI
//
//  Created by Tadeas Kriz.
//  Copyright © 2017 Brightify. All rights reserved.
//

import HyperdriveInterface
import UIKit

final class LiveUIErrorMessageItem: HyperViewBase, HyperView {

    final class State: HyperViewState {
        fileprivate weak var owner: LiveUIErrorMessageItem? { didSet { resynchronize() } }

        var file: String? { didSet { notifyFileChanged() } }
        var message: String? { didSet { notifyMessageChanged() } }

        init() {

        }

        func apply(from otherState: State) {
            file = otherState.file
            message = otherState.message
        }

        func resynchronize() {
            notifyFileChanged()
            notifyMessageChanged()
        }

        private func notifyFileChanged() {
            owner?.path.text = "in: \(file ?? "<n/a>")"
        }

        private func notifyMessageChanged() {
            owner?.message.text = message
        }
    }
    enum Action {

    }

    static let triggerReloadPaths: Set<String> = []

    var state: State {
        willSet { state.owner = nil }
        didSet { state.owner = self }
    }
    let actionPublisher: ActionPublisher<Action>

    private let message = UILabel()
    private let path = UILabel()

    init(initialState: State, actionPublisher: ActionPublisher<Action>) {
        state = initialState
        self.actionPublisher = actionPublisher

        super.init()

        loadView()

        setupConstraints()

        state.owner = self
    }

    private func loadView() {
        [
            message,
            path
        ].forEach(addSubview(_:))

        Styles.message(label: message)
        Styles.path(label: path)
    }

    private func setupConstraints() {
        message.snp.makeConstraints { make in
            make.leading.equalToSuperview()
            make.trailing.equalToSuperview()
            make.top.equalToSuperview()
        }

        path.snp.makeConstraints { make in
            make.leading.equalToSuperview().inset(10)
            make.trailing.equalToSuperview()
            make.top.equalTo(message.snp.bottom).offset(8)
            make.bottom.equalToSuperview()
        }
    }
}

extension LiveUIErrorMessageItem {
    fileprivate struct Styles {
        static func path(label: UILabel) {
            label.textColor = .white
            label.numberOfLines = 0
            label.font = UIFont.monospacedDigitSystemFont(ofSize: 14, weight: UIFont.Weight.regular)
        }

        static func message(label: UILabel) {
            label.textColor = .white
            label.numberOfLines = 0
            label.font = UIFont.boldSystemFont(ofSize: 16)
        }
    }
}
