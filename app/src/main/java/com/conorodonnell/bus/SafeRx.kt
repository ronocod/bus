package com.conorodonnell.bus

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable

inline fun <T> Observable<T>.disposingIn(disposable: CompositeDisposable,
                                         subscription: Observable<T>.() -> Disposable) =
        disposable.add(subscription())

inline fun <T> Single<T>.disposingIn(disposable: CompositeDisposable,
                                     subscription: Single<T>.() -> Disposable) =
        disposable.add(subscription())
