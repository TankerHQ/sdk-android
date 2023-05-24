package io.tanker.api

import com.sun.jna.Memory
import com.sun.jna.Pointer
import io.tanker.bindings.TankerHttpRequest
import io.tanker.bindings.TankerHttpResponse
import io.tanker.bindings.TankerLib
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class HttpClient(private val tankerLib: TankerLib, private val sdkType: String, private val sdkVersion: String) : TankerLib.HttpSendRequestCallback {
    companion object {
        val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()!!
    }

    private val client = OkHttpClient()
    internal val calls = HashMap<Int, Call>()
    private var lastId = 0

    override fun callback(crequest: TankerHttpRequest, data: Pointer?): Pointer? {
        try {
            val body =
                    when {
                        crequest.bodySize != 0 -> crequest.body?.getByteArray(0, crequest.bodySize)
                        crequest.method!! == "POST" ->
                            // okhttp really wants a body when we do a POST
                            byteArrayOf()
                        else ->
                            // okhttp really doesn't want a body when we do a GET (an empty body is not ok)
                            null
                    }

            val requestBuilder = Request.Builder()
                    .url(crequest.url!!)
                    .method(crequest.method!!, body?.toRequestBody(JSON))
                    .header("X-Tanker-SdkType", sdkType)
                    .header("X-Tanker-SdkVersion", sdkVersion)
            if (crequest.authorization != null)
                requestBuilder.header("Authorization", crequest.authorization!!)
            if (crequest.instanceId != null)
                requestBuilder.header("X-Tanker-Instanceid", crequest.instanceId!!)
            val request = requestBuilder.build()


            val call = client.newCall(request)
            val requestId = synchronized(this) {
                val requestId = ++lastId
                calls[requestId] = call
                requestId
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Note that onFailure is called also for canceled requests, but we bail out
                    // before calling back sdk-native in that case
                    val cresponse = TankerHttpResponse()
                    cresponse.errorMsg = e.message
                    synchronized(this) {
                        if (calls.remove(requestId) == null)
                            return // the request was canceled
                        tankerLib.tanker_http_handle_response(crequest.pointer, cresponse)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val cresponse = TankerHttpResponse()
                        cresponse.statusCode = it.code
                        cresponse.contentType = it.header("content-type")
                        val bodyData = it.body?.bytes()
                        if (bodyData != null && bodyData.isNotEmpty()) {
                            val bodyMemory = Memory(bodyData.size.toLong())
                            bodyMemory.write(0, bodyData, 0, bodyData.size)
                            cresponse.body = bodyMemory
                            cresponse.bodySize = bodyData.size.toLong()
                        }

                        synchronized(this) {
                            if (calls.remove(requestId) == null)
                                return // the request was canceled
                            tankerLib.tanker_http_handle_response(crequest.pointer, cresponse)
                        }
                    }
                }
            })

            return Pointer.createConstant(requestId)
        } catch (e: Throwable) {
            val cresponse = TankerHttpResponse()
            cresponse.errorMsg = "${e::class.java.canonicalName}: ${e.message}"
            tankerLib.tanker_http_handle_response(crequest.pointer, cresponse)
            return Pointer.NULL
        }
    }
}

class HttpClientCanceler(private val httpClient: HttpClient) : TankerLib.HttpCancelRequestCallback {
    override fun callback(crequest: TankerHttpRequest, requestHandle: Pointer?, data: Pointer?) {
        val requestId = Pointer.nativeValue(requestHandle).toInt()
        synchronized(httpClient) {
            val call = httpClient.calls.remove(requestId)
            call?.cancel()
        }
    }
}
