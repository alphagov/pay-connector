package uk.gov.pay.connector.service;


import com.stripe.exception.StripeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.refund.service.RefundReversalStripeConnectTransferRequestBuilder;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RefundReversalStripeConnectTransferRequestBuilderTest {
    @Mock
    private RandomIdGenerator mockRandomIdGenerator;

    @Mock
    private com.stripe.model.Refund mockStripeRefund;
    @Mock
    private com.stripe.model.Charge mockStripeCharge;
    @Mock
    private com.stripe.model.Account mockStripeAccount;
    private RefundReversalStripeConnectTransferRequestBuilder builder;


    @BeforeEach
    public void setUp() {
        builder = new RefundReversalStripeConnectTransferRequestBuilder();
    }

    @Test
    void testCreateRequest() throws StripeException {

        when(mockStripeRefund.getChargeObject()).thenReturn(mockStripeCharge);
        when(mockStripeCharge.getId()).thenReturn("ch_sdkhdg887s");
        when(mockStripeCharge.getTransferGroup()).thenReturn("abc");
        when(mockStripeCharge.getOnBehalfOf()).thenReturn("acct_jdsa7789d");
        when(mockStripeRefund.getAmount()).thenReturn(100L);
        when(mockStripeRefund.getCurrency()).thenReturn("GBP");

        Map<String, Object> builderRequest = builder.createRequest("randomId123", mockStripeRefund);

        List expandList = (List) builderRequest.get("expand");
        assertEquals(2, expandList.size());
        assertEquals("balance_transaction", expandList.getFirst());
        assertEquals("destination_payment", expandList.get(1));
        assertEquals(6, builderRequest.size());

        assertEquals("acct_jdsa7789d", builderRequest.get("destination"));
        assertEquals(100L, builderRequest.get("amount"));
        assertEquals("GBP", builderRequest.get("currency"));

        Map<String, Object> metadataMap = (Map<String, Object>) builderRequest.get("metadata");
        assertEquals(2, metadataMap.size());
        assertEquals("abc", builderRequest.get("transfer_group"));
        assertEquals("ch_sdkhdg887s", (metadataMap.get("stripe_charge_id")));
        assertEquals("randomId123", (metadataMap.get("govuk_pay_transaction_external_id")));
    }
}
