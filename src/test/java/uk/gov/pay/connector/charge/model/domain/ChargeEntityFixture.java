package uk.gov.pay.connector.charge.model.domain;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.commons.model.Source;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.wallets.WalletType;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;

public class ChargeEntityFixture {

    private Long id = ThreadLocalRandom.current().nextLong();
    private String externalId = RandomIdGenerator.newId();
    private Long amount = 500L;
    private String returnUrl = "http://return.invalid";
    private String email = "test@email.invalid";
    private String description = "This is a description";
    private ServicePaymentReference reference = ServicePaymentReference.of("This is a reference");
    private ChargeStatus status = ChargeStatus.CREATED;
    private GatewayAccountEntity gatewayAccountEntity = defaultGatewayAccountEntity();
    private String transactionId;
    private ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
    private List<ChargeEventEntity> events = new ArrayList<>();
    private Auth3dsDetailsEntity auth3dsDetailsEntity;
    private String providerSessionId;
    private SupportedLanguage language = SupportedLanguage.ENGLISH;
    private boolean delayedCapture = false;
    private Long corporateSurcharge = null;
    private Long fee = null;
    private WalletType walletType = null;
    private ExternalMetadata externalMetadata = null;
    private CardDetailsEntity cardDetails = null;
    private ParityCheckStatus parityCheckStatus = null;
    private String gatewayTransactionId = null;
    private Source source = null;
    private boolean moto;

    public static ChargeEntityFixture aValidChargeEntity() {
        return new ChargeEntityFixture();
    }

    public static GatewayAccountEntity defaultGatewayAccountEntity() {
        GatewayAccountEntity accountEntity = new GatewayAccountEntity("sandbox", new HashMap<>(), TEST);
        accountEntity.setId(1L);
        accountEntity.setServiceName("MyService");
        EmailNotificationEntity emailNotificationEntity = new EmailNotificationEntity(accountEntity);
        emailNotificationEntity.setTemplateBody("template body");
        accountEntity.addNotification(EmailNotificationType.PAYMENT_CONFIRMED, emailNotificationEntity);
        accountEntity.addNotification(EmailNotificationType.REFUND_ISSUED, emailNotificationEntity);
        return accountEntity;
    }

    public static CardDetailsEntity defaultCardDetails() {
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity();
        CardBrandLabelEntity CardBrandLabelEntity = new CardBrandLabelEntity();
        CardBrandLabelEntity.setBrand("visa");
        CardBrandLabelEntity.setLabel("Visa");

        cardDetailsEntity.setCardHolderName("Test");
        cardDetailsEntity.setCardBrand("visa");
        cardDetailsEntity.setExpiryDate("11/99");
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
                email,
                createdDate,
                language,
                delayedCapture,
                externalMetadata,
                source,
                gatewayTransactionId,
                cardDetails,
                moto);
        chargeEntity.setId(id);
        chargeEntity.setExternalId(externalId);
        chargeEntity.setCorporateSurcharge(corporateSurcharge);
        chargeEntity.getEvents().addAll(events);
        chargeEntity.setProviderSessionId(providerSessionId);
        chargeEntity.set3dsDetails(auth3dsDetailsEntity);
        chargeEntity.setSource(source);
        chargeEntity.setWalletType(walletType);

        if (this.fee != null) {
            FeeEntity fee = new FeeEntity(chargeEntity, this.fee);
            chargeEntity.setFee(fee);
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

    public ChargeEntityFixture withCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public ChargeEntityFixture withAuth3dsDetailsEntity(Auth3dsDetailsEntity auth3dsDetailsEntity) {
        this.auth3dsDetailsEntity = auth3dsDetailsEntity;
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

    public ChargeEntityFixture withFee(Long amount) {
        this.fee = amount;
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
}
