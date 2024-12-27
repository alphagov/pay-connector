package uk.gov.pay.connector.common.model.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Converter
public class UTCDateTimeConverter implements AttributeConverter<ZonedDateTime, Timestamp> {
    @Override
    public Timestamp convertToDatabaseColumn(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        else {
            return Timestamp.from(dateTime.toInstant());
        }
    }

    @Override
    public ZonedDateTime convertToEntityAttribute(Timestamp s) {
        if (s == null) {
            return null;
        } else {
            return ZonedDateTime.ofInstant(s.toInstant(), ZoneId.of("UTC"));
        }
    }
}
