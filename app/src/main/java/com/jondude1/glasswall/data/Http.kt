package com.jondude1.glasswall.data

import java.net.HttpURLConnection
import java.net.URL

/** Tiny HTTP helper. Follows redirects by hand because GitHub downloads hop to a CDN on another host. */
object Http {
    fun open(url: String, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        var current = url
        for (hop in 0..6) {
            val c = URL(current).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "GlassWall")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) error("Redirect without a location")
                current = URL(URL(current), loc).toString()
                continue
            }
            if (code !in 200..299) {
                c.disconnect()
                error("HTTP $code")
            }
            return c
        }
        error("Too many redirects")
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = open(url, headers)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
