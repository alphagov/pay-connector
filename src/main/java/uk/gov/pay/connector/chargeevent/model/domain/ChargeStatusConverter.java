package uk.gov.pay.connector.chargeevent.model.domain;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

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
            return ChargeStatus.fromString(status);
        }
    }
}
