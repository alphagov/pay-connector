package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForMaintenanceOrder;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EpdqPayloadDefinitionMaintenanceOrderTest {

    private static final String OPERATION_TYPE = "RES";
    private static final String PAY_ID = "PayId";
    private static final String PSP_ID = "PspId";
    private static final String PASSWORD = "password";
    private static final String USER_ID = "User";

    @Mock
    private EpdqOrderRequestBuilder.EpdqTemplateData mockTemplateData;

    private final EpdqPayloadDefinitionForMaintenanceOrder epdqPayloadDefinitionForMaintenanceOrder = new EpdqPayloadDefinitionForMaintenanceOrder();

    @Test
    public void shouldExtractParametersFromTemplate() {
        when(mockTemplateData.getOperationType()).thenReturn(OPERATION_TYPE);
        when(mockTemplateData.getTransactionId()).thenReturn(PAY_ID);
        when(mockTemplateData.getMerchantCode()).thenReturn(PSP_ID);
        when(mockTemplateData.getPassword()).thenReturn(PASSWORD);
        when(mockTemplateData.getUserId()).thenReturn(USER_ID);

        List<NameValuePair> result = epdqPayloadDefinitionForMaintenanceOrder.extract(mockTemplateData);

        assertThat(result, is(ImmutableList.builder().add(
                new BasicNameValuePair("OPERATION", OPERATION_TYPE),
                new BasicNameValuePair("PAYID", PAY_ID),
                new BasicNameValuePair("PSPID", PSP_ID),
                new BasicNameValuePair("PSWD", PASSWORD),
                new BasicNameValuePair("USERID", USER_ID))
                .build()));
    }


}
