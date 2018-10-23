package uk.gov.pay.connector.common.model.domain;

import javax.persistence.AttributeConverter;
import java.util.UUID;

@javax.persistence.Converter(autoApply = true)
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
