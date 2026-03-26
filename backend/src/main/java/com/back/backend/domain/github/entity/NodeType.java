package com.back.backend.domain.github.entity;

/**
 * code_index.node_type 값.
 * DB에는 소문자 문자열로 저장 (NodeTypeConverter 참조).
 */
public enum NodeType {
    CLASS("class"),
    FUNCTION("function"),
    METHOD("method");

    private final String value;

    NodeType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static NodeType fromValue(String value) {
        for (NodeType t : values()) {
            if (t.value.equals(value)) return t;
        }
        throw new IllegalArgumentException("Unknown NodeType: " + value);
    }
}
