package com.aidbridge.blockchain.envelope;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonical JSON for private evidence manifests (C01-O03 / D49 profile).
 *
 * <p>Mirrors {@code canonicalize} in {@code docs/fixtures/c01/verify-c01.mjs} so Java and TypeScript
 * produce byte-identical manifests: object keys sorted recursively (UTF-16 code unit order, same as
 * JavaScript {@code Array.prototype.sort}), no whitespace, strings must already be NFC, numbers must be
 * non-negative safe integers, {@code null} and floating-point values are rejected. String escaping
 * follows {@code JSON.stringify}.
 */
public final class CanonicalJson {

    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private CanonicalJson() {
    }

    public static String toCanonicalString(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    public static byte[] toCanonicalUtf8(Object value) {
        return toCanonicalString(value).getBytes(StandardCharsets.UTF_8);
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            throw new IllegalArgumentException("Canonical manifest does not allow null values");
        }
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Canonical manifest keys must be strings");
                }
                sorted.put(key, entry.getValue());
            }
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(entry.getKey(), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(list.get(i), out);
            }
            out.append(']');
        } else if (value instanceof String text) {
            writeString(text, out);
        } else if (value instanceof Boolean flag) {
            out.append(flag ? "true" : "false");
        } else if (value instanceof Integer || value instanceof Long || value instanceof Short
                || value instanceof Byte || value instanceof BigInteger) {
            BigInteger number = value instanceof BigInteger big ? big : BigInteger.valueOf(((Number) value).longValue());
            if (number.signum() < 0 || number.compareTo(BigInteger.valueOf(MAX_SAFE_INTEGER)) > 0) {
                throw new IllegalArgumentException("Canonical manifest numbers must be non-negative safe integers");
            }
            out.append(number);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported canonical manifest value type: " + value.getClass().getSimpleName());
        }
    }

    private static void writeString(String text, StringBuilder out) {
        if (!Normalizer.isNormalized(text, Normalizer.Form.NFC)) {
            throw new IllegalArgumentException("Canonical manifest strings must be NFC normalized");
        }
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isSurrogate(c)) {
                boolean validPair = Character.isHighSurrogate(c) && i + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(i + 1));
                if (!validPair) {
                    throw new IllegalArgumentException("Canonical manifest strings must not contain lone surrogates");
                }
                out.append(c).append(text.charAt(++i));
                continue;
            }
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
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
}
