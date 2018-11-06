package uk.gov.pay.connector.applepay;

import uk.gov.pay.connector.applepay.api.ApplePayToken;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import javax.inject.Inject;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ApplePayService {
    
    private ApplePayDecrypter applePayDecrypter;
    private AppleAuthoriseService authoriseService;
    
    @Inject
    public ApplePayService(ApplePayDecrypter applePayDecrypter, AppleAuthoriseService authoriseService) {
        this.applePayDecrypter = applePayDecrypter;
        this.authoriseService = authoriseService;
    }

    public GatewayResponse authorise(String chargeId, ApplePayToken applePayToken) {
        byte[] ephemeralPublicKey = applePayToken.getEncryptedPaymentData().getHeader().getEphemeralPublicKey().getBytes(UTF_8);
        ApplePaymentData data = applePayDecrypter.performDecryptOperation(applePayToken.getEncryptedPaymentData().getData().getBytes(UTF_8), ephemeralPublicKey);
        data.setPaymentInfo(applePayToken.getPaymentInfo());
        GatewayResponse<BaseAuthoriseResponse> response = authoriseService.doAuthorise(chargeId, data);
        System.out.println(response);
        return response;
    }
}
