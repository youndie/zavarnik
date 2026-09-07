package io.github.youndie.zavarnik.runner

/**
 * Enough JSON to read a value out of a response: objects, arrays, strings, numbers, booleans and
 * null, with a dot path over the result. Written here rather than pulled in, because the runner
 * has to work on a bare JRE with nothing but itself on the classpath.
 */
public object Json {
    /** Parses [text] into `Map`, `List`, `String`, `Double`, `Boolean` or `null`. */
    public fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipWhitespace()
        if (!parser.atEnd) throw RunnerException("zavarnik: trailing characters after the JSON value at ${parser.pos}")
        return value
    }

    /**
     * Follows [path] — segments separated by dots, an integer segment indexing an array — and
     * returns the value as text: strings as they are, numbers without a trailing `.0`, `null` as
     * `null`. Throws when a segment is missing, naming which.
     */
    public fun extract(
        value: Any?,
        path: String,
    ): String {
        var current = value
        for (segment in path.split('.')) {
            current =
                when (current) {
                    is Map<*, *> -> {
                        if (current.containsKey(segment)) {
                            current[segment]
                        } else {
                            throw RunnerException(
                                "zavarnik: no `$segment` in the response (path `$path`, keys ${current.keys})",
                            )
                        }
                    }

                    is List<*> -> {
                        val index = segment.toIntOrNull()
                        if (index == null || index !in current.indices) {
                            throw RunnerException(
                                "zavarnik: `$segment` is not an index into ${current.size} elements (path `$path`)",
                            )
                        }
                        current[index]
                    }

                    else -> {
                        throw RunnerException("zavarnik: `$segment` asked of a scalar (path `$path`)")
                    }
                }
        }
        return when (current) {
            null -> {
                "null"
            }

            is Double -> {
                if (current == Math.floor(current) &&
                    !current.isInfinite()
                ) {
                    current.toLong().toString()
                } else {
                    current.toString()
                }
            }

            is String, is Boolean -> {
                current.toString()
            }

            else -> {
                throw RunnerException("zavarnik: `$path` is not a scalar")
            }
        }
    }

    private class Parser(
        private val text: String,
    ) {
        var pos: Int = 0
        val atEnd: Boolean get() = pos >= text.length

        fun value(): Any? {
            skipWhitespace()
            if (atEnd) throw RunnerException("zavarnik: empty JSON")
            return when (val c = text[pos]) {
                '{' -> {
                    obj()
                }

                '[' -> {
                    array()
                }

                '"' -> {
                    string()
                }

                't' -> {
                    literal("true", true)
                }

                'f' -> {
                    literal("false", false)
                }

                'n' -> {
                    literal("null", null)
                }

                else -> {
                    if (c == '-' ||
                        c.isDigit()
                    ) {
                        number()
                    } else {
                        throw RunnerException("zavarnik: unexpected `$c` in JSON at $pos")
                    }
                }
            }
        }

        fun skipWhitespace() {
            while (!atEnd && text[pos].isWhitespace()) pos++
        }

        private fun obj(): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>()
            pos++
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = string()
                skipWhitespace()
                expect(':')
                result[key] = value()
                skipWhitespace()
                when (expectOneOf(",}")) {
                    '}' -> return result
                }
            }
        }

        private fun array(): List<Any?> {
            val result = ArrayList<Any?>()
            pos++
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return result
            }
            while (true) {
                result += value()
                skipWhitespace()
                when (expectOneOf(",]")) {
                    ']' -> return result
                }
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (atEnd) throw RunnerException("zavarnik: unterminated JSON string")
                val c = text[pos++]
                when (c) {
                    '"' -> {
                        return out.toString()
                    }

                    '\\' -> {
                        if (atEnd) throw RunnerException("zavarnik: unterminated JSON escape")
                        when (val e = text[pos++]) {
                            '"', '\\', '/' -> {
                                out.append(e)
                            }

                            'b' -> {
                                out.append('\b')
                            }

                            'f' -> {
                                out.append('\u000C')
                            }

                            'n' -> {
                                out.append('\n')
                            }

                            'r' -> {
                                out.append('\r')
                            }

                            't' -> {
                                out.append('\t')
                            }

                            'u' -> {
                                if (pos + HEX_DIGITS >
                                    text.length
                                ) {
                                    throw RunnerException("zavarnik: short \\u escape in JSON")
                                }
                                out.append(text.substring(pos, pos + HEX_DIGITS).toInt(HEX).toChar())
                                pos += HEX_DIGITS
                            }

                            else -> {
                                throw RunnerException("zavarnik: unknown JSON escape \\$e")
                            }
                        }
                    }

                    else -> {
                        out.append(c)
                    }
                }
            }
        }

        private fun number(): Double {
            val start = pos
            while (!atEnd && (text[pos].isDigit() || text[pos] in "+-.eE")) pos++
            return text.substring(start, pos).toDoubleOrNull()
                ?: throw RunnerException("zavarnik: bad JSON number `${text.substring(start, pos)}`")
        }

        private fun literal(
            word: String,
            value: Any?,
        ): Any? {
            if (!text.startsWith(word, pos)) throw RunnerException("zavarnik: unexpected token in JSON at $pos")
            pos += word.length
            return value
        }

        private fun peek(): Char = if (atEnd) throw RunnerException("zavarnik: unexpected end of JSON") else text[pos]

        private fun expect(c: Char) {
            if (peek() != c) throw RunnerException("zavarnik: expected `$c` in JSON at $pos, got `${text[pos]}`")
            pos++
        }

        private fun expectOneOf(chars: String): Char {
            val c = peek()
            if (c !in chars) throw RunnerException("zavarnik: expected one of `$chars` in JSON at $pos, got `$c`")
            pos++
            return c
        }
    }

    private const val HEX_DIGITS = 4
    private const val HEX = 16
}
