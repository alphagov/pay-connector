package uk.gov.pay.connector.chargeevent.model.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Converter
public class LocalDateTimeConverter implements AttributeConverter<ZonedDateTime, Timestamp> {
    @Override
    public Timestamp convertToDatabaseColumn(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        else {
            return Timestamp.valueOf(dateTime.toLocalDateTime());
        }
    }

    @Override
    public ZonedDateTime convertToEntityAttribute(Timestamp s) {
        if (s == null) {
            return null;
        } else {
            return s.toLocalDateTime().atZone(ZoneId.of("UTC"));
        }
    }
}
