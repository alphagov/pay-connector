package uk.gov.pay.connector.gatewayaccount.model;

import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

public final class GatewayAccountEntityFixture {
    private Long id = 1L;
    private String gatewayName = SANDBOX.getName();
    private GatewayAccountType type = TEST;
    private Map<String, Object> credentials = Map.of();
    private String serviceName = "Test Service";
    private String description;
    private String analyticsId;
    private boolean requires3ds;
    private boolean allowGooglePay;
    private boolean allowApplePay;

    private boolean allowMoto;
    private long corporateCreditCardSurchargeAmount;
    private long corporateDebitCardSurchargeAmount;
    private long corporatePrepaidDebitCardSurchargeAmount;
    private boolean allowZeroAmount;
    private int integrationVersion3ds;
    private Map<String, String> notifySettings;
    private Map<EmailNotificationType, EmailNotificationEntity> emailNotifications = new HashMap<>();
    private EmailCollectionMode emailCollectionMode = EmailCollectionMode.MANDATORY;
    private List<CardTypeEntity> cardTypes = newArrayList();
    private Worldpay3dsFlexCredentialsEntity worldpay3dsFlexCredentialsEntity;
    private boolean sendPayerIpAddressToGateway;
    private List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntities = new ArrayList<>();
    private boolean providerSwitchEnabled = false;
    private boolean blockPrepaidCards;
    private boolean disabled = false;

    private GatewayAccountEntityFixture() {
    }

    public static GatewayAccountEntityFixture aGatewayAccountEntity() {
        return new GatewayAccountEntityFixture();
    }

    public GatewayAccountEntityFixture withId(Long id) {
        this.id = id;
        return this;
    }

    public GatewayAccountEntityFixture withGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
        return this;
    }

    public GatewayAccountEntityFixture withType(GatewayAccountType type) {
        this.type = type;
        return this;
    }

    public GatewayAccountEntityFixture withCredentials(Map<String, Object> credentials) {
        this.credentials = credentials;
        return this;
    }

    public GatewayAccountEntityFixture withServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    public GatewayAccountEntityFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public GatewayAccountEntityFixture withAnalyticsId(String analyticsId) {
        this.analyticsId = analyticsId;
        return this;
    }

    public GatewayAccountEntityFixture withRequires3ds(boolean requires3ds) {
        this.requires3ds = requires3ds;
        return this;
    }

    public GatewayAccountEntityFixture withAllowGooglePay(boolean allowGooglePay) {
        this.allowGooglePay = allowGooglePay;
        return this;
    }

    public GatewayAccountEntityFixture withAllowApplePay(boolean allowApplePay) {
        this.allowApplePay = allowApplePay;
        return this;
    }

    public GatewayAccountEntityFixture withAllowMoto(boolean allowMoto) {
        this.allowMoto = allowMoto;
        return this;
    }

    public GatewayAccountEntityFixture withCorporateCreditCardSurchargeAmount(long corporateCreditCardSurchargeAmount) {
        this.corporateCreditCardSurchargeAmount = corporateCreditCardSurchargeAmount;
        return this;
    }

    public GatewayAccountEntityFixture withCorporateDebitCardSurchargeAmount(long corporateDebitCardSurchargeAmount) {
        this.corporateDebitCardSurchargeAmount = corporateDebitCardSurchargeAmount;
        return this;
    }

    public GatewayAccountEntityFixture withCorporatePrepaidDebitCardSurchargeAmount(long corporatePrepaidDebitCardSurchargeAmount) {
        this.corporatePrepaidDebitCardSurchargeAmount = corporatePrepaidDebitCardSurchargeAmount;
        return this;
    }

    public GatewayAccountEntityFixture withAllowZeroAmount(boolean allowZeroAmount) {
        this.allowZeroAmount = allowZeroAmount;
        return this;
    }

    public GatewayAccountEntityFixture withIntegrationVersion3ds(int integrationVersion3ds) {
        this.integrationVersion3ds = integrationVersion3ds;
        return this;
    }

    public GatewayAccountEntityFixture withNotifySettings(Map<String, String> notifySettings) {
        this.notifySettings = notifySettings;
        return this;
    }

    public GatewayAccountEntityFixture withEmailNotifications(Map<EmailNotificationType, EmailNotificationEntity> emailNotifications) {
        this.emailNotifications = emailNotifications;
        return this;
    }

    public GatewayAccountEntityFixture withEmailCollectionMode(EmailCollectionMode emailCollectionMode) {
        this.emailCollectionMode = emailCollectionMode;
        return this;
    }

    public GatewayAccountEntityFixture withCardTypes(List<CardTypeEntity> cardTypes) {
        this.cardTypes = cardTypes;
        return this;
    }

    public GatewayAccountEntityFixture withWorldpay3dsFlexCredentialsEntity(Worldpay3dsFlexCredentialsEntity worldpay3dsFlexCredentialsEntity) {
        this.worldpay3dsFlexCredentialsEntity = worldpay3dsFlexCredentialsEntity;
        return this;
    }

    public GatewayAccountEntityFixture withSendPayerIpAddressToGateway(boolean sendPayerIpAddressToGateway) {
        this.sendPayerIpAddressToGateway = sendPayerIpAddressToGateway;
        return this;
    }

    public GatewayAccountEntityFixture withGatewayAccountCredentials(List<GatewayAccountCredentialsEntity> credentials) {
        this.gatewayAccountCredentialsEntities = credentials;
        return this;
    }
    
    public GatewayAccountEntityFixture withActiveStripeGatewayAccountCredentials() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withState(GatewayAccountCredentialState.ACTIVE)
                .withPaymentProvider(WORLDPAY.getName())
                .withStripeCredentials()
                .build();
        this.gatewayAccountCredentialsEntities = List.of(credentialsEntity);
        return this;
    }

    public GatewayAccountEntityFixture withProviderSwitchEnabled(boolean providerSwitchEnabled) {
        this.providerSwitchEnabled = providerSwitchEnabled;
        return this;
    }

    public GatewayAccountEntityFixture withBlockPrepaidCards(boolean blockPrepaidCards) {
        this.blockPrepaidCards = blockPrepaidCards;
        return this;
    }
    
    public GatewayAccountEntityFixture withDisabled(boolean disabled) {
        this.disabled = disabled;
        return this;
    }

    public GatewayAccountEntity build() {
        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity();
        gatewayAccountEntity.setId(id);
        gatewayAccountEntity.setType(type);
        gatewayAccountEntity.setServiceName(serviceName);
        gatewayAccountEntity.setDescription(description);
        gatewayAccountEntity.setAnalyticsId(analyticsId);
        gatewayAccountEntity.setRequires3ds(requires3ds);
        gatewayAccountEntity.setAllowGooglePay(allowGooglePay);
        gatewayAccountEntity.setAllowApplePay(allowApplePay);
        gatewayAccountEntity.setCorporateCreditCardSurchargeAmount(corporateCreditCardSurchargeAmount);
        gatewayAccountEntity.setCorporateDebitCardSurchargeAmount(corporateDebitCardSurchargeAmount);
        gatewayAccountEntity.setCorporatePrepaidDebitCardSurchargeAmount(corporatePrepaidDebitCardSurchargeAmount);
        gatewayAccountEntity.setAllowZeroAmount(allowZeroAmount);
        gatewayAccountEntity.setIntegrationVersion3ds(integrationVersion3ds);
        gatewayAccountEntity.setNotifySettings(notifySettings);
        gatewayAccountEntity.setEmailNotifications(emailNotifications);
        gatewayAccountEntity.setEmailCollectionMode(emailCollectionMode);
        gatewayAccountEntity.setCardTypes(cardTypes);
        gatewayAccountEntity.setWorldpay3dsFlexCredentialsEntity(worldpay3dsFlexCredentialsEntity);
        gatewayAccountEntity.setSendPayerIpAddressToGateway(sendPayerIpAddressToGateway);
        gatewayAccountEntity.setProviderSwitchEnabled(providerSwitchEnabled);
        gatewayAccountEntity.setBlockPrepaidCards(blockPrepaidCards);
        gatewayAccountEntity.setDisabled(disabled);
        gatewayAccountEntity.setAllowMoto(allowMoto);

        if (credentials != null && !credentials.isEmpty() && gatewayAccountCredentialsEntities != null
                && gatewayAccountCredentialsEntities.isEmpty()) {
            GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                    .withPaymentProvider(gatewayName)
                    .withCredentials(credentials)
                    .build();
            gatewayAccountCredentialsEntities.add(credentialsEntity);
        }

        gatewayAccountEntity.setGatewayAccountCredentials(gatewayAccountCredentialsEntities);
        return gatewayAccountEntity;
    }
}
