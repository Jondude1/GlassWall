package com.jondude1.glasswall.sim

import org.json.JSONObject

/** Helpers that make whatever the local model returns safe to use: cleaning, tolerant parsing, near-duplicate checks. */
object Speech {
    class Reply(val action: String, val target: String, val say: String, val thought: String, val repaired: Boolean)

    /** Strips think tags, code fences, markdown and quotes, collapses spaces and cuts at a word boundary. */
    fun clean(raw: String, max: Int = 90): String {
        var s = raw
        s = s.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), " ")
        s = s.replace(Regex("<think>.*", RegexOption.DOT_MATCHES_ALL), " ")
        s = s.replace(Regex("</?[a-zA-Z][^>]*>"), " ")
        s = s.replace("```", " ").replace("**", "").replace("__", "").replace("`", "")
        s = s.replace(Regex("[*#_~]+"), "")
        s = s.replace(Regex("\\s+"), " ").trim()
        s = s.trim('"', '\'', '“', '”', ' ')
        if (s.length > max) {
            val cut = s.substring(0, max)
            val sp = cut.lastIndexOf(' ')
            val end = Regex("[.!?]").findAll(cut).lastOrNull()?.range?.last ?: -1
            s = when {
                end >= max / 2 -> cut.substring(0, end + 1)
                sp > max / 2 -> cut.substring(0, sp).trimEnd(',', ';', ':', '-') + "…"
                else -> cut.trimEnd() + "…"
            }
        }
        return s
    }

    private fun field(text: String, name: String): String {
        val m = Regex("\"$name\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)", RegexOption.IGNORE_CASE).find(text) ?: return ""
        return m.groupValues[1].replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\")
    }

    /** Accepts clean JSON, JSON with stray text around it, cut-off JSON, or just a few quoted fields. */
    fun parse(text: String): Reply? {
        val cleaned = text.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), " ").replace("```json", " ").replace("```", " ")
        val a = cleaned.indexOf('{')
        if (a < 0) return null
        val b = cleaned.lastIndexOf('}')
        if (b > a) {
            try {
                val o = JSONObject(cleaned.substring(a, b + 1))
                val act = o.optString("action").trim()
                if (act.isNotEmpty()) return Reply(act, o.optString("target"), o.optString("say"), o.optString("thought"), false)
            } catch (e: Exception) {
                // fall through to the forgiving parser
            }
        }
        val body = cleaned.substring(a)
        val act = field(body, "action").trim()
        if (act.isEmpty()) return null
        return Reply(act, field(body, "target"), field(body, "say"), field(body, "thought"), true)
    }

    private fun words(s: String): Set<String> =
        s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.length > 2 }.toSet()

    /** True when two lines say nearly the same thing. */
    fun similar(a: String, b: String): Boolean {
        val x = words(a)
        val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return a.trim().equals(b.trim(), ignoreCase = true)
        val inter = x.intersect(y).size.toFloat()
        val union = x.union(y).size.toFloat()
        return inter / union >= 0.6f || inter / minOf(x.size, y.size) >= 0.85f
    }

    /** Keeps a child's speech short and simple: first sentence, few words. */
    fun kidify(s: String): String {
        val first = s.split(Regex("(?<=[.!?])\\s+")).firstOrNull()?.trim().orEmpty()
        val w = first.split(' ')
        return if (w.size > 9) w.take(9).joinToString(" ").trimEnd(',', ';', ':') + "!" else first
    }
}
