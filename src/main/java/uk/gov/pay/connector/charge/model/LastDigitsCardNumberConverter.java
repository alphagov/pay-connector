package uk.gov.pay.connector.charge.model;

import uk.gov.pay.connector.model.LastDigitsCardNumber;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

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
    public LastDigitsCardNumber convertToEntityAttribute(String s) {
        return LastDigitsCardNumber.ofNullable(s);
    }
}
