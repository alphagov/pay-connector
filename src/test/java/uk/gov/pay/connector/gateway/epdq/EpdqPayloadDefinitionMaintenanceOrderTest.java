package uk.gov.pay.connector.gateway.epdq;

import com.google.common.collect.ImmutableList;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForCancelOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForCaptureOrder;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForRefundOrder;

import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.Is.is;

@RunWith(MockitoJUnitRunner.class)
public class EpdqPayloadDefinitionMaintenanceOrderTest {

    private static final String OPERATION_TYPE = "RES";
    private static final String PAY_ID = "PayId";
    private static final String PSP_ID = "PspId";
    private static final String PASSWORD = "password";
    private static final String USER_ID = "User";

    private EpdqTemplateData epdqTemplateData;

    private final EpdqPayloadDefinitionForCancelOrder cancelOrder = new EpdqPayloadDefinitionForCancelOrder();
    private final EpdqPayloadDefinitionForCaptureOrder captureOrder = new EpdqPayloadDefinitionForCaptureOrder();
    private final EpdqPayloadDefinitionForRefundOrder refundOrder = new EpdqPayloadDefinitionForRefundOrder();

    @Before
    public void setup() {
        epdqTemplateData = new EpdqTemplateData();

        epdqTemplateData.setOperationType(OPERATION_TYPE);
        epdqTemplateData.setTransactionId(PAY_ID);
        epdqTemplateData.setMerchantCode(PSP_ID);
        epdqTemplateData.setPassword(PASSWORD);
        epdqTemplateData.setUserId(USER_ID);
        epdqTemplateData.setAmount("400");
    }
    
    @Test
    public void shouldExtractParametersFromTemplate() {
        Set.of(cancelOrder, captureOrder, refundOrder).forEach(order -> {
            List<NameValuePair> result = order.extract(epdqTemplateData);
            assertThat(result, is(ImmutableList.builder().add(
                    new BasicNameValuePair("AMOUNT", "400"),
                    new BasicNameValuePair("OPERATION", OPERATION_TYPE),
                    new BasicNameValuePair("PAYID", PAY_ID),
                    new BasicNameValuePair("PSPID", PSP_ID),
                    new BasicNameValuePair("PSWD", PASSWORD),
                    new BasicNameValuePair("USERID", USER_ID))
                    .build()));
        });
    }

    @Test
    public void testOnlyTransactionIdIsSentIfBothTransactionIdAndOrderIdAvailable() {
        epdqTemplateData.setOrderId("Order-Id");
        List<NameValuePair> extractPairs = captureOrder.extract(epdqTemplateData);
        assertThat(extractPairs, hasItem(new BasicNameValuePair("PAYID", PAY_ID)));
    }

    @Test
    public void testThatOrderIdIsSentIfTransactionIsNotAvailable() {
        epdqTemplateData.setTransactionId(null);
        epdqTemplateData.setOrderId("Order-Id");
        List<NameValuePair> extractPairs = refundOrder.extract(epdqTemplateData);
        assertThat(extractPairs, hasItem(new BasicNameValuePair("ORDERID", "Order-Id")));
    }
}
