package uk.gov.pay.connector.charge.model;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class FirstDigitsCardNumberConverter implements AttributeConverter<FirstDigitsCardNumber, String> {
    @Override
    public String convertToDatabaseColumn(FirstDigitsCardNumber firstDigits) {
        if (firstDigits == null) {
            return null;
        }
        return firstDigits.toString();
    }

    @Override
    public FirstDigitsCardNumber convertToEntityAttribute(String s) {
        return FirstDigitsCardNumber.ofNullable(s);
    }
}
