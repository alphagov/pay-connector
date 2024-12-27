package uk.gov.pay.connector.gatewayaccount.exception;

import uk.gov.pay.connector.util.ResponseUtil;

import jakarta.ws.rs.BadRequestException;

public class DigitalWalletNotSupportedGatewayException extends BadRequestException {
    public DigitalWalletNotSupportedGatewayException(String gatewayName){
        super(ResponseUtil.badRequestResponse((String.format("Gateway %s does not support digital wallets.", gatewayName))));
    }
}
