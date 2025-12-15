package uk.gov.pay.connector.charge.model.domain;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.service.payments.commons.model.AgreementPaymentType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.Source;
import uk.gov.service.payments.commons.model.SupportedLanguage;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.CREATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class ChargeEntityFixture {

    private Long id = secureRandomLong();
    private String externalId = RandomIdGenerator.newId();
    private Long amount = 500L;
    private String returnUrl = "http://return.invalid";
    private String email = "test@email.invalid";
    private String description = "This is a description";
    private ServicePaymentReference reference = ServicePaymentReference.of("This is a reference");
    private ChargeStatus status = ChargeStatus.CREATED;
    private GatewayAccountEntity gatewayAccountEntity = defaultGatewayAccountEntity();
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = defaultGatewayAccountCredentialsEntity();
    private String transactionId;
    private Instant createdDate = Instant.now();
    private List<ChargeEventEntity> events = new ArrayList<>();
    private Auth3dsRequiredEntity auth3DsRequiredEntity;
    private String providerSessionId;
    private SupportedLanguage language = SupportedLanguage.ENGLISH;
    private boolean delayedCapture = false;
    private Long corporateSurcharge = null;
    private List<Fee> fees;
    private WalletType walletType = null;
    private ExternalMetadata externalMetadata = null;
    private CardDetailsEntity cardDetails = null;
    private ParityCheckStatus parityCheckStatus = null;
    private String gatewayTransactionId = null;
    private Source source = null;
    private boolean moto;
    private Exemption3ds exemption3ds;
    private Exemption3dsType exemption3dsRequested = null;
    private String paymentProvider = "sandbox";
    private String serviceId = randomUuid();
    private AgreementEntity agreementEntity;
    private boolean savePaymentInstrumentToAgreement = false;
    private PaymentInstrumentEntity paymentInstrument = null;
    private AuthorisationMode authorisationMode = AuthorisationMode.WEB;
    private Boolean canRetry;
    private Instant updatedDate;
    private Boolean requires3ds;
    private ChargeResponse.AuthorisationSummary authorisationSummary;
    private AgreementPaymentType agreementPaymentType;

    public static ChargeEntityFixture aValidChargeEntity() {
        return new ChargeEntityFixture();
    }

    public static GatewayAccountEntity defaultGatewayAccountEntity() {
        GatewayAccountEntity accountEntity = new GatewayAccountEntity(TEST);
        accountEntity.setId(1L);
        accountEntity.setServiceName("MyService");

        EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(accountEntity);
        emailNotificationEntity.setTemplateBody("template body");
        accountEntity.addNotification(EmailNotificationType.PAYMENT_CONFIRMED, emailNotificationEntity);
        accountEntity.addNotification(EmailNotificationType.REFUND_ISSUED, emailNotificationEntity);

        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = defaultGatewayAccountCredentialsEntity();
        accountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        return accountEntity;
    }

    public static GatewayAccountCredentialsEntity defaultGatewayAccountCredentialsEntity() {
        return aGatewayAccountCredentialsEntity()
                .withPaymentProvider("sandbox")
                .withCredentials(Map.of())
                .withState(CREATED)
                .build();
    }

    public static CardDetailsEntity defaultCardDetails() {
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity();
        CardBrandLabelEntity CardBrandLabelEntity = new CardBrandLabelEntity();
        CardBrandLabelEntity.setBrand("visa");
        CardBrandLabelEntity.setLabel("Visa");

        cardDetailsEntity.setCardHolderName("Test");
        cardDetailsEntity.setCardBrand("visa");
        cardDetailsEntity.setExpiryDate(CardExpiryDate.valueOf("11/99"));
        cardDetailsEntity.setFirstDigitsCardNumber(FirstDigitsCardNumber.of("123456"));
        cardDetailsEntity.setLastDigitsCardNumber(LastDigitsCardNumber.of("1234"));
        cardDetailsEntity.setCardType(CardType.DEBIT);
        cardDetailsEntity.setCardTypeDetails(CardBrandLabelEntity);
        cardDetailsEntity.setBillingAddress(defaultBillingAddress());

        return cardDetailsEntity;
    }

    public static AddressEntity defaultBillingAddress() {
        AddressEntity addressEntity = new AddressEntity();

        addressEntity.setLine1("10 WCB");
        addressEntity.setLine2("address line 2");
        addressEntity.setCity("London");
        addressEntity.setCounty("London");
        addressEntity.setPostcode("E1 8XX");
        addressEntity.setCountry("UK");

        return addressEntity;
    }

    public ChargeEntity build() {
        if (gatewayTransactionId == null) {
            gatewayTransactionId = transactionId;
        }

        ChargeEntity chargeEntity = new ChargeEntity(amount,
                status,
                returnUrl,
                description,
                reference,
                gatewayAccountEntity,
                gatewayAccountCredentialsEntity,
                paymentProvider,
                email,
                createdDate,
                language,
                delayedCapture,
                externalMetadata,
                source,
                gatewayTransactionId,
                cardDetails,
                moto,
                serviceId,
                agreementEntity,
                agreementPaymentType,
                savePaymentInstrumentToAgreement,
                authorisationMode,
                canRetry,
                requires3ds);
        chargeEntity.setId(id);
        chargeEntity.setExternalId(externalId);
        chargeEntity.setCorporateSurcharge(corporateSurcharge);
        chargeEntity.getEvents().addAll(events);
        chargeEntity.setProviderSessionId(providerSessionId);
        chargeEntity.setWalletType(walletType);
        chargeEntity.setExemption3ds(exemption3ds);
        chargeEntity.setExemption3dsRequested(exemption3dsRequested);
        chargeEntity.setPaymentInstrument(paymentInstrument);
        chargeEntity.setUpdatedDate(updatedDate);

        if (this.auth3DsRequiredEntity != null) {
            chargeEntity.set3dsRequiredDetails(auth3DsRequiredEntity);
        }

        if (this.fees != null) {
            fees.stream().forEach(partialFee -> {
                FeeEntity fee = new FeeEntity(chargeEntity, Instant.now(), partialFee);
                chargeEntity.addFee(fee);
            });
        }

        if (parityCheckStatus != null) {
            chargeEntity.updateParityCheck(parityCheckStatus);
        }

        return chargeEntity;
    }

    public ChargeEntityFixture withId(Long id) {
        this.id = id;
        return this;
    }

    public ChargeEntityFixture withExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public ChargeEntityFixture withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public ChargeEntityFixture withGatewayAccountEntity(GatewayAccountEntity gatewayAccountEntity) {
        this.gatewayAccountEntity = gatewayAccountEntity;
        return this;
    }

    public ChargeEntityFixture withGatewayAccountCredentialsEntity(GatewayAccountCredentialsEntity credentialsEntity) {
        this.gatewayAccountCredentialsEntity = credentialsEntity;
        return this;
    }

    public ChargeEntityFixture withSource(Source source) {
        this.source = source;
        return this;
    }

    public ChargeEntityFixture withTransactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    public ChargeEntityFixture withReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        return this;
    }

    public ChargeEntityFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public ChargeEntityFixture withReference(ServicePaymentReference reference) {
        this.reference = reference;
        return this;
    }

    public ChargeEntityFixture withStatus(ChargeStatus status) {
        this.status = status;
        return this;
    }

    public ChargeEntityFixture withEvents(List<ChargeEventEntity> events) {
        this.events = events;
        return this;
    }

    public ChargeEntityFixture withCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public ChargeEntityFixture withAuth3dsDetailsEntity(Auth3dsRequiredEntity auth3DsRequiredEntity) {
        this.auth3DsRequiredEntity = auth3DsRequiredEntity;
        return this;
    }

    public ChargeEntityFixture withProviderSessionId(String providerSessionId) {
        this.providerSessionId = providerSessionId;
        return this;
    }

    public ChargeEntityFixture withNotifySettings(ImmutableMap<String, String> notifySettings) {
        this.gatewayAccountEntity.setNotifySettings(notifySettings);
        return this;
    }

    public ChargeEntityFixture withCorporateSurcharge(Long corporateSurcharge) {
        this.corporateSurcharge = corporateSurcharge;
        return this;
    }

    public ChargeEntityFixture withWalletType(WalletType walletType) {
        this.walletType = walletType;
        return this;
    }

    public ChargeEntityFixture withFee(Fee fee) {
        if (this.fees == null) {
            this.fees = new ArrayList<>();
        }
        this.fees.add(fee);
        return this;
    }

    public ChargeEntityFixture withExternalMetadata(ExternalMetadata externalMetadata) {
        this.externalMetadata = externalMetadata;
        return this;
    }

    public ChargeEntityFixture withCardDetails(CardDetailsEntity cardDetailsEntity) {
        this.cardDetails = cardDetailsEntity;
        return this;
    }

    public ChargeEntityFixture withCardLabelEntity(String label, String brand) {
        CardBrandLabelEntity cardBrandLabelEntity = new CardBrandLabelEntity();
        cardBrandLabelEntity.setBrand(brand);
        cardBrandLabelEntity.setLabel(label);
        if (cardDetails == null) {
            cardDetails = new CardDetailsEntity();
        }
        cardDetails.setCardTypeDetails(cardBrandLabelEntity);
        return this;
    }

    public ChargeEntityFixture withParityStatus(ParityCheckStatus parityCheckStatus) {
        this.parityCheckStatus = parityCheckStatus;
        return this;
    }

    public ChargeEntityFixture withGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
        return this;
    }

    public ChargeEntityFixture withEmail(String email) {
        this.email = email;
        return this;
    }

    public ChargeEntityFixture withMoto(boolean moto) {
        this.moto = moto;
        return this;
    }

    public ChargeEntityFixture withDelayedCapture(boolean delayedCapture) {
        this.delayedCapture = delayedCapture;
        return this;
    }

    public ChargeEntityFixture withExemption3ds(Exemption3ds exemption3ds) {
        this.exemption3ds = exemption3ds;
        return this;
    }

    public Exemption3dsType getExemption3dsRequested() {
        return exemption3dsRequested;
    }

    public ChargeEntityFixture withExemption3dsType(Exemption3dsType exemption3DsRequested) {
        this.exemption3dsRequested = exemption3DsRequested;
        return this;
    }

    public ChargeEntityFixture withPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
        return this;
    }

    public ChargeEntityFixture withServiceId(String serviceId) {
        this.serviceId = serviceId;
        return this;
    }

    public ChargeEntityFixture withAgreementEntity(AgreementEntity agreementEntity) {
        this.agreementEntity = agreementEntity;
        return this;
    }

    public ChargeEntityFixture withSavePaymentInstrumentToAgreement(boolean savePaymentInstrumentToAgreement) {
        this.savePaymentInstrumentToAgreement = savePaymentInstrumentToAgreement;
        return this;
    }

    public ChargeEntityFixture withPaymentInstrument(PaymentInstrumentEntity paymentInstrument) {
        this.paymentInstrument = paymentInstrument;
        return this;
    }

    public ChargeEntityFixture withAuthorisationMode(AuthorisationMode authorisationMode) {
        this.authorisationMode = authorisationMode;
        return this;
    }

    public ChargeEntityFixture withUpdatedDate(Instant updatedDate) {
        this.updatedDate = updatedDate;
        return this;
    }

    public ChargeEntityFixture withCanRetry(Boolean canRetry) {
        this.canRetry = canRetry;
        return this;
    }

    public ChargeEntityFixture withRequires3ds(Boolean requires3ds) {
        this.requires3ds = requires3ds;
        return this;
    }

    public ChargeEntityFixture withAgreementPaymentType(AgreementPaymentType agreementPaymentType) {
        this.agreementPaymentType = agreementPaymentType;
        return this;
    }
}
