package uk.gov.pay.connector.gatewayaccount.exception;

import uk.gov.pay.connector.util.ResponseUtil;

import jakarta.ws.rs.BadRequestException;

public class MissingWorldpay3dsFlexCredentialsEntityException extends BadRequestException {
    public MissingWorldpay3dsFlexCredentialsEntityException(Long accountId,
                                                            String path){
        super(ResponseUtil.badRequestResponse(
                String.format("Worldpay gateway account %s has no Worldpay 3DS flex credentials entity for path [%s].",
                        accountId, path)));
    }
}
