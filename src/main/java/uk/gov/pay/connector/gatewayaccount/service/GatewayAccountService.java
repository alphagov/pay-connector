package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchRequest;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.DigitalWalletNotSupportedGatewayException;
import uk.gov.pay.connector.gatewayaccount.exception.MerchantIdWithoutCredentialsException;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountResourceDTO;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountResponse;

import javax.inject.Inject;
import javax.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.CREDENTIALS_GATEWAY_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_ALLOW_APPLE_PAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_ALLOW_GOOGLE_PAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_ALLOW_ZERO_AMOUNT;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_CORPORATE_PREPAID_CREDIT_CARD_SURCHARGE_AMOUNT;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_EMAIL_COLLECTION_MODE;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_INTEGRATION_VERSION_3DS;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_NOTIFY_SETTINGS;

public class GatewayAccountService {

    private static final Logger logger = LoggerFactory.getLogger(GatewayAccountService.class);

    private final GatewayAccountDao gatewayAccountDao;
    private final CardTypeDao cardTypeDao;

    @Inject
    public GatewayAccountService(GatewayAccountDao gatewayAccountDao, CardTypeDao cardTypeDao) {
        this.gatewayAccountDao = gatewayAccountDao;
        this.cardTypeDao = cardTypeDao;
    }

    public Optional<GatewayAccountEntity> getGatewayAccount(long gatewayAccountId) {
        return gatewayAccountDao.findById(gatewayAccountId);
    }

    public List<GatewayAccountResourceDTO> getAllGatewayAccounts() {
        return gatewayAccountDao.listAll().stream()
                .map(GatewayAccountResourceDTO::fromEntity)
                .collect(Collectors.toList());
    }

    public List<GatewayAccountResourceDTO> getGatewayAccounts(List<Long> gatewayAccountIds) {
        return gatewayAccountDao.list(gatewayAccountIds).stream()
                .map(GatewayAccountResourceDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public Optional<GatewayAccount> doPatch(Long gatewayAccountId, JsonPatchRequest gatewayAccountRequest) {
        return gatewayAccountDao.findById(gatewayAccountId)
                .flatMap(gatewayAccountEntity -> {
                    attributeUpdater.get(gatewayAccountRequest.getPath())
                            .accept(gatewayAccountRequest, gatewayAccountEntity);
                    gatewayAccountDao.merge(gatewayAccountEntity);
                    return Optional.of(GatewayAccount.valueOf(gatewayAccountEntity));
                });
    }

    @Transactional
    public GatewayAccountResponse createGatewayAccount(GatewayAccountRequest gatewayAccountRequest, UriInfo uriInfo) {

        GatewayAccountEntity gatewayAccountEntity = GatewayAccountObjectConverter.createEntityFrom(gatewayAccountRequest);

        logger.info("Setting the new account to accept all card types by default");

        gatewayAccountEntity.setCardTypes(cardTypeDao.findAllNon3ds());

        gatewayAccountDao.persist(gatewayAccountEntity);

        return GatewayAccountObjectConverter.createResponseFrom(gatewayAccountEntity, uriInfo);
    }

    private final Map<String, BiConsumer<JsonPatchRequest, GatewayAccountEntity>> attributeUpdater =
            new HashMap<>() {{
                put(CREDENTIALS_GATEWAY_MERCHANT_ID,
                        (gatewayAccountRequest, gatewayAccountEntity) -> {
                            Map<String, String> credentials = gatewayAccountEntity.getCredentials();
                            if (credentials.isEmpty()) {
                                throw new MerchantIdWithoutCredentialsException();
                            }
                            throwIfNotDigitalWalletSupportedGateway(gatewayAccountEntity);
                            Map<String, String> updatedCredentials = new HashMap<>(credentials);
                            updatedCredentials.put("gateway_merchant_id", gatewayAccountRequest.valueAsString());
                            gatewayAccountEntity.setCredentials(updatedCredentials);
                        });
                put(FIELD_ALLOW_GOOGLE_PAY,
                        (gatewayAccountRequest, gatewayAccountEntity) -> {
                            throwIfNotDigitalWalletSupportedGateway(gatewayAccountEntity);
                            gatewayAccountEntity.setAllowGooglePay(Boolean.valueOf(gatewayAccountRequest.valueAsString()));
                        });
                put(FIELD_ALLOW_APPLE_PAY,
                        (gatewayAccountRequest, gatewayAccountEntity) -> {
                            throwIfNotDigitalWalletSupportedGateway(gatewayAccountEntity);
                            gatewayAccountEntity.setAllowApplePay(Boolean.valueOf(gatewayAccountRequest.valueAsString()));
                        });
                put(FIELD_NOTIFY_SETTINGS,
                        (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setNotifySettings(gatewayAccountRequest.valueAsObject()));
                put(FIELD_EMAIL_COLLECTION_MODE,
                        (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setEmailCollectionMode(EmailCollectionMode.fromString(gatewayAccountRequest.valueAsString())));
                put(FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT,
                        (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setCorporateCreditCardSurchargeAmount(gatewayAccountRequest.valueAsLong()));
                put(FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT,
                        (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setCorporateDebitCardSurchargeAmount(gatewayAccountRequest.valueAsLong()));
                put(FIELD_CORPORATE_PREPAID_CREDIT_CARD_SURCHARGE_AMOUNT,
                        (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setCorporatePrepaidCreditCardSurchargeAmount(gatewayAccountRequest.valueAsLong()));
                put(FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT,
                        (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setCorporatePrepaidDebitCardSurchargeAmount(gatewayAccountRequest.valueAsLong()));
                put(FIELD_ALLOW_ZERO_AMOUNT,
                        (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setAllowZeroAmount(Boolean.valueOf(gatewayAccountRequest.valueAsString())));
                put(FIELD_INTEGRATION_VERSION_3DS,
                        (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setIntegrationVersion3ds(gatewayAccountRequest.valueAsInt()));
            }};

    private void throwIfNotDigitalWalletSupportedGateway(GatewayAccountEntity gatewayAccountEntity) {
        if (!PaymentGatewayName.WORLDPAY.getName().equals(gatewayAccountEntity.getGatewayName())) {
            throw new DigitalWalletNotSupportedGatewayException(gatewayAccountEntity.getGatewayName());
        }
    }
}
