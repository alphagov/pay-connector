package uk.gov.pay.connector.filters;

import org.slf4j.MDC;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import java.util.Optional;

import static uk.gov.pay.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.pay.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.pay.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.pay.logging.LoggingKeys.PROVIDER;
import static uk.gov.pay.logging.LoggingKeys.REFUND_EXTERNAL_ID;
import static uk.gov.pay.logging.LoggingKeys.SECURE_TOKEN;

public class LoggingMDCRequestFilter implements ContainerRequestFilter {

    private final ChargeService chargeService;
    private final GatewayAccountService gatewayAccountService;

    @Inject
    public LoggingMDCRequestFilter(ChargeService chargeService, GatewayAccountService gatewayAccountService) {
        this.chargeService = chargeService;
        this.gatewayAccountService = gatewayAccountService;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        var chargeEntity = getChargeFromRequest(requestContext);
        var gatewayAccountEntity = chargeEntity.map(ChargeEntity::getGatewayAccount)
                .or(() -> getAccountFromRequest(requestContext));

        chargeEntity.ifPresent(charge -> MDC.put(PAYMENT_EXTERNAL_ID, charge.getExternalId()));
        gatewayAccountEntity.ifPresent(gatewayAccount -> {
            MDC.put(GATEWAY_ACCOUNT_ID, gatewayAccount.getId().toString());
            MDC.put(PROVIDER, gatewayAccount.getGatewayName());
            MDC.put(GATEWAY_ACCOUNT_TYPE, gatewayAccount.getType());
        });

        getPathParameterFromRequest("refundId", requestContext)
                .ifPresent(refund -> MDC.put(REFUND_EXTERNAL_ID, refund));

        getPathParameterFromRequest("chargeTokenId", requestContext)
                .ifPresent(token -> MDC.put(SECURE_TOKEN, token));
    }

    private Optional<ChargeEntity> getChargeFromRequest(ContainerRequestContext requestContext) {
        try {
            return getPathParameterFromRequest("chargeId", requestContext)
                .map(chargeService::findChargeById);
        } catch (ChargeNotFoundRuntimeException ex) {
            return Optional.empty();
        }
    }

    private Optional<GatewayAccountEntity> getAccountFromRequest(ContainerRequestContext requestContext) {
        return getPathParameterFromRequest("accountId", requestContext)
                .flatMap(this::safelyConvertToLong)
                .flatMap(gatewayAccountService::getGatewayAccount);
    }

    private Optional<String> getPathParameterFromRequest(String parameterName, ContainerRequestContext requestContext) {
        return Optional.ofNullable(requestContext.getUriInfo().getPathParameters().getFirst(parameterName));
    }

    private Optional<Long> safelyConvertToLong(String value) {
        try {
            return Optional.of(Long.valueOf(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
