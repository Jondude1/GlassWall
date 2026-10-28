package com.jondude1.glasswall.sim

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** Talks to a local model server (Ollama, LM Studio, llama.cpp...) through its OpenAI-style chat API. */
object Llm {
    fun endpoint(baseUrl: String): String {
        var base = baseUrl.trim().trimEnd('/')
        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://$base"
        return if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
    }

    fun chat(baseUrl: String, model: String, system: String, user: String, maxTokens: Int = 700): String {
        val body = JSONObject()
            .put("model", model.trim())
            .put("stream", false)
            .put("temperature", 0.8)
            .put("max_tokens", maxTokens)
            .put("reasoning_effort", "none")
            .put("reasoning_effort", "none")
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", "$user /no_think"))
            )
        val c = URL(endpoint(baseUrl)).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 6_000
            c.readTimeout = 40_000
            c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(300)
                error("HTTP $code $err")
            }
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val content = JSONObject(text).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content")
            return content.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
        } finally {
            c.disconnect()
        }
    }
}
