package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.DigitalWalletNotSupportedGatewayException;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountWithoutAnActiveCredentialException;
import uk.gov.pay.connector.gatewayaccount.exception.MissingWorldpay3dsFlexCredentialsEntityException;
import uk.gov.pay.connector.gatewayaccount.exception.NotSupportedGatewayAccountException;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountResourceDTO;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountSearchParams;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.service.payments.commons.model.jsonpatch.JsonPatchRequest;

import javax.inject.Inject;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static java.util.Map.entry;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_ALLOW_APPLE_PAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_ALLOW_AUTHORISATION_API;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_ALLOW_GOOGLE_PAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_ALLOW_MOTO;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_ALLOW_TELEPHONE_PAYMENT_NOTIFICATIONS;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_ALLOW_ZERO_AMOUNT;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_BLOCK_PREPAID_CARDS;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_DISABLED;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_DISABLED_REASON;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_EMAIL_COLLECTION_MODE;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_INTEGRATION_VERSION_3DS;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_MOTO_MASK_CARD_NUMBER_INPUT;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_MOTO_MASK_CARD_SECURITY_CODE_INPUT;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_NOTIFY_SETTINGS;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_PROVIDER_SWITCH_ENABLED;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_RECURRING_ENABLED;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_REQUIRES_ADDITIONAL_KYC_DATA;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_SEND_PAYER_EMAIL_TO_GATEWAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_SEND_PAYER_IP_ADDRESS_TO_GATEWAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_SEND_REFERENCE_TO_GATEWAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class GatewayAccountService {

    private static final Logger logger = LoggerFactory.getLogger(GatewayAccountService.class);

    private final GatewayAccountDao gatewayAccountDao;
    private final CardTypeDao cardTypeDao;
    private final GatewayAccountCredentialsService gatewayAccountCredentialsService;

    @Inject
    public GatewayAccountService(GatewayAccountDao gatewayAccountDao, CardTypeDao cardTypeDao,
                                 GatewayAccountCredentialsService gatewayAccountCredentialsService) {
        this.gatewayAccountDao = gatewayAccountDao;
        this.cardTypeDao = cardTypeDao;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
    }

    public Optional<GatewayAccountEntity> getGatewayAccount(long gatewayAccountId) {
        return gatewayAccountDao.findById(gatewayAccountId);
    }

    public List<GatewayAccountResourceDTO> searchGatewayAccounts(GatewayAccountSearchParams params) {
        return gatewayAccountDao.search(params).stream()
                .map(GatewayAccountResourceDTO::new)
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

        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity,
                gatewayAccountRequest.getPaymentProvider(), gatewayAccountRequest.getCredentialsAsMap());

        return GatewayAccountObjectConverter.createResponseFrom(gatewayAccountEntity, uriInfo);
    }

    public Optional<GatewayAccountEntity> getGatewayAccountByExternal(String gatewayAccountExternalId) {
        return gatewayAccountDao.findByExternalId(gatewayAccountExternalId);
    }

    public boolean isATelephonePaymentNotificationAccount(String merchantCode) {
        return gatewayAccountDao.isATelephonePaymentNotificationAccount(merchantCode);
    }

    private final Map<String, BiConsumer<JsonPatchRequest, GatewayAccountEntity>> attributeUpdater = Map.ofEntries(
            entry(
                    FIELD_ALLOW_GOOGLE_PAY,
                    (gatewayAccountRequest, gatewayAccountEntity) -> {
                        throwIfNotDigitalWalletSupportedGateway(gatewayAccountEntity);
                        gatewayAccountEntity.setAllowGooglePay(Boolean.parseBoolean(gatewayAccountRequest.valueAsString()));
                    }
            ),
            entry(
                    FIELD_ALLOW_APPLE_PAY,
                    (gatewayAccountRequest, gatewayAccountEntity) -> {
                        throwIfNotDigitalWalletSupportedGateway(gatewayAccountEntity);
                        gatewayAccountEntity.setAllowApplePay(Boolean.parseBoolean(gatewayAccountRequest.valueAsString()));
                    }
            ),
            entry(
                    FIELD_NOTIFY_SETTINGS,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setNotifySettings(gatewayAccountRequest.valueAsObject())
            ),
            entry(
                    FIELD_EMAIL_COLLECTION_MODE,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setEmailCollectionMode(EmailCollectionMode.fromString(gatewayAccountRequest.valueAsString()))
            ),
            entry(
                    FIELD_CORPORATE_CREDIT_CARD_SURCHARGE_AMOUNT,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setCorporateCreditCardSurchargeAmount(gatewayAccountRequest.valueAsLong())
            ),
            entry(
                    FIELD_CORPORATE_DEBIT_CARD_SURCHARGE_AMOUNT,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setCorporateDebitCardSurchargeAmount(gatewayAccountRequest.valueAsLong())
            ),
            entry(
                    FIELD_CORPORATE_PREPAID_DEBIT_CARD_SURCHARGE_AMOUNT,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setCorporatePrepaidDebitCardSurchargeAmount(gatewayAccountRequest.valueAsLong())
            ),
            entry(
                    FIELD_ALLOW_ZERO_AMOUNT,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setAllowZeroAmount(Boolean.parseBoolean(gatewayAccountRequest.valueAsString()))
            ),
            entry(
                    FIELD_INTEGRATION_VERSION_3DS,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setIntegrationVersion3ds(gatewayAccountRequest.valueAsInt())
            ),
            entry(
                    FIELD_BLOCK_PREPAID_CARDS,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setBlockPrepaidCards(gatewayAccountRequest.valueAsBoolean())
            ),
            entry(
                    FIELD_ALLOW_MOTO,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setAllowMoto(gatewayAccountRequest.valueAsBoolean())
            ),
            entry(
                    FIELD_MOTO_MASK_CARD_NUMBER_INPUT,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setMotoMaskCardNumberInput(gatewayAccountRequest.valueAsBoolean())
            ),
            entry(
                    FIELD_MOTO_MASK_CARD_SECURITY_CODE_INPUT,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setMotoMaskCardSecurityCodeInput(gatewayAccountRequest.valueAsBoolean())
            ),
            entry(
                    FIELD_ALLOW_TELEPHONE_PAYMENT_NOTIFICATIONS,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setAllowTelephonePaymentNotifications(gatewayAccountRequest.valueAsBoolean())
            ),
            entry(
                    FIELD_SEND_PAYER_IP_ADDRESS_TO_GATEWAY,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setSendPayerIpAddressToGateway(gatewayAccountRequest.valueAsBoolean())
            ),
            entry(
                    FIELD_SEND_PAYER_EMAIL_TO_GATEWAY,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setSendPayerEmailToGateway(gatewayAccountRequest.valueAsBoolean())
            ),
            entry(
                    FIELD_SEND_REFERENCE_TO_GATEWAY,
                    (gatewayAccountRequest, gatewayAccountEntity) -> gatewayAccountEntity.setSendReferenceToGateway(gatewayAccountRequest.valueAsBoolean())
            ),
            entry(
                    FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED,
                    (gatewayAccountRequest, gatewayAccountEntity) -> {
                        throwIfGatewayAccountIsNotWorldpay(gatewayAccountEntity, FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED);
                        var worldpay3dsFlexCredentialsEntity = gatewayAccountEntity.getWorldpay3dsFlexCredentialsEntity()
                                .orElseThrow(() -> new MissingWorldpay3dsFlexCredentialsEntityException(gatewayAccountEntity.getId(), FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED));
                        worldpay3dsFlexCredentialsEntity.setExemptionEngineEnabled(gatewayAccountRequest.valueAsBoolean());
                    }
            ),
            entry(
                    FIELD_PROVIDER_SWITCH_ENABLED,
                    (JsonPatchRequest gatewayAccountRequest, GatewayAccountEntity gatewayAccountEntity) -> {
                        throwIfNoActiveCredentialExist(gatewayAccountEntity);
                        gatewayAccountEntity.setProviderSwitchEnabled(gatewayAccountRequest.valueAsBoolean());

                        if (gatewayAccountRequest.valueAsBoolean()) {
                            logger.info("Enabled switching payment provider for gateway account [id={}]",
                                    gatewayAccountEntity.getId(),
                                    kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()),
                                    kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType()),
                                    kv(PROVIDER, gatewayAccountEntity.getGatewayName()));
                        }
                    }
            ),
            entry(FIELD_REQUIRES_ADDITIONAL_KYC_DATA, (gatewayAccountRequest, gatewayAccountEntity) ->
                    gatewayAccountEntity.setRequiresAdditionalKycData(gatewayAccountRequest.valueAsBoolean())),
            entry(FIELD_ALLOW_AUTHORISATION_API, (gatewayAccountRequest, gatewayAccountEntity) ->
                    gatewayAccountEntity.setAllowAuthorisationApi(gatewayAccountRequest.valueAsBoolean())),
            entry(FIELD_RECURRING_ENABLED, (gatewayAccountRequest, gatewayAccountEntity) ->
                    gatewayAccountEntity.setRecurringEnabled(gatewayAccountRequest.valueAsBoolean())),
            entry(FIELD_DISABLED, (gatewayAccountRequest, gatewayAccountEntity) ->
                    gatewayAccountEntity.setDisabled(gatewayAccountRequest.valueAsBoolean())),
            entry(FIELD_DISABLED_REASON, (gatewayAccountRequest, gatewayAccountEntity) ->
                    gatewayAccountEntity.setDisabledReason(gatewayAccountRequest.valueAsString()))
    );

    private void throwIfNoActiveCredentialExist(GatewayAccountEntity gatewayAccountEntity) {
        if (!gatewayAccountCredentialsService.hasActiveCredentials(gatewayAccountEntity.getId())) {
            throw new GatewayAccountWithoutAnActiveCredentialException(gatewayAccountEntity.getId());
        }
    }

    private void throwIfNotDigitalWalletSupportedGateway(GatewayAccountEntity gatewayAccountEntity) {
        if (!WORLDPAY.getName().equals(gatewayAccountEntity.getGatewayName())) {
            throw new DigitalWalletNotSupportedGatewayException(gatewayAccountEntity.getGatewayName());
        }
    }

    private void throwIfGatewayAccountIsNotWorldpay(GatewayAccountEntity gatewayAccountEntity, String path) {
        if (!WORLDPAY.getName().equals(gatewayAccountEntity.getGatewayName())) {
            throw new NotSupportedGatewayAccountException(gatewayAccountEntity.getId(), WORLDPAY.getName(), path);
        }
    }
}
