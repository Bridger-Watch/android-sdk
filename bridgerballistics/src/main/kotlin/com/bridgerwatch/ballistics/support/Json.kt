package com.bridgerwatch.ballistics.support

import kotlin.math.abs

/**
 * A tiny, dependency-free JSON writer used to produce the exact bytes Bridger
 * consumes. It serializes a tree of [Map], [List], [String], [Number], [Boolean]
 * and `null`.
 *
 * - Compact output preserves insertion order (Bridger parses by key, not order).
 * - Pretty output sorts keys at every level and indents two spaces, matching the
 *   Swift package's `.prettyPrinted, .sortedKeys` output byte-for-byte.
 * - Numbers are emitted canonically: an integral [Double] becomes `0` not `0.0`.
 * - Slashes are not escaped and non-ASCII is emitted as literal UTF-8.
 */
internal object Json {

    fun serialize(value: Any?, pretty: Boolean): String {
        val sb = StringBuilder()
        write(sb, value, pretty, 0)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, value: Any?, pretty: Boolean, indent: Int) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Number -> sb.append(formatNumber(value))
            is Map<*, *> -> writeObject(sb, value, pretty, indent)
            is List<*> -> writeArray(sb, value, pretty, indent)
            else -> writeString(sb, value.toString())
        }
    }

    private fun writeObject(sb: StringBuilder, map: Map<*, *>, pretty: Boolean, indent: Int) {
        if (map.isEmpty()) {
            sb.append("{}")
            return
        }
        val keys = map.keys.map { it.toString() }
        val ordered = if (pretty) keys.sorted() else keys
        sb.append("{")
        ordered.forEachIndexed { i, key ->
            if (i > 0) sb.append(",")
            if (pretty) {
                sb.append("\n")
                sb.append("  ".repeat(indent + 1))
            }
            writeString(sb, key)
            sb.append(if (pretty) " : " else ":")
            write(sb, map[key], pretty, indent + 1)
        }
        if (pretty) {
            sb.append("\n")
            sb.append("  ".repeat(indent))
        }
        sb.append("}")
    }

    private fun writeArray(sb: StringBuilder, list: List<*>, pretty: Boolean, indent: Int) {
        if (list.isEmpty()) {
            sb.append("[]")
            return
        }
        sb.append("[")
        list.forEachIndexed { i, element ->
            if (i > 0) sb.append(",")
            if (pretty) {
                sb.append("\n")
                sb.append("  ".repeat(indent + 1))
            }
            write(sb, element, pretty, indent + 1)
        }
        if (pretty) {
            sb.append("\n")
            sb.append("  ".repeat(indent))
        }
        sb.append("]")
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    /** Canonical JSON number: integral doubles print without a decimal point. */
    internal fun formatNumber(n: Number): String = when (n) {
        is Double -> doubleString(n)
        is Float -> doubleString(n.toDouble())
        else -> n.toString()
    }

    private fun doubleString(d: Double): String {
        require(d.isFinite()) { "Non-finite number cannot be serialized to JSON: $d" }
        return if (d == Math.rint(d) && abs(d) < 1e15) d.toLong().toString() else d.toString()
    }
}
