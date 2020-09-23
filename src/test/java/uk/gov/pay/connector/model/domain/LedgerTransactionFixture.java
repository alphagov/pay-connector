package uk.gov.pay.connector.model.domain;

import uk.gov.pay.commons.model.Source;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.model.Address;
import uk.gov.pay.connector.client.ledger.model.CardDetails;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.SettlementSummary;
import uk.gov.pay.connector.client.ledger.model.TransactionState;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.wallets.WalletType;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static uk.gov.pay.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getTotalAmountFor;

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
    private ZonedDateTime createdDate = ZonedDateTime.now();
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
    private Long totalAmount;
    private ZonedDateTime captureSubmittedDate;
    private ZonedDateTime capturedDate;
    private ChargeResponse.RefundSummary refundSummary;
    private String parentTransactionId;
    private String userEmail;
    private String userExternalId;

    public static LedgerTransactionFixture aValidLedgerTransaction() {
        return new LedgerTransactionFixture();
    }

    public static LedgerTransactionFixture from(ChargeEntity chargeEntity, List<RefundEntity> refundsList) {
        LedgerTransactionFixture ledgerTransactionFixture =
                aValidLedgerTransaction()
                        .withStatus(ChargeStatus.fromString(chargeEntity.getStatus()).toExternal().getStatusV2())
                        .withExternalId(chargeEntity.getExternalId())
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
                        .withTotalAmount(getTotalAmountFor(chargeEntity))
                        .withNetAmount(chargeEntity.getNetAmount().orElse(null));

        ledgerTransactionFixture.withCreatedDate(getEventDate(chargeEntity.getEvents(), CREATED));
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
                    chargeEntityCardDetails.getCardTypeDetails().map(CardBrandLabelEntity::getLabel).orElse(null),
                    ofNullable(chargeEntityCardDetails.getLastDigitsCardNumber()).map(LastDigitsCardNumber::toString).orElse(null),
                    ofNullable(chargeEntityCardDetails.getFirstDigitsCardNumber()).map(FirstDigitsCardNumber::toString).orElse(null),
                    chargeEntityCardDetails.getExpiryDate(),
                    ofNullable(chargeEntityCardDetails.getCardType()).map(cardType -> cardType.toString().toLowerCase()).orElse(null)
            );

            ledgerTransactionFixture.withCardDetails(cardDetails);
        }

        ledgerTransactionFixture.withCapturedDate(getEventDate(chargeEntity.getEvents(), CAPTURED));
        ledgerTransactionFixture.withCaptureSubmittedDate(getEventDate(chargeEntity.getEvents(), CAPTURE_SUBMITTED));

        ChargeResponse.RefundSummary refundSummary = new ChargeResponse.RefundSummary();
        ExternalChargeRefundAvailability refundAvailability;
        if (refundsList != null) {
            refundAvailability = new DefaultExternalRefundAvailabilityCalculator()
                    .calculate(Charge.from(chargeEntity), refundsList.stream().map(Refund::from).collect(Collectors.toList()));
        } else {
            refundAvailability = new DefaultExternalRefundAvailabilityCalculator()
                    .calculate(Charge.from(chargeEntity), List.of());
        }
        refundSummary.setStatus(refundAvailability.getStatus());
        ledgerTransactionFixture.withRefundSummary(refundSummary);

        return ledgerTransactionFixture;
    }

    public static LedgerTransactionFixture from(RefundEntity refundEntity) {
        LedgerTransactionFixture ledgerTransactionFixture =
                aValidLedgerTransaction()
                        .withAmount(refundEntity.getAmount())
                        .withStatus(refundEntity.getStatus().toExternal().getStatus())
                        .withCreatedDate(refundEntity.getCreatedDate())
                        .withExternalId(refundEntity.getExternalId())
                        .withGatewayTransactionId(refundEntity.getGatewayTransactionId())
                        .withParentTransactionId(refundEntity.getChargeExternalId())
                        .withUserEmail(refundEntity.getUserEmail())
                        .withUserExternalId(refundEntity.getUserExternalId());

        return ledgerTransactionFixture;
    }

    private static ZonedDateTime getEventDate(List<ChargeEventEntity> chargeEventEntities, ChargeStatus status) {
        return ofNullable(chargeEventEntities).flatMap(entities -> entities.stream()
                .filter(chargeEvent -> status.equals(chargeEvent.getStatus()))
                .findFirst()
                .map(ChargeEventEntity::getUpdated))
                .orElse(null);
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

        if (gatewayAccountId != null) {
            ledgerTransaction.setGatewayAccountId(gatewayAccountId.toString());
        }

        ledgerTransaction.setSource(source);
        ledgerTransaction.setMoto(moto);
        ledgerTransaction.setDelayedCapture(delayedCapture);
        ledgerTransaction.setFee(fee);
        ledgerTransaction.setCorporateCardSurcharge(corporateCardSurcharge);
        ledgerTransaction.setNetAmount(netAmount);
        ledgerTransaction.setTotalAmount(totalAmount);
        ledgerTransaction.setWalletType(walletType);

        SettlementSummary settlementSummary = new SettlementSummary();
        if (capturedDate != null) {
            settlementSummary.setCapturedDate(DateTimeUtils.toUTCDateString(capturedDate));
        }
        settlementSummary.setCaptureSubmitTime(captureSubmittedDate);
        ledgerTransaction.setSettlementSummary(settlementSummary);
        ledgerTransaction.setRefundSummary(refundSummary);

        ledgerTransaction.setParentTransactionId(parentTransactionId);
        ledgerTransaction.setUserEmail(userEmail);
        ledgerTransaction.setUserExternalId(userExternalId);

        return ledgerTransaction;
    }

    public LedgerTransactionFixture withCreatedDate(ZonedDateTime createdDate) {
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

    public LedgerTransactionFixture withTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public LedgerTransactionFixture withCaptureSubmittedDate(ZonedDateTime captureSubmittedDate) {
        this.captureSubmittedDate = captureSubmittedDate;
        return this;
    }

    public LedgerTransactionFixture withCapturedDate(ZonedDateTime capturedDate) {
        this.capturedDate = capturedDate;
        return this;
    }

    public LedgerTransactionFixture withRefundSummary(ChargeResponse.RefundSummary refundSummary) {
        this.refundSummary = refundSummary;
        return this;
    }

    public LedgerTransactionFixture withParentTransactionId(String chargeExternalId) {
        this.parentTransactionId = chargeExternalId;
        return this;
    }

    public LedgerTransactionFixture withUserEmail(String userEmail) {
        this.userEmail = userEmail;
        return this;
    }

    public LedgerTransactionFixture withUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
        return this;
    }
}
