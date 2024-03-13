package uk.gov.pay.connector.refund.service;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.json.JSONObject;
import uk.gov.pay.connector.util.RandomIdGenerator;

@Singleton
public class RefundReversalStripeConnectTransferRequestBuilder {
    private final RandomIdGenerator randomIdGenerator;

    @Inject
    public RefundReversalStripeConnectTransferRequestBuilder(RandomIdGenerator randomIdGenerator) {
        this.randomIdGenerator = randomIdGenerator;
    }
    
    public JSONObject createRequest(com.stripe.model.Refund refundFromStripe) {
        
        String stripeChargeId = refundFromStripe.getChargeObject().getId(); 
        String destination = refundFromStripe.getChargeObject().getOnBehalfOfObject().getId(); 
        String transferGroup = refundFromStripe.getChargeObject().getTransferGroup(); 
        long amount = refundFromStripe.getAmount();
        String currency = refundFromStripe.getCurrency();
        String correctionPaymentId = randomIdGenerator.random13ByteHexGenerator();
        
        JSONObject metadata = new JSONObject();
        metadata.put("stripeChargeId", stripeChargeId);
        metadata.put("correctionPaymentId", correctionPaymentId);
        
        JSONObject requestJson = new JSONObject();
        requestJson.put("destination", destination);
        requestJson.put("amount", amount);
        requestJson.put("metadata", metadata);
        requestJson.put("currency", currency);
        requestJson.put("transferGroup", transferGroup);
        requestJson.put("expand", new String[]{"balance_transaction", "destination_payment"});
        
        return requestJson;
    }
}
