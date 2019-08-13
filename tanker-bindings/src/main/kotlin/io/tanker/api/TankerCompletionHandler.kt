package io.tanker.api

// nio.channels.CompletionHandler requires API 26
// provide our own interface as a replacement

interface TankerCompletionHandler<V, A> {
    fun completed(result: V, attachment: A)
    fun failed(exc: Throwable, attachment: A)
}