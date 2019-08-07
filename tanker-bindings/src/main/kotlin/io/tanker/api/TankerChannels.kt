package io.tanker.api

import java.io.InputStream

class TankerChannels {

    companion object {
        @JvmStatic
        public fun newInputStream(channel: TankerStreamChannel): InputStream {
            return TankerInputStream(channel)
        }
    }
}