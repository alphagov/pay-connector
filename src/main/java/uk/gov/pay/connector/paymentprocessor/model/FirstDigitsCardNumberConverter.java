package uk.gov.pay.connector.paymentprocessor.model;

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
    public FirstDigitsCardNumber convertToEntityAttribute(String firstDigitsCardNumber) {
        if (firstDigitsCardNumber == null) {
            return null;
        }
        return FirstDigitsCardNumber.of(firstDigitsCardNumber);
    }

}
