package com.it.greendeer.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * Jackson 轻封装：构造/解析 JSON 且不抛受检异常，替代原先的 fastjson2 用法。
 * （本机离线缓存无 fastjson2 jar，统一走 spring-boot 自带的 jackson。）
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static ObjectNode obj() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode arr() {
        return MAPPER.createArrayNode();
    }

    /** 向对象写入字段（递归支持常见类型），返回同一对象便于链式。 */
    public static ObjectNode put(ObjectNode n, String key, Object value) {
        if (value == null) {
            n.putNull(key);
        } else if (value instanceof JsonNode jn) {
            n.set(key, jn);
        } else if (value instanceof String s) {
            n.put(key, s);
        } else if (value instanceof Boolean b) {
            n.put(key, b);
        } else if (value instanceof Integer i) {
            n.put(key, i);
        } else if (value instanceof Long l) {
            n.put(key, l);
        } else if (value instanceof Double d) {
            n.put(key, d);
        } else if (value instanceof Float f) {
            n.put(key, f.doubleValue());
        } else if (value instanceof List<?> list) {
            ArrayNode a = n.putArray(key);
            for (Object o : list) {
                a.add(toNode(o));
            }
        } else if (value instanceof Map<?, ?> map) {
            ObjectNode sub = obj();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                put(sub, String.valueOf(e.getKey()), e.getValue());
            }
            n.set(key, sub);
        } else {
            n.set(key, toNode(value));
        }
        return n;
    }

    public static JsonNode toNode(Object value) {
        return MAPPER.valueToTree(value);
    }

    /** 解析 JSON；非法输入返回 NullNode。 */
    public static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.nullNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage(), e);
        }
    }

    public static String write(JsonNode node) {
        return node == null ? "null" : node.toString();
    }

    // ---------------- 读取辅助 ---------------- //

    public static String text(JsonNode root, String key) {
        JsonNode n = root == null ? null : root.get(key);
        return n == null || n.isNull() ? null : n.asText();
    }

    public static String textOrDefault(JsonNode root, String key, String def) {
        String v = text(root, key);
        return v == null ? def : v;
    }

    public static int intValue(JsonNode root, String key, int def) {
        JsonNode n = root == null ? null : root.get(key);
        return n == null || n.isNull() || !n.canConvertToInt() ? def : n.asInt();
    }

    public static long longValue(JsonNode root, String key, long def) {
        JsonNode n = root == null ? null : root.get(key);
        return n == null || n.isNull() || !n.canConvertToLong() ? def : n.asLong();
    }

    public static boolean bool(JsonNode root, String key, boolean def) {
        JsonNode n = root == null ? null : root.get(key);
        return n == null || n.isNull() ? def : n.asBoolean();
    }

    public static JsonNode node(JsonNode root, String key) {
        return root == null ? null : root.get(key);
    }

    /**
     * Jackson2 JsonNode → 纯 Map/List/标量。
     * 供 REST 层返回（MVC 使用 Jackson3 序列化，Map 最稳）。
     */
    public static Object toPlain(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isObject()) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            n.fields().forEachRemaining(e -> m.put(e.getKey(), toPlain(e.getValue())));
            return m;
        }
        if (n.isArray()) {
            List<Object> list = new java.util.ArrayList<>();
            n.forEach(x -> list.add(toPlain(x)));
            return list;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isIntegralNumber()) {
            return n.isBigInteger() ? n.bigIntegerValue() : n.asLong();
        }
        if (n.isFloatingPointNumber()) {
            return n.asDouble();
        }
        return n.asText();
    }
}
