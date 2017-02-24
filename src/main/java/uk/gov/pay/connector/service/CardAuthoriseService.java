package uk.gov.pay.connector.service;

import com.google.inject.persist.Transactional;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.dao.ChargeDao;
import uk.gov.pay.connector.model.domain.*;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;

import javax.inject.Inject;
import java.util.Optional;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class CardAuthoriseService extends CardAuthoriseBaseService<AuthCardDetails> {

    private final Auth3dsDetailsFactory auth3dsDetailsFactory;

    @Inject
    public CardAuthoriseService(ChargeDao chargeDao,
                                PaymentProviders providers,
                                CardExecutorService cardExecutorService,
                                Auth3dsDetailsFactory auth3dsDetailsFactory,
                                Environment environment) {
        super(chargeDao, providers, cardExecutorService, environment);

        this.auth3dsDetailsFactory = auth3dsDetailsFactory;
    }

    @Transactional
    public ChargeEntity preOperation(ChargeEntity chargeEntity) {
        chargeEntity = preOperation(chargeEntity, OperationType.AUTHORISATION, getLegalStates(), AUTHORISATION_READY);
        getPaymentProviderFor(chargeEntity).generateTransactionId().ifPresent(chargeEntity::setGatewayTransactionId);
        return chargeEntity;
    }

    public GatewayResponse<BaseAuthoriseResponse> operation(ChargeEntity chargeEntity, AuthCardDetails authCardDetails) {
        return getPaymentProviderFor(chargeEntity)
                .authorise(AuthorisationGatewayRequest.valueOf(chargeEntity, authCardDetails));
    }

    @Override
    protected ChargeStatus[] getLegalStates() {
        return new ChargeStatus[]{
                ENTERING_CARD_DETAILS
        };
    }

    @Transactional
    public GatewayResponse<BaseAuthoriseResponse> postOperation(ChargeEntity chargeEntity, AuthCardDetails authCardDetails, GatewayResponse<BaseAuthoriseResponse> operationResponse) {
        ChargeEntity reloadedCharge = chargeDao.merge(chargeEntity);

        ChargeStatus status = operationResponse.getBaseResponse()
                .map(BaseAuthoriseResponse::authoriseStatus)
                .map(BaseAuthoriseResponse.AuthoriseStatus::getMappedChargeStatus)
                .orElse(ChargeStatus.AUTHORISATION_ERROR);

        String transactionId = operationResponse.getBaseResponse()
                .map(BaseAuthoriseResponse::getTransactionId).orElse("");

        operationResponse.getSessionIdentifier().ifPresent(reloadedCharge::setProviderSessionId);

        logger.info("AuthCardDetails authorisation response received - charge_external_id={}, operation_type={}, transaction_id={}, status={}",
                chargeEntity.getExternalId(), OperationType.AUTHORISATION.getValue(), transactionId, status);

        GatewayAccountEntity account = chargeEntity.getGatewayAccount();

        metricRegistry.counter(String.format("gateway-operations.%s.%s.%s.authorise.result.%s", account.getGatewayName(), account.getType(), account.getId(), status.toString())).inc();

        reloadedCharge.setStatus(status);
        operationResponse.getBaseResponse().ifPresent(response -> auth3dsDetailsFactory.create(response).ifPresent(reloadedCharge::set3dsDetails));

        if (StringUtils.isBlank(transactionId)) {
            logger.warn("AuthCardDetails authorisation response received with no transaction id. -  charge_external_id={}", reloadedCharge.getExternalId());
        } else {
            reloadedCharge.setGatewayTransactionId(transactionId);
        }

        appendCardDetails(reloadedCharge, authCardDetails);
        chargeDao.mergeAndNotifyStatusHasChanged(reloadedCharge, Optional.empty());
        return operationResponse;
    }

    private void appendCardDetails(ChargeEntity chargeEntity, AuthCardDetails authCardDetails) {
        CardDetailsEntity detailsEntity = new CardDetailsEntity();
        detailsEntity.setCardBrand(authCardDetails.getCardBrand());
        detailsEntity.setBillingAddress(new AddressEntity(authCardDetails.getAddress()));
        detailsEntity.setCardHolderName(authCardDetails.getCardHolder());
        detailsEntity.setExpiryDate(authCardDetails.getEndDate());
        detailsEntity.setLastDigitsCardNumber(StringUtils.right(authCardDetails.getCardNo(), 4));
        chargeEntity.setCardDetails(detailsEntity);
        logger.info("Stored confirmation details for charge - charge_external_id={}", chargeEntity.getExternalId());
    }
}
