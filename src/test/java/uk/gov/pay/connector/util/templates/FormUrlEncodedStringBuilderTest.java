package uk.gov.pay.connector.util.templates;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.service.OrderRequestBuilder;
import uk.gov.pay.connector.service.epdq.SignatureGenerator;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FormUrlEncodedStringBuilderTest {

    @Mock
    private PayloadDefinition mockPayloadDefinition;

    @Mock
    private SignatureGenerator mockSignatureGenerator;

    @Mock
    private OrderRequestBuilder.TemplateData mockTemplateData;

    private final ImmutableList<NameValuePair> nameValuePairsList = ImmutableList.of(
            new BasicNameValuePair("first parameter", "this has spaces"),
            new BasicNameValuePair("second parameter", "spaces & punctuation marks/points!"),
            new BasicNameValuePair("third parameter", "shall we spend some € in a café?")
    );

    private FormUrlEncodedStringBuilder formUrlEncodedStringBuilder;

    @Test
    public void shouldFormatParametersIntoApplicationXWwwFormUrlEncodedString() {
        when(mockPayloadDefinition.extract(mockTemplateData)).thenReturn(nameValuePairsList);

        formUrlEncodedStringBuilder = new FormUrlEncodedStringBuilder(mockPayloadDefinition);

        String result = formUrlEncodedStringBuilder.buildWith(mockTemplateData);

        assertThat(result, is("first+parameter=this+has+spaces"
                + "&second+parameter=spaces+%26+punctuation+marks%2Fpoints%21"
                + "&third+parameter=shall+we+spend+some+%E2%82%AC+in+a+caf%C3%A9%3F"));
    }

}