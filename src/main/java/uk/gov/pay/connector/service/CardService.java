package uk.gov.pay.connector.service;

import fj.data.Either;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.dao.GatewayAccountDao;
import uk.gov.pay.connector.model.ErrorResponse;
import uk.gov.pay.connector.model.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.function.Supplier;

import static fj.data.Either.left;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static uk.gov.pay.connector.model.ErrorType.CHARGE_NOT_FOUND;

public abstract class CardService {

    protected final GatewayAccountDao accountDao;
    protected final ChargeDao chargeDao;
    protected final PaymentProviders providers;

    @Inject
    public CardService(GatewayAccountDao accountDao, ChargeDao chargeDao, PaymentProviders providers) {
        this.accountDao = accountDao;
        this.chargeDao = chargeDao;
        this.providers = providers;
    }

    protected PaymentProvider paymentProviderFor(ChargeEntity charge) {
        return providers.resolve(charge.getGatewayAccount().getGatewayName());
    }

    protected boolean hasStatus(ChargeEntity charge, ChargeStatus... states) {
        return Arrays.stream(states)
                .anyMatch(status -> equalsIgnoreCase(status.getValue(), charge.getStatus()));
    }

    protected Supplier<Either<ErrorResponse, GatewayResponse>> chargeNotFound(String chargeId) {
        return () -> left(new ErrorResponse(format("Charge with id [%s] not found.", chargeId), CHARGE_NOT_FOUND));
    }
}
