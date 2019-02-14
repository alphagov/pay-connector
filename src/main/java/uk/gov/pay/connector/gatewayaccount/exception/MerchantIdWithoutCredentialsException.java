package uk.gov.pay.connector.gatewayaccount.exception;

import uk.gov.pay.connector.util.ResponseUtil;

import javax.ws.rs.BadRequestException;

public class MerchantIdWithoutCredentialsException extends BadRequestException {
    public MerchantIdWithoutCredentialsException(){
        super(ResponseUtil.badRequestResponse("Account Credentials are required to set a Gateway Merchant ID"));
    }
}
