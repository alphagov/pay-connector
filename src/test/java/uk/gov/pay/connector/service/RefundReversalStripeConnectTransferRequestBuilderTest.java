package uk.gov.pay.connector.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.refund.service.RefundReversalStripeConnectTransferRequestBuilder;
import uk.gov.pay.connector.util.RandomIdGenerator;

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
    private  RefundReversalStripeConnectTransferRequestBuilder builder;
    

    @BeforeEach
    public void setUp() {
        builder = new RefundReversalStripeConnectTransferRequestBuilder(mockRandomIdGenerator);
    }
    
    @Test
    public void testCreateRequest() throws StripeException, JsonProcessingException {

        when(mockStripeRefund.getChargeObject()).thenReturn(mockStripeCharge);
        when(mockStripeCharge.getId()).thenReturn("ch_sdkhdg887s");
        when(mockStripeCharge.getTransferGroup()).thenReturn("abc");
        when(mockStripeCharge.getOnBehalfOfObject()).thenReturn(mockStripeAccount);
        when(mockStripeAccount.getId()).thenReturn("acct_jdsa7789d");
        when(mockStripeRefund.getAmount()).thenReturn(100L);
        when(mockStripeRefund.getCurrency()).thenReturn("GBP");
        when(mockRandomIdGenerator.random13ByteHexGenerator()).thenReturn("randomId123");

        JSONObject builderRequest = builder.createRequest(mockStripeRefund);

        JsonNode convertedBuilderRequest = new ObjectMapper().readTree(builder.createRequest(mockStripeRefund).toString());
        
        JsonNode expandArray = convertedBuilderRequest.get("expand");
        assertEquals(2, expandArray.size());
        assertEquals("\"balance_transaction\"", expandArray.get(0).toString());
        assertEquals("\"destination_payment\"", expandArray.get(1).toString());

        assertEquals("acct_jdsa7789d", builderRequest.getString("destination"));
        assertEquals(100, builderRequest.getInt("amount"));
        assertEquals("GBP", builderRequest.getString("currency"));
        assertEquals("abc", builderRequest.getString("transferGroup"));
        assertEquals("ch_sdkhdg887s", builderRequest.getJSONObject("metadata").getString("stripeChargeId"));
        assertEquals("randomId123", builderRequest.getJSONObject("metadata").getString("correctionPaymentId"));
    }
}

