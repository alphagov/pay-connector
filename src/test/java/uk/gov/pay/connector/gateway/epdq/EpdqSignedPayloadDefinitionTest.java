package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.EpdqTemplateData;
import uk.gov.pay.connector.gateway.epdq.EpdqSignedPayloadDefinition.EpdqSignedPayloadDefinitionFactory;
import uk.gov.pay.connector.gateway.templates.PayloadDefinition;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.epdq.EpdqSignedPayloadDefinition.EpdqSignedPayloadDefinitionFactory.anEpdqSignedPayloadDefinitionFactory;

@RunWith(MockitoJUnitRunner.class)
public class EpdqSignedPayloadDefinitionTest {

    private static final String SHA_IN_PASSPHRASE = "my super-secret passphrase";
    private static final String SIGNATURE = "signed sealed and delivered";

    private static final NameValuePair PARAM_1 = new BasicNameValuePair("Key 1", "Value 1");
    private static final NameValuePair PARAM_2 = new BasicNameValuePair("Key 2", "Value 2");
    private static final NameValuePair PARAM_3 = new BasicNameValuePair("Key 3", "Value 3");

    @Mock
    private SignatureGenerator mockSignatureGenerator;
    @Mock
    private PayloadDefinition mockPayloadDefinition;
    @Mock
    private EpdqTemplateData mockEpdqTemplateData;

    @Test
    public void shouldDecoratePayloadDefinitionWithSignature() {
        EpdqSignedPayloadDefinitionFactory epdqSignedPayloadDefinitionFactory = anEpdqSignedPayloadDefinitionFactory(mockSignatureGenerator);
        EpdqSignedPayloadDefinition epdqSignedPayloadDefinition = epdqSignedPayloadDefinitionFactory.create(mockPayloadDefinition);

        when(mockPayloadDefinition.extract(mockEpdqTemplateData)).thenReturn(ImmutableList.of(PARAM_1, PARAM_2, PARAM_3));
        when(mockEpdqTemplateData.getShaInPassphrase()).thenReturn(SHA_IN_PASSPHRASE);
        when(mockSignatureGenerator.sign(ImmutableList.of(PARAM_1, PARAM_2, PARAM_3), SHA_IN_PASSPHRASE)).thenReturn(SIGNATURE);

        ImmutableList<NameValuePair> result = epdqSignedPayloadDefinition.extract(mockEpdqTemplateData);

        assertThat(result, is(ImmutableList.of(PARAM_1, PARAM_2, PARAM_3, new BasicNameValuePair("SHASIGN", SIGNATURE))));
    }

}
