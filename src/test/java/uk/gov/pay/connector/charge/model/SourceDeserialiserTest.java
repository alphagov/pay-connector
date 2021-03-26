package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.service.payments.commons.model.Source;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SourceDeserialiserTest {

    @Mock private JsonParser mockJsonParser;
    @Mock private DeserializationContext mockDeserializationContext;

    private final SourceDeserialiser sourceDeserialiser = new SourceDeserialiser();

    @Test
    void shouldDeserialiseNullToNull() throws IOException {
        given(mockJsonParser.getValueAsString()).willReturn(null);
        Source result = sourceDeserialiser.deserialize(mockJsonParser, mockDeserializationContext);
        assertThat(result, is(nullValue()));
    }

    @ParameterizedTest
    @EnumSource(value = Source.class, names = {"CARD_API", "CARD_PAYMENT_LINK", "CARD_AGENT_INITIATED_MOTO"})
    void shouldDeserialiseAcceptedSources(Source source) throws IOException {
        given(mockJsonParser.getValueAsString()).willReturn(source.name());
        Source result = sourceDeserialiser.deserialize(mockJsonParser, mockDeserializationContext);
        assertThat(result, is(source));
    }

    @ParameterizedTest
    @EnumSource(value = Source.class, names = {"CARD_API", "CARD_PAYMENT_LINK", "CARD_AGENT_INITIATED_MOTO"}, mode = EXCLUDE)
    void shouldThrowExceptionForSourcesNotExplicitlyAllowed(Source source) throws IOException {
        given(mockJsonParser.getValueAsString()).willReturn(source.name());
        var thrown = assertThrows(JsonMappingException.class,
                () -> sourceDeserialiser.deserialize(mockJsonParser, mockDeserializationContext));
        assertThat(thrown.getMessage(), is("Field [source] must be one of CARD_API, CARD_PAYMENT_LINK, CARD_AGENT_INITIATED_MOTO"));
    }

    @Test
    void shouldThrowExceptionForUnrecognisedSource() throws IOException {
        given(mockJsonParser.getValueAsString()).willReturn("CAKE_BASED_BARTERING_SYSTEM");
        var thrown = assertThrows(JsonMappingException.class,
                () -> sourceDeserialiser.deserialize(mockJsonParser, mockDeserializationContext));
        assertThat(thrown.getMessage(), is("Field [source] must be one of CARD_API, CARD_PAYMENT_LINK, CARD_AGENT_INITIATED_MOTO"));
    }

}
