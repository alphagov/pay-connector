package uk.gov.pay.connector.model.domain;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Converter
public class SupportedLanguageConverter implements AttributeConverter<SupportedLanguage, String> {

    @Override
    public String convertToDatabaseColumn(SupportedLanguage supportedLanguage) {
        return supportedLanguage.toString();
    }

    @Override
    public SupportedLanguage convertToEntityAttribute(String supportedLanguage) {
        return SupportedLanguage.fromIso639AlphaTwoCode(supportedLanguage);
    }

}
