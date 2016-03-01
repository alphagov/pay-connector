package uk.gov.pay.connector.model.domain;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Converter
public class ChargeStatusConverter implements AttributeConverter<ChargeStatus, String> {
    @Override
    public String convertToDatabaseColumn(ChargeStatus status) {
        return status.getValue();
    }

    @Override
    public ChargeStatus convertToEntityAttribute(String status) {
        if (status == null) {
            return null;
        } else {
            return ChargeStatus.chargeStatusFrom(status);
        }
    }
}
