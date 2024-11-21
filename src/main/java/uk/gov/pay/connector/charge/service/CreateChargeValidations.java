package uk.gov.pay.connector.charge.service;

import uk.gov.pay.connector.charge.exception.ErrorListMapper;
import uk.gov.pay.connector.charge.exception.GatewayAccountDisabledException;
import uk.gov.pay.connector.charge.exception.HttpReturnUrlNotAllowedForLiveGatewayAccountException;
import uk.gov.pay.connector.charge.exception.MotoPaymentNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.exception.ZeroAmountNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.exception.AuthorisationApiNotAllowedForGatewayAccountException;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

class CreateChargeValidations {
    
    static Function<GatewayAccountEntity, Optional<ErrorListMapper.Error>> GATEWAY_ACCOUNT_DISABLED_CHECK = gatewayAccount -> {
        if (gatewayAccount.isDisabled()) {
            return Optional.of(new GatewayAccountDisabledException("Attempt to create a charge for a disabled gateway account"));
        }
        return Optional.empty();
    };
    
    static BiFunction<ChargeCreateRequest, GatewayAccountEntity, Optional<ErrorListMapper.Error>> ZERO_AMOUNT_ALLOWED_CHECK = (request, gatewayAccount) -> {
        if (request.getAmount() == 0L && !gatewayAccount.isAllowZeroAmount()) {
            return Optional.of(new ZeroAmountNotAllowedForGatewayAccountException(gatewayAccount.getId()));
        }
        return Optional.empty();
    };
    
    static BiFunction<ChargeCreateRequest, GatewayAccountEntity, Optional<ErrorListMapper.Error>> RETURN_URL_CHECK = (request, gatewayAccount) -> {
        if (gatewayAccount.isLive() && request.getReturnUrl().isPresent() && !request.getReturnUrl().get().startsWith("https://")) {
            return Optional.of(new HttpReturnUrlNotAllowedForLiveGatewayAccountException(String.format("Gateway account %d is LIVE, but is configured to use a non-https return_url", gatewayAccount.getId())));
        }
        return Optional.empty();
    };
    
    static BiFunction<ChargeCreateRequest, GatewayAccountEntity, Optional<ErrorListMapper.Error>> MOTO_PAYMENT_CHECK = (request, gatewayAccount) -> {
        if (request.getAuthorisationMode() == MOTO_API && !gatewayAccount.isAllowAuthorisationApi()) {
            return Optional.of(new AuthorisationApiNotAllowedForGatewayAccountException(gatewayAccount.getId()));
        }
        if (request.isMoto() && !gatewayAccount.isAllowMoto()) {
            return Optional.of(new MotoPaymentNotAllowedForGatewayAccountException(gatewayAccount.getId()));
        }
        return Optional.empty();
    };
}
