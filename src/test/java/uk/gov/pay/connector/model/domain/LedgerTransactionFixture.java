package uk.gov.pay.connector.model.domain;

import uk.gov.pay.commons.model.Source;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paritycheck.Address;
import uk.gov.pay.connector.paritycheck.CardDetails;
import uk.gov.pay.connector.paritycheck.LedgerTransaction;
import uk.gov.pay.connector.paritycheck.TransactionState;
import uk.gov.pay.connector.wallets.WalletType;

import java.time.ZonedDateTime;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static uk.gov.pay.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;

public class LedgerTransactionFixture {
    private String status = "created";
    private String externalId;
    private Long amount;
    private String description;
    private String reference;
    private String email;
    private String gatewayTransactionId;
    private String returnUrl;
    private SupportedLanguage language;
    private CardDetails cardDetails;
    private ZonedDateTime createdDate;
    private boolean live;
    private Long gatewayAccountId;
    private String paymentProvider;
    private String walletType;
    private Long netAmount;
    private Source source;
    private boolean delayedCapture;
    private Long corporateCardSurcharge;
    private Long fee;
    private boolean moto;

    public static LedgerTransactionFixture aValidLedgerTransaction() {
        return new LedgerTransactionFixture();
    }

    public static LedgerTransactionFixture from(ChargeEntity chargeEntity) {
        LedgerTransactionFixture ledgerTransactionFixture =
                aValidLedgerTransaction()
                        .withStatus(ChargeStatus.fromString(chargeEntity.getStatus()).toExternal().getStatusV2())
                        .withExternalId(chargeEntity.getExternalId())
                        .withCreatedDate(chargeEntity.getCreatedDate())
                        .withAmount(chargeEntity.getAmount())
                        .withDescription(chargeEntity.getDescription())
                        .withReference(chargeEntity.getReference().toString())
                        .withLanguage(chargeEntity.getLanguage())
                        .withEmail(chargeEntity.getEmail())
                        .withReturnUrl(chargeEntity.getReturnUrl())
                        .withGatewayTransactionId(chargeEntity.getGatewayTransactionId())
                        .withDelayedCapture(chargeEntity.isDelayedCapture())
                        .withSource(chargeEntity.getSource())
                        .withMoto(chargeEntity.isMoto())
                        .withFee(chargeEntity.getFeeAmount().orElse(null))
                        .withCorporateCardSurcharge(chargeEntity.getCorporateSurcharge().orElse(null))
                        .withWalletType(chargeEntity.getWalletType())
                        .withNetAmount(chargeEntity.getNetAmount().orElse(null));

        if (chargeEntity.getGatewayAccount() != null) {
            GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
            ledgerTransactionFixture.withPaymentProvider(gatewayAccount.getGatewayName());
            ledgerTransactionFixture.withGatewayAccountId(gatewayAccount.getId());
            ledgerTransactionFixture.isLive(gatewayAccount.isLive());
        }
        if (nonNull(chargeEntity.getCardDetails())) {

            CardDetailsEntity chargeEntityCardDetails = chargeEntity.getCardDetails();
            Address ledgerAddress = chargeEntityCardDetails.getBillingAddress().map(addressEntity -> {
                return new Address(addressEntity.getLine1(),
                        addressEntity.getLine2(),
                        addressEntity.getPostcode(),
                        addressEntity.getCity(),
                        addressEntity.getCounty(),
                        addressEntity.getCountry());
            }).orElse(null);

            CardDetails cardDetails = new CardDetails(chargeEntityCardDetails.getCardHolderName(),
                    ledgerAddress,
                    chargeEntityCardDetails.getCardBrand(),
                    ofNullable(chargeEntityCardDetails.getLastDigitsCardNumber()).map(LastDigitsCardNumber::toString).orElse(null),
                    ofNullable(chargeEntityCardDetails.getFirstDigitsCardNumber()).map(FirstDigitsCardNumber::toString).orElse(null),
                    chargeEntityCardDetails.getExpiryDate(),
                    chargeEntityCardDetails.getCardType()
            );


            ledgerTransactionFixture.withCardDetails(cardDetails);
        }


        return ledgerTransactionFixture;
    }

    public LedgerTransaction build() {
        var ledgerTransaction = new LedgerTransaction();
        ledgerTransaction.setState(new TransactionState(status));
        ledgerTransaction.setTransactionId(externalId);
        ledgerTransaction.setAmount(amount);
        ledgerTransaction.setDescription(description);
        ledgerTransaction.setReference(reference);
        ledgerTransaction.setEmail(email);
        ledgerTransaction.setGatewayTransactionId(gatewayTransactionId);
        ledgerTransaction.setReturnUrl(returnUrl);
        ledgerTransaction.setLanguage(language);
        ledgerTransaction.setCardDetails(cardDetails);
        ledgerTransaction.setCreatedDate(ofNullable(createdDate)
                .map(ISO_INSTANT_MILLISECOND_PRECISION::format)
                .orElse(null));

        ledgerTransaction.setLive(live);
        ledgerTransaction.setPaymentProvider(paymentProvider);
        ledgerTransaction.setGatewayAccountId(gatewayAccountId);

        ledgerTransaction.setSource(source);
        ledgerTransaction.setMoto(moto);
        ledgerTransaction.setDelayedCapture(delayedCapture);
        ledgerTransaction.setFee(fee);
        ledgerTransaction.setCorporateCardSurcharge(corporateCardSurcharge);
        ledgerTransaction.setNetAmount(netAmount);
        ledgerTransaction.setWalletType(walletType);
        
        return ledgerTransaction;
    }

    private LedgerTransactionFixture withCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public LedgerTransactionFixture withStatus(String status) {
        this.status = status;
        return this;
    }

    public LedgerTransactionFixture withExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public LedgerTransactionFixture withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public LedgerTransactionFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public LedgerTransactionFixture withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public LedgerTransactionFixture withEmail(String email) {
        this.email = email;
        return this;
    }

    public LedgerTransactionFixture withReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
        return this;
    }

    public LedgerTransactionFixture withGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
        return this;
    }

    public LedgerTransactionFixture withLanguage(SupportedLanguage language) {
        this.language = language;
        return this;
    }

    public LedgerTransactionFixture withCardDetails(CardDetails cardDetails) {
        this.cardDetails = cardDetails;
        return this;
    }

    public LedgerTransactionFixture isLive(boolean live) {
        this.live = live;
        return this;
    }

    public LedgerTransactionFixture withGatewayAccountId(Long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
        return this;
    }

    public LedgerTransactionFixture withPaymentProvider(String paymemtProvider) {
        this.paymentProvider = paymemtProvider;
        return this;
    }

    public LedgerTransactionFixture withNetAmount(Long netAmount) {
        this.netAmount = netAmount;
        return this;
    }

    public LedgerTransactionFixture withWalletType(WalletType walletType) {
        this.walletType = walletType.toString();
        return this;
    }

    public LedgerTransactionFixture withCorporateCardSurcharge(Long corporateCardSurcharge) {
        this.corporateCardSurcharge = corporateCardSurcharge;
        return this;
    }

    public LedgerTransactionFixture withFee(Long fee) {
        this.fee = fee;
        return this;
    }

    public LedgerTransactionFixture withMoto(boolean moto) {
        this.moto = moto;
        return this;
    }

    public LedgerTransactionFixture withSource(Source source) {
        this.source = source;
        return this;
    }

    public LedgerTransactionFixture withDelayedCapture(boolean delayedCapture) {
        this.delayedCapture = delayedCapture;
        return this;
    }

}
