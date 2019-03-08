package uk.gov.pay.connector.gatewayaccount.exception;

import uk.gov.pay.connector.util.ResponseUtil;

import javax.ws.rs.BadRequestException;

public class DigitalWalletNotSupportedGatewayException extends BadRequestException {
    public DigitalWalletNotSupportedGatewayException(){
        super(ResponseUtil.badRequestResponse(("This gateway does not support digital wallets.")));
    }
}
