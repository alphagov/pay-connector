package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountWithoutAnActiveCredentialException;
import uk.gov.pay.connector.gatewayaccount.exception.MissingWorldpay3dsFlexCredentialsEntityException;
import uk.gov.pay.connector.gatewayaccount.exception.NotSupportedGatewayAccountException;
import uk.gov.pay.connector.gatewayaccount.model.CreateGatewayAccountResponse;
import uk.gov.pay.connector.gatewayaccount.model.EmailCollectionMode;
import uk.gov.pay.connector.gatewayaccount.model.EpdqCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountSearchParams;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsDao;
import uk.gov.pay.connector.gatewayaccountcredentials.dao.GatewayAccountCredentialsHistoryDao;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.service.payments.commons.model.jsonpatch.JsonPatchRequest;

import javax.inject.Inject;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Map.entry;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
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
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_SEND_PAYER_EMAIL_TO_GATEWAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_SEND_PAYER_IP_ADDRESS_TO_GATEWAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_SEND_REFERENCE_TO_GATEWAY;
import static uk.gov.pay.connector.gatewayaccount.resource.GatewayAccountRequestValidator.FIELD_WORLDPAY_EXEMPTION_ENGINE_ENABLED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.RETIRED;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;
import static uk.gov.service.payments.logging.LoggingKeys.SERVICE_EXTERNAL_ID;

public class GatewayAccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAccountService.class);

    private final GatewayAccountDao gatewayAccountDao;
    private final CardTypeDao cardTypeDao;
    private final GatewayAccountCredentialsService gatewayAccountCredentialsService;
    private final GatewayAccountCredentialsHistoryDao gatewayAccountCredentialsHistoryDao;
    private final GatewayAccountCredentialsDao gatewayAccountCredentialsDao;

    @Inject
    public GatewayAccountService(GatewayAccountDao gatewayAccountDao, CardTypeDao cardTypeDao,
                                 GatewayAccountCredentialsService gatewayAccountCredentialsService, 
                                 GatewayAccountCredentialsHistoryDao gatewayAccountCredentialsHistoryDao, 
                                 GatewayAccountCredentialsDao gatewayAccountCredentialsDao) {
        this.gatewayAccountDao = gatewayAccountDao;
        this.cardTypeDao = cardTypeDao;
        this.gatewayAccountCredentialsService = gatewayAccountCredentialsService;
        this.gatewayAccountCredentialsHistoryDao = gatewayAccountCredentialsHistoryDao;
        this.gatewayAccountCredentialsDao = gatewayAccountCredentialsDao;
    }

    public Optional<GatewayAccountEntity> getGatewayAccount(long gatewayAccountId) {
        return gatewayAccountDao.findById(gatewayAccountId);
    }

    public List<GatewayAccountEntity> searchGatewayAccounts(GatewayAccountSearchParams params) {
        return gatewayAccountDao.search(params);
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
    public Optional<GatewayAccount> doPatch(String serviceId, GatewayAccountType accountType, JsonPatchRequest gatewayAccountRequest) {
        return gatewayAccountDao.findByServiceIdAndAccountType(serviceId, accountType)
                .flatMap(gatewayAccountEntity -> {
                    attributeUpdater.get(gatewayAccountRequest.getPath())
                            .accept(gatewayAccountRequest, gatewayAccountEntity);
                    gatewayAccountDao.merge(gatewayAccountEntity);
                    return Optional.of(GatewayAccount.valueOf(gatewayAccountEntity));
                });
    }

    @Transactional
    public CreateGatewayAccountResponse createGatewayAccount(GatewayAccountRequest gatewayAccountRequest, UriInfo uriInfo) {

        GatewayAccountEntity gatewayAccountEntity = GatewayAccountObjectConverter.createEntityFrom(gatewayAccountRequest);

        LOGGER.info("Setting the new account to accept all card types by default");

        gatewayAccountEntity.setCardTypes(cardTypeDao.findAllNon3ds());

        if (SANDBOX.getName().equalsIgnoreCase(gatewayAccountRequest.getPaymentProvider())) {
            gatewayAccountEntity.setAllowApplePay(true);
        }

        gatewayAccountDao.persist(gatewayAccountEntity);

        gatewayAccountCredentialsService.createGatewayAccountCredentials(gatewayAccountEntity,
                gatewayAccountRequest.getPaymentProvider(), gatewayAccountRequest.getCredentialsAsMap());

        return GatewayAccountObjectConverter.createResponseFrom(gatewayAccountEntity, uriInfo);
    }

    public Optional<GatewayAccountEntity> getGatewayAccountByExternal(String gatewayAccountExternalId) {
        return gatewayAccountDao.findByExternalId(gatewayAccountExternalId);
    }

    public Optional<GatewayAccountEntity> getGatewayAccountByServiceIdAndAccountType(String serviceId, GatewayAccountType accountType) {
        return gatewayAccountDao.findByServiceIdAndAccountType(serviceId, accountType);
    }

    public boolean isATelephonePaymentNotificationAccount(String merchantCode) {
        return gatewayAccountDao.isATelephonePaymentNotificationAccount(merchantCode);
    }

    private final Map<String, BiConsumer<JsonPatchRequest, GatewayAccountEntity>> attributeUpdater = Map.ofEntries(
            entry(
                    FIELD_ALLOW_GOOGLE_PAY,
                    (gatewayAccountRequest, gatewayAccountEntity) -> {
                        gatewayAccountEntity.setAllowGooglePay(Boolean.parseBoolean(gatewayAccountRequest.valueAsString()));
                    }
            ),
            entry(
                    FIELD_ALLOW_APPLE_PAY,
                    (gatewayAccountRequest, gatewayAccountEntity) -> {
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
                            LOGGER.info("Enabled switching payment provider for gateway account [id={}]",
                                    gatewayAccountEntity.getId(),
                                    kv(GATEWAY_ACCOUNT_ID, gatewayAccountEntity.getId()),
                                    kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountEntity.getType()),
                                    kv(PROVIDER, gatewayAccountEntity.getGatewayName()));
                        }
                    }
            ),
            entry(FIELD_ALLOW_AUTHORISATION_API, (gatewayAccountRequest, gatewayAccountEntity) ->
                    gatewayAccountEntity.setAllowAuthorisationApi(gatewayAccountRequest.valueAsBoolean())),
            entry(FIELD_RECURRING_ENABLED, (gatewayAccountRequest, gatewayAccountEntity) ->
                    gatewayAccountEntity.setRecurringEnabled(gatewayAccountRequest.valueAsBoolean())),
            entry(FIELD_DISABLED, (gatewayAccountRequest, gatewayAccountEntity) -> {
                boolean disable = gatewayAccountRequest.valueAsBoolean();
                gatewayAccountEntity.setDisabled(disable);
                if (!disable) {
                    gatewayAccountEntity.setDisabledReason(null);
                }
                LOGGER.info("Gateway account {}", disable ? "disabled" : "re-enabled");
            }),
            entry(FIELD_DISABLED_REASON, (gatewayAccountRequest, gatewayAccountEntity) ->
                    gatewayAccountEntity.setDisabledReason(gatewayAccountRequest.valueAsString()))
    );

    private void throwIfNoActiveCredentialExist(GatewayAccountEntity gatewayAccountEntity) {
        if (!gatewayAccountCredentialsService.hasActiveCredentials(gatewayAccountEntity.getId())) {
            throw new GatewayAccountWithoutAnActiveCredentialException(gatewayAccountEntity.getId());
        }
    }

    private void throwIfGatewayAccountIsNotWorldpay(GatewayAccountEntity gatewayAccountEntity, String path) {
        if (!WORLDPAY.getName().equals(gatewayAccountEntity.getGatewayName())) {
            throw new NotSupportedGatewayAccountException(gatewayAccountEntity.getId(), WORLDPAY.getName(), path);
        }
    }

    @Transactional
    public void disableAccountsAndRedactOrDeleteCredentials(String serviceId) {
        List<GatewayAccountEntity> gatewayAccounts = gatewayAccountDao.findByServiceId(serviceId);
        
        LOGGER.info(format("Disabling gateway accounts %s for service.", gatewayAccounts.stream().map(GatewayAccountEntity::getExternalId).collect(Collectors.joining(","))),
                kv(SERVICE_EXTERNAL_ID, serviceId));
        
        gatewayAccounts.forEach(ga -> {
            ga.setDisabled(true);
            ga.setNotificationCredentials(null);
            gatewayAccountDao.merge(ga);
            ga.getGatewayAccountCredentials().forEach(creds -> {
                creds.setState(RETIRED);
                switch (PaymentGatewayName.valueFrom(creds.getPaymentProvider())) {
                    case WORLDPAY:
                        WorldpayCredentials worldpayCredentials = (WorldpayCredentials) creds.getCredentialsObject();
                        worldpayCredentials.getRecurringCustomerInitiatedCredentials().ifPresent(WorldpayMerchantCodeCredentials::redactSensitiveInformation);
                        worldpayCredentials.getOneOffCustomerInitiatedCredentials().ifPresent(WorldpayMerchantCodeCredentials::redactSensitiveInformation);
                        worldpayCredentials.getRecurringMerchantInitiatedCredentials().ifPresent(WorldpayMerchantCodeCredentials::redactSensitiveInformation);
                        creds.setCredentials(worldpayCredentials);
                        gatewayAccountCredentialsDao.merge(creds);
                        LOGGER.info("Credentials redacted",
                                kv(SERVICE_EXTERNAL_ID, serviceId),
                                kv("credential_external_id", creds.getExternalId()),
                                kv(GATEWAY_ACCOUNT_ID, ga.getExternalId()),
                                kv(PROVIDER, creds.getPaymentProvider()));
                        break;
                    case EPDQ:
                        EpdqCredentials epdqCredentials = (EpdqCredentials) creds.getCredentialsObject();
                        epdqCredentials.setUsername("<DELETED>");
                        epdqCredentials.setPassword("<DELETED>");
                        creds.setCredentials(epdqCredentials);
                        gatewayAccountCredentialsDao.merge(creds);
                        LOGGER.info("Credentials redacted",
                                kv(SERVICE_EXTERNAL_ID, serviceId),
                                kv("credential_external_id", creds.getExternalId()),
                                kv(GATEWAY_ACCOUNT_ID, ga.getExternalId()),
                                kv(PROVIDER, creds.getPaymentProvider()));
                        break;
                    default:
                        LOGGER.info("No credentials to redact.",
                                kv(SERVICE_EXTERNAL_ID, serviceId),
                                kv("credential_external_id", creds.getExternalId()),
                                kv(GATEWAY_ACCOUNT_ID, ga.getExternalId()),
                                kv(PROVIDER, creds.getPaymentProvider()));
                }
            });
        });
        gatewayAccountCredentialsHistoryDao.delete(serviceId);
    }
}
