package to.sparkapp.app.extensions;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Inserts the bootstrap statement after the directive prologue ("use strict" and similar), so the
 * worker keeps its strict mode and its own URL for relative imports.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ExtensionWorkerScript {
    static String prepend(String source, String bootstrap) {
        var offset = source.startsWith("\uFEFF") ? 1 : 0;
        if (source.startsWith("#!", offset)) {
            var newline = source.indexOf('\n', offset);
            offset = newline < 0 ? source.length() : newline + 1;
        }
        while (true) {
            var start = skipTrivia(source, offset);
            var end = stringEnd(source, start);
            if (end < 0) {
                return insert(source, start, bootstrap);
            }
            var next = skipTrivia(source, end);
            if (next < source.length() && source.charAt(next) == ';') {
                offset = next + 1;
                continue;
            }
            var trivia = source.substring(end, next);
            var newline = trivia.chars().anyMatch(value -> value == '\n' || value == '\r' || value == 0x2028 || value == 0x2029);
            if (next == source.length() || (newline && !continuesExpression(source, next))) {
                offset = next;
                continue;
            }
            return insert(source, start, bootstrap);
        }
    }

    private static String insert(String source, int offset, String bootstrap) {
        return source.substring(0, offset) + "\n" + bootstrap + "\n" + source.substring(offset);
    }

    private static int skipTrivia(String source, int start) {
        var offset = start;
        while (offset < source.length()) {
            if (Character.isWhitespace(source.charAt(offset))) {
                offset++;
            } else if (source.startsWith("//", offset)) {
                var end = source.indexOf('\n', offset);
                offset = end < 0 ? source.length() : end + 1;
            } else if (source.startsWith("/*", offset)) {
                var end = source.indexOf("*/", offset + 2);
                offset = end < 0 ? source.length() : end + 2;
            } else {
                break;
            }
        }
        return offset;
    }

    private static int stringEnd(String source, int start) {
        if (start >= source.length() || (source.charAt(start) != '\'' && source.charAt(start) != '"')) {
            return -1;
        }
        var quote = source.charAt(start);
        for (var offset = start + 1; offset < source.length(); offset++) {
            var value = source.charAt(offset);
            if (value == '\\') {
                offset++;
                if (offset + 1 < source.length() && source.charAt(offset) == '\r' && source.charAt(offset + 1) == '\n') {
                    offset++;
                }
            } else if (value == quote) {
                return offset + 1;
            } else if (value == '\r' || value == '\n') {
                return -1;
            }
        }
        return -1;
    }

    private static boolean continuesExpression(String source, int offset) {
        if (source.startsWith("++", offset) || source.startsWith("--", offset)) {
            return false;
        }
        var value = source.charAt(offset);
        if ("([.`+-*/%?<>=&|^,".indexOf(value) >= 0) {
            return true;
        }
        for (var operator : new String[]{"in", "instanceof"}) {
            var end = offset + operator.length();
            if (source.startsWith(operator, offset) && (end == source.length() || !Character.isJavaIdentifierPart(source.charAt(end)))) {
                return true;
            }
        }
        return false;
    }
}
