package uk.gov.pay.connector.common.model.domain;

import jakarta.persistence.AttributeConverter;
import java.util.UUID;

@jakarta.persistence.Converter(autoApply = true)
public class UuidConverter implements AttributeConverter<UUID, UUID> {
    @Override
    public UUID convertToDatabaseColumn(UUID attribute) {
        return attribute;
    }

    @Override
    public UUID convertToEntityAttribute(UUID dbData) {
        return dbData;
    }
}
