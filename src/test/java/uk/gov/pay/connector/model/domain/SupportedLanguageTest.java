package uk.gov.pay.connector.model.domain;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class SupportedLanguageTest {

    @Test
    public void enMapsToEnglish() {
        SupportedLanguage result = SupportedLanguage.fromIso639AlphaTwoCode("en");
        assertThat(result, is(SupportedLanguage.ENGLISH));
    }

    @Test
    public void cyMapsToWelsh() {
        SupportedLanguage result = SupportedLanguage.fromIso639AlphaTwoCode("cy");
        assertThat(result, is(SupportedLanguage.WELSH));
    }

    @Test(expected = IllegalArgumentException.class)
    public void frThrowsException() {
        SupportedLanguage.fromIso639AlphaTwoCode("fr");
    }

    @Test(expected = IllegalArgumentException.class)
    public void enUpperCaseThrowsException() {
        SupportedLanguage.fromIso639AlphaTwoCode("EN");
    }

    @Test(expected = IllegalArgumentException.class)
    public void englishUpperCaseThrowsException() {
        SupportedLanguage.fromIso639AlphaTwoCode("ENGLISH");
    }

}
