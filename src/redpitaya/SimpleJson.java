package redpitaya;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimpleJson {

    private SimpleJson() {
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    public static Object parse(String json) {
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Unexpected JSON trailing data");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("JSON value is not an object");
        }
        return (Map<String, Object>) value;
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(out, s);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, item);
            }
            out.append(']');
        } else if (value instanceof int[] ints) {
            out.append('[');
            for (int i = 0; i < ints.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(ints[i]);
            }
            out.append(']');
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' ->
                    out.append("\\\"");
                case '\\' ->
                    out.append("\\\\");
                case '\b' ->
                    out.append("\\b");
                case '\f' ->
                    out.append("\\f");
                case '\n' ->
                    out.append("\\n");
                case '\r' ->
                    out.append("\\r");
                case '\t' ->
                    out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {

        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' ->
                    parseObject();
                case '[' ->
                    parseArray();
                case '"' ->
                    parseString();
                case 't' -> {
                    expect("true");
                    yield Boolean.TRUE;
                }
                case 'f' -> {
                    expect("false");
                    yield Boolean.FALSE;
                }
                case 'n' -> {
                    expect("null");
                    yield null;
                }
                default ->
                    parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (consume('}')) {
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                require(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (consume('}')) {
                    return map;
                }
                require(',');
            }
        }

        private List<Object> parseArray() {
            ArrayList<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (consume(']')) {
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return list;
                }
                require(',');
            }
        }

        private String parseString() {
            require('"');
            StringBuilder out = new StringBuilder();
            while (!atEnd()) {
                char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw new IllegalArgumentException("Bad JSON escape");
                }
                char e = text.charAt(pos++);
                switch (e) {
                    case '"' ->
                        out.append('"');
                    case '\\' ->
                        out.append('\\');
                    case '/' ->
                        out.append('/');
                    case 'b' ->
                        out.append('\b');
                    case 'f' ->
                        out.append('\f');
                    case 'n' ->
                        out.append('\n');
                    case 'r' ->
                        out.append('\r');
                    case 't' ->
                        out.append('\t');
                    case 'u' -> {
                        if (pos + 4 > text.length()) {
                            throw new IllegalArgumentException("Bad JSON unicode escape");
                        }
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default ->
                        throw new IllegalArgumentException("Unsupported JSON escape: " + e);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Number parseNumber() {
            int start = pos;
            if (!atEnd() && text.charAt(pos) == '-') {
                pos++;
            }
            while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            boolean floating = false;
            if (!atEnd() && text.charAt(pos) == '.') {
                floating = true;
                pos++;
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (!atEnd() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                floating = true;
                pos++;
                if (!atEnd() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            String raw = text.substring(start, pos);
            if (raw.isEmpty() || "-".equals(raw)) {
                throw new IllegalArgumentException("Bad JSON number");
            }
            if (floating) {
                return Double.parseDouble(raw);
            }
            long value = Long.parseLong(raw);
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
            return value;
        }

        private void expect(String value) {
            if (!text.startsWith(value, pos)) {
                throw new IllegalArgumentException("Expected " + value);
            }
            pos += value.length();
        }

        private boolean consume(char c) {
            if (!atEnd() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void require(char c) {
            if (atEnd() || text.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "'");
            }
            pos++;
        }
    }
}
