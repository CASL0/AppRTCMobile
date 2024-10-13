/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
package org.appspot.apprtc.util

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Scanner
import java.util.concurrent.Executors

/** Asynchronous http requests implementation. */
class AsyncHttpURLConnection(
    private val method: String,
    private val url: String,
    private val message: String?,
    private val events: AsyncHttpEvents
) {
    /** Http requests callbacks. */
    interface AsyncHttpEvents {
        fun onHttpError(errorMessage: String?)
        fun onHttpComplete(response: String?)
    }

    var contentType: String? = null

    fun send() {
        BACKGROUND.submit {
            sendHttpMessage()
        }
    }

    private fun sendHttpMessage() {
        try {
            val postData = message?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                useCaches = false
                doInput = true
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                // TODO(glaznev) - query request origin from pref_room_server_url_key preferences.
                addRequestProperty(
                    "origin",
                    HTTP_ORIGIN
                )
                if (method == "POST") {
                    doOutput = true
                    setFixedLengthStreamingMode(postData.size)
                    // Send POST request.
                    if (postData.isNotEmpty()) {
                        val outStream = outputStream
                        outStream.write(postData)
                        outStream.close()
                    }
                }

            }
            connection.setRequestProperty(
                "Content-Type",
                if (contentType == null) {
                    "text/plain; charset=utf-8"

                } else {
                    contentType
                }
            )

            // Get response.
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                events.onHttpError(
                    "Non-200 response to $method to URL: $url : ${connection.getHeaderField(null)}"
                )
                connection.disconnect()
                return
            }
            val responseStream = connection.inputStream
            val response = drainStream(responseStream)
            responseStream.close()
            connection.disconnect()
            events.onHttpComplete(response)
        } catch (e: SocketTimeoutException) {
            events.onHttpError("HTTP $method to $url timeout")
        } catch (e: IOException) {
            events.onHttpError("HTTP $method to $url error: ${e.message}")
        }
    }

    companion object {
        private const val HTTP_TIMEOUT_MS = 8_000
        private const val HTTP_ORIGIN = "https://appr.tc"
        private val BACKGROUND = Executors.newFixedThreadPool(2)

        // Return the contents of an InputStream as a String.
        private fun drainStream(`in`: InputStream): String {
            val s = Scanner(
                `in`,
                "UTF-8"
            ).useDelimiter("\\A")
            return if (s.hasNext()) s.next() else ""
        }
    }
}
