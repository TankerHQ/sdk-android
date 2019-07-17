package io.tanker.api

internal class TankerCallbackWithKeepAlive<T, U>(@ProguardKeep @Suppress("unused") private val keepAlive: Any?, private val cb: (T) -> U) : TankerCallback<T, U> {
    override fun call(result: T): U {
        return cb(result)
    }
}
