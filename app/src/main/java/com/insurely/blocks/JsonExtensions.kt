package com.insurely.blocks

import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.toInstruction(): Instruction {
    val requestJson = this.getJSONObject("request")
    val url = requestJson.getString("url")
    val method = requestJson.getString("method")
    val body = requestJson.optJSONObject("body")?.toMap()
    val headers = requestJson.getJSONObject("headers").toMap()
    val cookiesJsonArray = requestJson.optJSONArray("cookies")
    val cookies = cookiesJsonArray?.toCookieList()
    val etag = requestJson.getString("etag")

    val request = Request(url, method, body, headers, cookies, etag)
    return Instruction(request)
}

fun JSONObject.toMap(): Map<String, String> {
    val keys = this.keys()
    val map = mutableMapOf<String, String>()
    while (keys.hasNext()) {
        val key = keys.next()
        map[key] = this.getString(key)
    }
    return map
}

fun JSONArray.toCookieList(): List<Cookie> {
    val cookies = mutableListOf<Cookie>()
    for (i in 0 until this.length()) {
        val json = this.getJSONObject(i)
        val cookie = Cookie(
            name = json.getString("name"),
            value = json.getString("value"),
            domain = json.getString("domain"),
            secure = json.getBoolean("secure"),
            httpOnly = json.getBoolean("httpOnly"),
            path = json.getString("path")
        )
        cookies.add(cookie)
    }
    return cookies
}
