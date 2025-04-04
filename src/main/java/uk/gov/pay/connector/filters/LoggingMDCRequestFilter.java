package uk.gov.pay.connector.filters;

import org.slf4j.MDC;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.util.MDCUtils;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import java.util.Optional;

import static uk.gov.service.payments.logging.LoggingKeys.AGREEMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER_PAYMENT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.REFUND_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.SECURE_TOKEN;

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

        // Fields are removed from the MDC when the API responds in LoggingMDCResponseFilter
        chargeEntity.ifPresentOrElse(MDCUtils::addChargeAndGatewayAccountDetailsToMDC,
                () -> getAccountFromRequest(requestContext).ifPresent(MDCUtils::addGatewayAccountDetailsToMDC));

        getPathParameterFromRequest("refundId", requestContext)
                .ifPresent(refund -> MDC.put(REFUND_EXTERNAL_ID, refund));

        getPathParameterFromRequest("chargeTokenId", requestContext)
                .ifPresent(token -> MDC.put(SECURE_TOKEN, token));

        getPathParameterFromRequest("gatewayTransactionId", requestContext)
                .ifPresent(gatewayTxId -> MDC.put(PROVIDER_PAYMENT_ID, gatewayTxId));
        
        getPathParameterFromRequest("agreementId", requestContext)
                .ifPresent(agreementId -> MDC.put(AGREEMENT_EXTERNAL_ID, agreementId));
    }

    private Optional<ChargeEntity> getChargeFromRequest(ContainerRequestContext requestContext) {
        try {
            return getPathParameterFromRequest("chargeId", requestContext)
                    .map(chargeService::findChargeByExternalId);
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
