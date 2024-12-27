package uk.gov.pay.connector.charge.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class LastDigitsCardNumberConverter implements AttributeConverter<LastDigitsCardNumber, String> {
    @Override
    public String convertToDatabaseColumn(LastDigitsCardNumber lastDigits) {
        if (lastDigits == null) {
            return null;
        }
        return lastDigits.toString();
    }

    @Override
    public LastDigitsCardNumber convertToEntityAttribute(String lastDigitsCardNumber) {
        if (lastDigitsCardNumber == null) {
            return null;
        }
        return LastDigitsCardNumber.of(lastDigitsCardNumber);
    }
}
