package za.co.neroland.neropower.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny SNBT reader for test fixtures: compounds, lists, quoted strings, ints, {@code L} longs and
 * {@code b} bytes, and {@code //} line comments. Values become Map / List / Integer / Long / Byte /
 * String. Enough for save fixtures; not a general NBT parser.
 */
final class MiniSnbt {

    private final String s;
    private int i;

    private MiniSnbt(String s) {
        this.s = s;
    }

    static Object parse(String text) {
        MiniSnbt p = new MiniSnbt(text);
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) {
            throw new IllegalArgumentException("trailing text at " + p.i);
        }
        return v;
    }

    private void ws() {
        while (this.i < this.s.length()) {
            char c = this.s.charAt(this.i);
            if (Character.isWhitespace(c)) {
                this.i++;
            } else if (c == '/' && this.s.startsWith("//", this.i)) {
                while (this.i < this.s.length() && this.s.charAt(this.i) != '\n') {
                    this.i++;
                }
            } else {
                return;
            }
        }
    }

    private Object value() {
        ws();
        char c = this.s.charAt(this.i);
        if (c == '{') {
            return compound();
        }
        if (c == '[') {
            return list();
        }
        if (c == '"') {
            return quoted();
        }
        return scalar(bare());
    }

    private Map<String, Object> compound() {
        Map<String, Object> out = new LinkedHashMap<>();
        this.i++;
        ws();
        while (this.s.charAt(this.i) != '}') {
            String key = this.s.charAt(this.i) == '"' ? quoted() : bare();
            ws();
            expect(':');
            out.put(key, value());
            ws();
            if (this.s.charAt(this.i) == ',') {
                this.i++;
                ws();
            }
        }
        this.i++;
        return out;
    }

    private List<Object> list() {
        List<Object> out = new ArrayList<>();
        this.i++;
        ws();
        while (this.s.charAt(this.i) != ']') {
            out.add(value());
            ws();
            if (this.s.charAt(this.i) == ',') {
                this.i++;
                ws();
            }
        }
        this.i++;
        return out;
    }

    private void expect(char c) {
        if (this.s.charAt(this.i) != c) {
            throw new IllegalArgumentException("expected " + c + " at " + this.i);
        }
        this.i++;
    }

    private String quoted() {
        expect('"');
        int start = this.i;
        while (this.s.charAt(this.i) != '"') {
            this.i++;
        }
        return this.s.substring(start, this.i++);
    }

    private String bare() {
        int start = this.i;
        while (this.i < this.s.length() && "{}[],:\" \t\r\n".indexOf(this.s.charAt(this.i)) < 0) {
            this.i++;
        }
        return this.s.substring(start, this.i);
    }

    private static Object scalar(String t) {
        if (t.endsWith("L")) {
            return Long.parseLong(t.substring(0, t.length() - 1));
        }
        if (t.endsWith("b")) {
            return Byte.parseByte(t.substring(0, t.length() - 1));
        }
        return Integer.parseInt(t);
    }
}
