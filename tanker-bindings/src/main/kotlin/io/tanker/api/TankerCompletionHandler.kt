package io.tanker.api

// nio.channels.CompletionHandler requires API 26
// provide our own interface as a replacement

interface TankerCompletionHandler<V, A> {
    public abstract fun completed(result: V, attachment: A): Unit
    public abstract fun failed(exc: Throwable, attachment: A): Unit
}