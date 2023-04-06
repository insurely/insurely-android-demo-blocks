package com.insurely.blocks

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class Cookie(
    val name: String,
    val value: String,
    val domain: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val path: String
)

data class Request(
    val url: String,
    val method: String,
    val body: Map<String, String>?,
    val headers: Map<String, String>,
    val cookies: List<Cookie>?,
    val etag: String
)

data class Instruction(
    val request: Request
)

data class Response(
    val type: String = "RESPONSE_OBJECT",
    val headers: Map<String, String>,
    val response: JSONObject
)

fun Response.toJsonString(): String {
    val responseJson = JSONObject()
    responseJson.put("type", this.type)
    responseJson.put("headers", JSONObject(this.headers))
    responseJson.put("response", this.response)

    return responseJson.toString()
}

sealed class RequestResult {
    data class Success(val response: Response) : RequestResult()
    object NotUniqueETag : RequestResult()
    object RequestFailed : RequestResult()
}

class InstructionsHandler {
    private val seenETags = mutableSetOf<String>()

    fun executeRequest(instruction: Instruction): RequestResult {
        val request = instruction.request
        val urlString = request.url
        val method = request.method
        val headers = request.headers
        val body = request.body
        val cookies = request.cookies
        val etag = request.etag

        // Check if ETag is unique
        if (etag in seenETags) {
            return RequestResult.NotUniqueETag;
        }
        seenETags.add(etag)

        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doInput = true
            doOutput = method != "GET"

            // Add headers
            headers.forEach { (key, value) -> setRequestProperty(key, value) }

            // Add cookies
            cookies?.takeIf { it.isNotEmpty() }?.let {
                setRequestProperty("Cookie", it.joinToString("; ") { cookie ->
                    "${cookie.name}=${cookie.value}; Domain=${cookie.domain}; Path=${cookie.path}; " +
                            "Secure=${cookie.secure}; HttpOnly=${cookie.httpOnly}"
                })
            }
        }

        // Add body if it's a POST, PUT, or PATCH request
        if (body != null && method != "GET") {
            val bodyJson = JSONObject(body).toString()
            val outputStreamWriter = OutputStreamWriter(connection.outputStream)
            outputStreamWriter.write(bodyJson)
            outputStreamWriter.flush()
            outputStreamWriter.close()
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            return RequestResult.RequestFailed
        }

        val bufferedReader = BufferedReader(InputStreamReader(connection.inputStream))
        val responseText = bufferedReader.use { it.readText() }
        val responseJson = JSONObject(responseText)

        val responseHeaders = connection.headerFields
            .filterKeys { it != null }
            .mapValues { it.value.joinToString(",") }

        return RequestResult.Success(
            Response(
                headers = responseHeaders,
                response = responseJson
            )
        )
    }
}
