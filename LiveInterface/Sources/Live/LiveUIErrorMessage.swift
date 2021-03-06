//
//  LiveUIErrorMessage.swift
//  ReactantUI
//
//  Created by Tadeas Kriz.
//  Copyright © 2017 Brightify. All rights reserved.
//

import HyperdriveInterface
import UIKit

final class LiveUIErrorMessage: HyperViewBase, HyperView {

    final class State: HyperViewState {
        fileprivate weak var owner: LiveUIErrorMessage? { didSet { resynchronize() } }

        var errors: [LiveUIErrorMessageItem.State] = [] { didSet { notifyErrorsChanged() } }

        init() {

        }

        func apply(from otherState: LiveUIErrorMessage.State) {
            errors = otherState.errors
        }

        func resynchronize() {
            notifyErrorsChanged()
        }

        private func notifyErrorsChanged() {
            guard let owner = owner else { return }

            owner.stackView.arrangedSubviews.forEach { $0.removeFromSuperview() }
            for (index, item) in errors.enumerated() {
                if index > 0 {
                    let divider = UIView()
                    Styles.divider(view: divider)
                    owner.stackView.addArrangedSubview(divider)
                    divider.snp.makeConstraints { make in
                        make.height.equalTo(1)
                    }
                }

                let itemView = LiveUIErrorMessageItem(initialState: item, actionPublisher: ActionPublisher())
                owner.stackView.addArrangedSubview(itemView)
            }

            owner.isHidden = errors.isEmpty
        }
    }
    enum Action {
        case dismiss
    }

    static let triggerReloadPaths: Set<String> = []

    var state: State {
        willSet { state.owner = nil }
        didSet { state.owner = self }
    }

    override var preferredFocusedView: UIView? {
        return button
    }

//    override var actions: [Observable<LiveUIErrorMessageItemAction>] {
//        #if os(tvOS)
//        return [
//            button.rx.primaryAction.rewrite(with: .dismiss)
//        ]
//        #else
//        return [
//            button.rx.tap.rewrite(with: .dismiss)
//        ]
//        #endif
//    }

    private let scrollView = UIScrollView()
    private let stackView = UIStackView()
    private let button = UIButton()

    let actionPublisher: ActionPublisher<Action>

    init(initialState: State, actionPublisher: ActionPublisher<Action>) {
        state = initialState
        self.actionPublisher = actionPublisher

        super.init()

        loadView()

        setupConstraints()

        state.owner = self

        GestureRecognizerObserver.bindTap(to: button, handler: {
            actionPublisher.publish(action: .dismiss)
        })
    }

//    override func update() {
//        let state = componentState
//
//        stackView.arrangedSubviews.forEach { $0.removeFromSuperview() }
//        for (index, item) in state.enumerated() {
//            if index > 0 {
//                let divider = UIView()
//                Styles.divider(view: divider)
//                stackView.addArrangedSubview(divider)
//                divider.snp.makeConstraints { make in
//                    make.height.equalTo(1)
//                }
//            }
//
//            let itemView = LiveUIErrorMessageItem().with(state: (file: item.key, message: item.value))
//            stackView.addArrangedSubview(itemView)
//        }
//
//        isHidden = state.isEmpty
//    }

    private func loadView() {
        [
            scrollView,
            button
        ].forEach(addSubview(_:))

        [
            stackView
        ].forEach(scrollView.addSubview(_:))

        Styles.base(view: self)

        stackView.axis = .vertical
        stackView.distribution = .equalSpacing
        stackView.alignment = .fill
        stackView.spacing = 10

        button.setTitle("Dismiss (ESC)", for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.setTitleColor(.black, for: .focused)
        #warning("TODO: Add implementation to HyperdriveUI")
//        button.setBackgroundColor(UIColor.white.withAlphaComponent(0.1), for: .normal)
//        button.setBackgroundColor(UIColor.white, for: .focused)

        button.clipsToBounds = true
        button.layer.cornerRadius = 16
    }

    private func setupConstraints() {
        scrollView.snp.makeConstraints { make in
            make.leading.equalToSuperview()
            make.trailing.equalToSuperview()
            make.top.equalToSuperview()
        }

        stackView.snp.makeConstraints { make in
            make.edges.equalToSuperview().inset(UIEdgeInsets(top: 40, left: 20, bottom: 20, right: 20))
            make.width.equalToSuperview().inset(20)
        }

        button.snp.makeConstraints { make in
            make.leading.greaterThanOrEqualToSuperview()
            make.trailing.lessThanOrEqualToSuperview()
            make.centerX.equalToSuperview()
            make.width.equalToSuperview().dividedBy(3)

            make.top.equalTo(scrollView.snp.bottom)
            #if os(tvOS)
            make.bottom.equalToSuperview().inset(48)
            make.height.equalTo(80)
            #else
                make.bottom.equalToSuperview().inset(16)
                make.height.equalTo(48)
            #endif
        }
    }
}

extension LiveUIErrorMessage {
    fileprivate struct Styles {
        static func base(view: LiveUIErrorMessage) {
            view.backgroundColor = UIColor(red:0.800, green: 0.000, blue: 0.000, alpha:1)
        }

        static func divider(view: UIView) {
            view.backgroundColor = .white
        }

        static func stack(label: UILabel) {
            label.textColor = .white
            label.numberOfLines = 0
        }
    }
}
