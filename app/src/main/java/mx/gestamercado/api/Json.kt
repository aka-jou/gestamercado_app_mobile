package mx.gestamercado.api

import java.math.BigDecimal

/**
 * JSON mínimo sin dependencias (el proyecto no usa Gson/Moshi/kotlinx.serialization).
 * Codifica Map, List, String, Number, Boolean y null; decodifica a esos mismos tipos
 * (los números a Long cuando son enteros y a Double en otro caso).
 */
object Json {

    fun encode(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> str(sb, v)
            is BigDecimal -> sb.append(v.toPlainString())
            is Double -> {
                require(v.isFinite()) { "Número no finito" }
                sb.append(if (v == Math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString())
            }
            is Float -> write(sb, v.toDouble())
            is Number -> sb.append(v.toString())
            is Boolean -> sb.append(v)
            is Enum<*> -> str(sb, v.name)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    str(sb, k.toString()); sb.append(':'); write(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                v.forEachIndexed { i, x -> if (i > 0) sb.append(','); write(sb, x) }
                sb.append(']')
            }
            is Array<*> -> write(sb, v.asList())
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
        sb.append('"')
    }

    fun parse(s: String): Any? {
        val p = Parser(s)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != s.length) throw IllegalArgumentException("JSON con texto sobrante en ${p.i}")
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun fail(m: String): Nothing = throw IllegalArgumentException("JSON inválido en $i: $m")
        fun value(): Any? {
            if (i >= s.length) fail("fin inesperado")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> string()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("carácter '$c'")
            }
        }
        fun lit(t: String, v: Any?): Any? { if (!s.startsWith(t, i)) fail("se esperaba $t"); i += t.length; return v }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++; ws()
            if (s.getOrNull(i) == '}') { i++; return m }
            while (true) {
                ws(); if (s.getOrNull(i) != '"') fail("se esperaba clave")
                val k = string(); ws()
                if (s.getOrNull(i) != ':') fail("se esperaba ':'")
                i++; ws(); m[k] = value(); ws()
                when (s.getOrNull(i)) { ',' -> i++; '}' -> { i++; return m }; else -> fail("se esperaba ',' o '}'") }
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++; ws()
            if (s.getOrNull(i) == ']') { i++; return l }
            while (true) {
                ws(); l += value(); ws()
                when (s.getOrNull(i)) { ',' -> i++; ']' -> { i++; return l }; else -> fail("se esperaba ',' o ']'") }
            }
        }
        fun string(): String {
            val sb = StringBuilder()
            i++
            while (true) {
                if (i >= s.length) fail("cadena sin cerrar")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> fail("escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun num(): Number {
            val ini = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(ini, i)
            return if (t.any { it == '.' || it == 'e' || it == 'E' }) t.toDouble() else t.toLongOrNull() ?: t.toDouble()
        }
    }
}

/* Lectura defensiva de respuestas: el contrato de respuesta no está fijado en la especificación. */
@Suppress("UNCHECKED_CAST")
fun Any?.asMap(): Map<String, Any?> = (this as? Map<String, Any?>) ?: emptyMap()
@Suppress("UNCHECKED_CAST")
fun Any?.asList(): List<Any?> = (this as? List<Any?>) ?: (this.asMap()["content"] as? List<Any?>) ?: emptyList()
fun Map<String, Any?>.str(vararg k: String): String? = k.firstNotNullOfOrNull { this[it]?.takeIf { v -> v !is Map<*, *> && v !is List<*> }?.toString() }
fun Map<String, Any?>.num(vararg k: String): Double? = k.firstNotNullOfOrNull { (this[it] as? Number)?.toDouble() ?: (this[it] as? String)?.toDoubleOrNull() }
fun Map<String, Any?>.bool(vararg k: String): Boolean? = k.firstNotNullOfOrNull { this[it] as? Boolean ?: (this[it] as? String)?.toBooleanStrictOrNull() }
