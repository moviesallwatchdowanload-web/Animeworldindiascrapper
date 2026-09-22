package com.outcome.watchanimeworld

object DeanEdwards {

    private val PACKED_REGEX = Regex(
        "eval\\(function\\(p,a,c,k,e,d\\)\\{[\\s\\S]*?\\}\\('([\\s\\S]*?)',(\\d+),(\\d+),'([\\s\\S]*?)'\\.split\\('\\|'\\)",
        RegexOption.DOT_MATCHES_ALL
    )

    fun unpack(html: String): String? {
        val m = PACKED_REGEX.find(html) ?: return null
        val p = m.groupValues[1]
        val a = m.groupValues[2].toInt()
        var c = m.groupValues[3].toInt()
        val k = m.groupValues[4].split("|")

        val d = mutableMapOf<String, String>()

        fun e(n: Int): String {
            if (n < a) return ""
            return e(n / a) + ((n % a).let { r ->
                if (r > 35) (r + 29).toChar().toString() else r.toString(36)
            })
        }

        while (c > 0) {
            c--
            val key = e(c)
            d[key] = k.getOrElse(c) { key }.ifBlank { key }
        }

        val sb = StringBuilder()
        val wordRegex = Regex("\\b\\w+\\b")
        var last = 0
        for (match in wordRegex.findAll(p)) {
            sb.append(p.substring(last, match.range.first))
            val w = match.value
            sb.append(d[w] ?: w)
            last = match.range.last + 1
        }
        sb.append(p.substring(last))
        return sb.toString()
    }
}
