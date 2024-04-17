package uk.gov.pay.connector.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.refund.service.RefundReversalStripeConnectTransferRequestBuilder;
import uk.gov.pay.connector.util.RandomIdGenerator;

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
        builder = new RefundReversalStripeConnectTransferRequestBuilder(mockRandomIdGenerator);
    }

    @Test
    void testCreateRequest() throws StripeException, JsonProcessingException {

        when(mockStripeRefund.getChargeObject()).thenReturn(mockStripeCharge);
        when(mockStripeCharge.getId()).thenReturn("ch_sdkhdg887s");
        when(mockStripeCharge.getTransferGroup()).thenReturn("abc");
        when(mockStripeCharge.getOnBehalfOfObject()).thenReturn(mockStripeAccount);
        when(mockStripeAccount.getId()).thenReturn("acct_jdsa7789d");
        when(mockStripeRefund.getAmount()).thenReturn(100L);
        when(mockStripeRefund.getCurrency()).thenReturn("GBP");
        when(mockRandomIdGenerator.random13ByteHexGenerator()).thenReturn("randomId123");

        Map<String, Object> builderRequest = builder.createRequest(mockStripeRefund);

        String[] expandArray = (String[]) builderRequest.get("expand");

        assertEquals(2, expandArray.length);
        assertEquals("balance_transaction", expandArray[0]);
        assertEquals("destination_payment", expandArray[1]);
        assertEquals(6, builderRequest.size());
        
        assertEquals("acct_jdsa7789d", builderRequest.get("destination"));
        assertEquals(100L, builderRequest.get("amount"));
        assertEquals("GBP", builderRequest.get("currency"));
        
        Map<String, Object> metadataMap = (Map<String, Object>) builderRequest.get("metadata");
        assertEquals(2, metadataMap.size());
        assertEquals("abc", builderRequest.get("transferGroup"));
        assertEquals("ch_sdkhdg887s", (metadataMap.get("stripeChargeId")));
        assertEquals("randomId123", (metadataMap.get("correctionPaymentId")));
    }
}
