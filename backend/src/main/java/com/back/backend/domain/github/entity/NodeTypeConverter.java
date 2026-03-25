package com.back.backend.domain.github.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class NodeTypeConverter implements AttributeConverter<NodeType, String> {

    @Override
    public String convertToDatabaseColumn(NodeType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public NodeType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : NodeType.fromValue(dbData);
    }
}
