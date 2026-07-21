package com.roonshield.roon.moo

import java.nio.charset.StandardCharsets

/**
 * A single MOO protocol message. In Roon's WebSocket transport, exactly one binary
 * WebSocket frame carries exactly one MOO message.
 *
 * Wire format:
 * ```
 * MOO/1 <VERB> <name>\n
 * Request-Id: <n>\n
 * [Content-Length: <n>\n]
 * [Content-Type: <type>\n]
 * \n
 * <body bytes>
 * ```
 * VERB is REQUEST (extension -> core, or core -> extension for provided services),
 * or COMPLETE / CONTINUE (responses). `name` is a `service:ver/operation` string on
 * requests and a status token (e.g. "Success", "Registered", "Subscribed", "Changed")
 * on responses.
 */
data class MooMessage(
    val verb: String,
    val name: String,
    val requestId: Int,
    val headers: Map<String, String> = emptyMap(),
    val contentType: String? = null,
    val body: ByteArray? = null,
) {
    val bodyText: String?
        get() = body?.toString(StandardCharsets.UTF_8)

    fun encode(): ByteArray {
        val sb = StringBuilder()
        sb.append("MOO/1 ").append(verb).append(' ').append(name).append('\n')
        sb.append("Request-Id: ").append(requestId).append('\n')
        if (body != null) {
            sb.append("Content-Length: ").append(body.size).append('\n')
            sb.append("Content-Type: ").append(contentType ?: "application/json").append('\n')
        }
        // blank line terminates the header block
        sb.append('\n')
        val header = sb.toString().toByteArray(StandardCharsets.UTF_8)
        return if (body != null) header + body else header
    }

    companion object {
        const val VERB_REQUEST = "REQUEST"
        const val VERB_COMPLETE = "COMPLETE"
        const val VERB_CONTINUE = "CONTINUE"

        fun request(name: String, requestId: Int, body: ByteArray?, contentType: String? = null): MooMessage =
            MooMessage(VERB_REQUEST, name, requestId, contentType = if (body != null) (contentType ?: "application/json") else null, body = body)

        fun response(verb: String, name: String, requestId: Int, body: ByteArray?, contentType: String? = null): MooMessage =
            MooMessage(verb, name, requestId, contentType = if (body != null) (contentType ?: "application/json") else null, body = body)

        fun parse(bytes: ByteArray): MooMessage {
            val sep = indexOfHeaderEnd(bytes)
            require(sep >= 0) { "Malformed MOO message: no header/body separator" }
            val headerText = String(bytes, 0, sep, StandardCharsets.UTF_8)
            val bodyStart = sep + 2 // skip the two '\n'
            val lines = headerText.split("\n")
            require(lines.isNotEmpty()) { "Malformed MOO message: empty header" }

            val first = lines[0].split(" ", limit = 3)
            require(first.size >= 2 && first[0] == "MOO/1") { "Malformed MOO start line: ${lines[0]}" }
            val verb = first[1]
            val name = if (first.size >= 3) first[2] else ""

            val headers = LinkedHashMap<String, String>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                if (line.isEmpty()) continue
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                headers[line.substring(0, colon).trim()] = line.substring(colon + 1).trim()
            }

            val requestId = headers["Request-Id"]?.toIntOrNull()
                ?: error("Malformed MOO message: missing Request-Id")
            val contentType = headers["Content-Type"]
            val contentLength = headers["Content-Length"]?.toIntOrNull()

            val body: ByteArray? = when {
                contentLength != null && contentLength > 0 -> {
                    val end = (bodyStart + contentLength).coerceAtMost(bytes.size)
                    bytes.copyOfRange(bodyStart, end)
                }
                bodyStart < bytes.size -> bytes.copyOfRange(bodyStart, bytes.size)
                else -> null
            }

            return MooMessage(verb, name, requestId, headers, contentType, body)
        }

        /** Index of the first '\n' of the "\n\n" header terminator, or -1. */
        private fun indexOfHeaderEnd(bytes: ByteArray): Int {
            var i = 0
            while (i < bytes.size - 1) {
                if (bytes[i] == '\n'.code.toByte() && bytes[i + 1] == '\n'.code.toByte()) return i
                i++
            }
            return -1
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MooMessage) return false
        return verb == other.verb && name == other.name && requestId == other.requestId &&
            headers == other.headers && contentType == other.contentType &&
            (body?.contentEquals(other.body) ?: (other.body == null))
    }

    override fun hashCode(): Int {
        var result = verb.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + requestId
        result = 31 * result + headers.hashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}
