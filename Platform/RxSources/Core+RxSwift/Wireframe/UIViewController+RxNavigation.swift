//
//  UIViewController+Navigation.swift
//  Reactant
//
//  Created by Filip Dolnik on 09.11.16.
//  Copyright © 2016 Brightify. All rights reserved.
//

#if canImport(RxSwift)
import RxSwift

extension Reactive where Base: Platform.ViewController {

    /**
     * Presents a view controller and returns `Observable` that indicates when the view has been successfully presented.
     * - parameter controller: generic controller to present
     * - parameter animated: determines whether the view controller presentation should be animated, default is `true`
     */
    public func present<C: Platform.ViewController>(controller: C, animated: Bool = true) -> Single<C> {
        return Single<C>.create { emitter in
            self.base.present(controller, animated: animated, completion: {
                emitter(.success(controller))
            })

            return Disposables.create()
        }
    }

    /**
     * Dismisses topmost view controller and returns `Observable` that indicates when the view has been dismissed.
     * - parameter animated: determines whether the view controller dismissal should be animated, default is `true`
     */
    public func dismiss(animated: Bool = true) -> Completable {
        return Completable.create { emitter in
            self.base.dismiss(animated: animated, completion: {
                emitter(.completed)
            })

            return Disposables.create()
        }
    }

    public func present<C: Platform.ViewController>(controller: Single<C>, animated: Bool = true) -> Single<C> {
        return controller
            .flatMap { controller in
                self.present(controller: controller, animated: animated)
            }
    }
}
#endif
