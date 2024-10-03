package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit.target.AmqpTarget;
import au.com.dius.pact.provider.junit.target.Target;
import au.com.dius.pact.provider.junit.target.TestTarget;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureConfirmedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.CaptureSubmittedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.Gateway3dsExemptionResultObtainedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.Gateway3dsInfoObtainedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.GatewayRequires3dsAuthorisationEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentDetailsEnteredEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentDetailsSubmittedByAPIEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.Requested3dsExemptionEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.UserEmailCollectedEventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeCreatedEventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeEvidenceSubmittedEventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeLostEventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeWonEventDetails;
import uk.gov.pay.connector.events.model.charge.CancelledByUser;
import uk.gov.pay.connector.events.model.charge.CaptureConfirmed;
import uk.gov.pay.connector.events.model.charge.CaptureSubmitted;
import uk.gov.pay.connector.events.model.charge.FeeIncurredEvent;
import uk.gov.pay.connector.events.model.charge.Gateway3dsExemptionResultObtained;
import uk.gov.pay.connector.events.model.charge.Gateway3dsInfoObtained;
import uk.gov.pay.connector.events.model.charge.GatewayRequires3dsAuthorisation;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsSubmittedByAPI;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsTakenFromPaymentInstrument;
import uk.gov.pay.connector.events.model.charge.PaymentIncludedInPayout;
import uk.gov.pay.connector.events.model.charge.PaymentNotificationCreated;
import uk.gov.pay.connector.events.model.charge.Requested3dsExemption;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToCapturedToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.UserEmailCollected;
import uk.gov.pay.connector.events.model.dispute.DisputeCreated;
import uk.gov.pay.connector.events.model.dispute.DisputeEvidenceSubmitted;
import uk.gov.pay.connector.events.model.dispute.DisputeLost;
import uk.gov.pay.connector.events.model.dispute.DisputeWon;
import uk.gov.pay.connector.events.model.payout.PayoutCreated;
import uk.gov.pay.connector.events.model.payout.PayoutFailed;
import uk.gov.pay.connector.events.model.payout.PayoutPaid;
import uk.gov.pay.connector.events.model.payout.PayoutUpdated;
import uk.gov.pay.connector.events.model.refund.RefundCreatedByUser;
import uk.gov.pay.connector.events.model.refund.RefundIncludedInPayout;
import uk.gov.pay.connector.events.model.refund.RefundSubmitted;
import uk.gov.pay.connector.events.model.refund.RefundSucceeded;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.stripe.json.StripePayout;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;

import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultCardDetails;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;
import static uk.gov.pay.connector.events.model.payout.PayoutCreated.from;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.pact.ChargeEventEntityFixture.aValidChargeEventEntity;
import static uk.gov.pay.connector.pact.RefundHistoryEntityFixture.aValidRefundHistoryEntity;
import static uk.gov.service.payments.commons.model.AuthorisationMode.EXTERNAL;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;
import static uk.gov.service.payments.commons.model.Source.CARD_API;
import static uk.gov.service.payments.commons.model.Source.CARD_EXTERNAL_TELEPHONE;

public class QueueMessageContractTest {

    @TestTarget
    public final Target target = new AmqpTarget();

    private String resourceId = "anExternalResourceId";

    @PactVerifyProvider("a payment created message")
    public String verifyPaymentCreatedEvent() throws Exception {
        ChargeEntity charge = aValidChargeEntity()
                .withExternalMetadata(new ExternalMetadata(ImmutableMap.of("key", "value")))
                .withCorporateSurcharge(55L)
                .withSource(CARD_API)
                .withCardDetails(anAuthCardDetails().getCardDetailsEntity())
                .withAgreementEntity(anAgreementEntity().build())
                .build();

        PaymentCreated paymentCreatedEvent = new PaymentCreated(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                resourceId,
                PaymentCreatedEventDetails.from(charge),
                Instant.now()
        );

        return paymentCreatedEvent.toJsonString();
    }

    @PactVerifyProvider("a capture confirmed message")
    public String verifyCaptureConfirmedEvent() throws JsonProcessingException {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(ChargeStatus.CAPTURED)
                .withFee(Fee.of(null, 42L))
                .build();

        ChargeEventEntity chargeEventEntity = aValidChargeEventEntity()
                .withCharge(chargeEntity)
                .withChargeStatus(ChargeStatus.CAPTURED)
                .withGatewayEventDate(ZonedDateTime.now())
                .build();

        CaptureConfirmed captureConfirmedEvent = new CaptureConfirmed(
                chargeEventEntity.getChargeEntity().getServiceId(),
                chargeEventEntity.getChargeEntity().getGatewayAccount().isLive(),
                chargeEventEntity.getChargeEntity().getGatewayAccount().getId(),
                resourceId,
                CaptureConfirmedEventDetails.from(chargeEventEntity),
                Instant.now()
        );

        return captureConfirmedEvent.toJsonString();
    }

    @PactVerifyProvider("a payment details entered message")
    public String verifyPaymentDetailsEnteredEvent() throws JsonProcessingException {
        ChargeEntity charge = aValidChargeEntity()
                .withTransactionId("gateway_transaction_id")
                .withCorporateSurcharge(55L)
                .withCardDetails(anAuthCardDetails().getCardDetailsEntity())
                .build();

        PaymentDetailsEntered captureConfirmedEvent = new PaymentDetailsEntered(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                resourceId,
                PaymentDetailsEnteredEventDetails.from(charge),
                Instant.now()
        );

        return captureConfirmedEvent.toJsonString();
    }

    @PactVerifyProvider("a payment details submitted by api message")
    public String verifyPaymentDetailsSubmittedByAPIEvent() throws JsonProcessingException {
        ChargeEntity charge = aValidChargeEntity()
                .withTransactionId("gateway_transaction_id")
                .withAuthorisationMode(MOTO_API)
                .withCardDetails(anAuthCardDetails().getCardDetailsEntity())
                .build();

        PaymentDetailsSubmittedByAPI paymentDetailsSubmittedByAPIEvent = new PaymentDetailsSubmittedByAPI(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                resourceId,
                PaymentDetailsSubmittedByAPIEventDetails.from(charge),
                Instant.now()
        );

        return paymentDetailsSubmittedByAPIEvent.toJsonString();
    }

    @PactVerifyProvider("a user email collected message")
    public String verifyUserEmailCollectedEvent() throws JsonProcessingException {
        ChargeEntity charge = aValidChargeEntity()
                .withEmail("test@example.org")
                .withAgreementEntity(anAgreementEntity().build())
                .build();

        UserEmailCollected userEmailCollected = new UserEmailCollected(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                resourceId,
                UserEmailCollectedEventDetails.from(charge),
                Instant.now()
        );

        return userEmailCollected.toJsonString();
    }

    @PactVerifyProvider("a capture submitted message")
    public String verifyCaptureSubmittedEvent() throws JsonProcessingException {
        ChargeEventEntity chargeEventEntity = aValidChargeEventEntity()
                .withGatewayEventDate(ZonedDateTime.now())
                .build();

        CaptureSubmitted captureSubmittedEvent = new CaptureSubmitted(
                chargeEventEntity.getChargeEntity().getServiceId(),
                chargeEventEntity.getChargeEntity().getGatewayAccount().isLive(),
                chargeEventEntity.getChargeEntity().getGatewayAccount().getId(),
                resourceId,
                CaptureSubmittedEventDetails.from(chargeEventEntity),
                Instant.now()
        );

        return captureSubmittedEvent.toJsonString();
    }

    @PactVerifyProvider("a refund created by user message")
    public String verifyRefundCreatedByUserEvent() throws JsonProcessingException {
        ChargeEntity chargeEntity = aValidChargeEntity().build();
        Charge charge = Charge.from(chargeEntity);
        RefundHistory refundHistory = aValidRefundHistoryEntity()
                .withUserExternalId(RandomStringUtils.randomAlphanumeric(10))
                .withUserEmail("test@example.com")
                .build();
        RefundCreatedByUser refundCreatedByUser = RefundCreatedByUser.from(refundHistory, charge);

        return refundCreatedByUser.toJsonString();
    }

    @PactVerifyProvider("a refund submitted message")
    public String verifyRefundSubmittedEvent() throws JsonProcessingException {
        ChargeEntity chargeEntity = aValidChargeEntity().build();
        Charge charge = Charge.from(chargeEntity);
        RefundHistory refundHistory = aValidRefundHistoryEntity()
                .build();
        RefundSubmitted refundSubmitted = RefundSubmitted.from(charge, refundHistory);

        return refundSubmitted.toJsonString();
    }

    @PactVerifyProvider("a refund succeeded message")
    public String verifyRefundedEvent() throws JsonProcessingException {
        String gatewayTransactionId = RandomStringUtils.randomAlphanumeric(14);
        ChargeEntity chargeEntity = aValidChargeEntity().build();
        Charge charge = Charge.from(chargeEntity);
        RefundHistory refundHistory = aValidRefundHistoryEntity()
                .withStatus(RefundStatus.REFUNDED.getValue())
                .withGatewayTransactionId(gatewayTransactionId)
                .build();
        RefundSucceeded refundSucceeded = RefundSucceeded.from(charge, refundHistory);

        return refundSucceeded.toJsonString();
    }

    @PactVerifyProvider("a payment notification created message")
    public String verifyPaymentNotificationCreatedEvent() throws JsonProcessingException {
        ExternalMetadata externalMetadata = new ExternalMetadata(Map.of(
                "processor_id", "processorId",
                "auth_code", "012345",
                "telephone_number", "+447700900796",
                "status", "success",
                "authorised_date", "2018-02-21T16:05:33Z",
                "created_date", "2018-02-21T15:05:13Z"));
        ChargeEntity charge = aValidChargeEntity()
                .withStatus(ChargeStatus.PAYMENT_NOTIFICATION_CREATED)
                .withGatewayTransactionId("providerId")
                .withEmail("j.doe@example.org")
                .withSource(CARD_EXTERNAL_TELEPHONE)
                .withCardDetails(anAuthCardDetails().withAddress(null).getCardDetailsEntity())
                .withExternalMetadata(externalMetadata)
                .withAuthorisationMode(EXTERNAL)
                .build();
        ChargeEventEntity chargeEventEntity = aValidChargeEventEntity()
                .withCharge(charge)
                .build();

        PaymentNotificationCreated paymentNotificationCreated = PaymentNotificationCreated.from(chargeEventEntity);

        return paymentNotificationCreated.toJsonString();
    }

    @PactVerifyProvider("a payout created message")
    public String verifyPayoutCreatedEvent() throws JsonProcessingException {
        StripePayout payout = new StripePayout("po_1234567890", 1000L, 1589395533L,
                1589395500L, "pending", "bank_account", "SERVICE NAME");
        PayoutCreated payoutCreated = from(123456789L, payout);

        return payoutCreated.toJsonString();
    }

    @PactVerifyProvider("a payout failed message")
    public String verifyPayoutFailedEvent() throws JsonProcessingException {
        StripePayout payout = new StripePayout("po_failed_1234567890", "failed", "account_closed",
                "The bank account has been closed", "ba_aaaaaaaaaa");
        PayoutFailed payoutFailed = PayoutFailed.from(Instant.parse("2020-05-13T18:50:00Z"), payout);

        return payoutFailed.toJsonString();
    }

    @PactVerifyProvider("a payout paid message")
    public String verifyPayoutPaidEvent() throws JsonProcessingException {
        StripePayout payout = new StripePayout("po_paid_1234567890", 1000L, 1589395533L,
                1589395500L, "paid", "bank_account", "SERVICE NAME");
        PayoutPaid payoutPaid = PayoutPaid.from(Instant.parse("2020-05-13T18:50:00Z"), payout);

        return payoutPaid.toJsonString();
    }

    @PactVerifyProvider("a payout updated message")
    public String verifyPayoutUpdatedEvent() throws JsonProcessingException {
        StripePayout payout = new StripePayout("po_updated_1234567890", 1000L, 1589395533L,
                1589395500L, "pending", "bank_account", "SERVICE NAME");
        PayoutUpdated payoutPaid = PayoutUpdated.from(Instant.parse("2020-05-13T18:50:00Z"), payout);

        return payoutPaid.toJsonString();
    }

    @PactVerifyProvider("a payment included in payout message")
    public String verifyPaymentIncludedInPayoutEvent() throws JsonProcessingException {
        PaymentIncludedInPayout event = new PaymentIncludedInPayout(resourceId, "po_1234567890", Instant.now());

        return event.toJsonString();
    }

    @PactVerifyProvider("a refund included in payout message")
    public String verifyRefundIncludedInPayoutEvent() throws JsonProcessingException {
        RefundIncludedInPayout event = new RefundIncludedInPayout(resourceId, "po_1234567890", Instant.now());

        return event.toJsonString();
    }

    @PactVerifyProvider("a cancelled by user message")
    public String verifyCancelledByUserEvent() throws JsonProcessingException {
        ChargeEntity charge = aValidChargeEntity()
                .withTransactionId("gateway_transaction_id")
                .withGatewayTransactionId("gateway_transaction_id")
                .build();
        ChargeEventEntity chargeEventEntity = aValidChargeEventEntity()
                .withCharge(charge)
                .build();
        CancelledByUser event = CancelledByUser.from(chargeEventEntity);

        return event.toJsonString();
    }

    @PactVerifyProvider("a gateway 3DS exemption result obtained message")
    public String verifyGateway3dsExemptionResultObtainedEvent() throws JsonProcessingException {
        ChargeEntity charge = aValidChargeEntity()
                .withExemption3ds(Exemption3ds.EXEMPTION_HONOURED)
                .build();

        var gateway3dsExemptionResultObtained = new Gateway3dsExemptionResultObtained(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                resourceId,
                Gateway3dsExemptionResultObtainedEventDetails.from(charge),
                Instant.now()
        );

        return gateway3dsExemptionResultObtained.toJsonString();
    }

    @PactVerifyProvider("a gateway 3DS info obtained message")
    public String verifyGateway3dsInfoObtainedEvent() throws JsonProcessingException {
        var auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setThreeDsVersion("2.1.0");
        var charge = aValidChargeEntity()
                .withAuth3dsDetailsEntity(auth3dsRequiredEntity)
                .build();

        var gateway3dsInfoObtained = new Gateway3dsInfoObtained(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                resourceId,
                Gateway3dsInfoObtainedEventDetails.from(charge),
                Instant.now()
        );

        return gateway3dsInfoObtained.toJsonString();
    }

    @PactVerifyProvider("a gateway requested 3DS exemption message")
    public String verifyRequested3dsExemptionEvent() throws JsonProcessingException {
        var charge = aValidChargeEntity()
                .withExemption3dsType(Exemption3dsType.OPTIMISED)
                .build();

        var var = new Requested3dsExemption(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                resourceId,
                Requested3dsExemptionEventDetails.from(charge),
                Instant.now()
        );

        return var.toJsonString();
    }

    @PactVerifyProvider("a gateway requires 3DS authorisation message")
    public String verifyGatewayRequires3dsAuthorisationEvent() throws JsonProcessingException {
        var auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setThreeDsVersion("2.1.0");
        var charge = aValidChargeEntity()
                .withAuth3dsDetailsEntity(auth3dsRequiredEntity)
                .build();

        var gatewayRequires3dsAuthorisation = new GatewayRequires3dsAuthorisation(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                resourceId,
                GatewayRequires3dsAuthorisationEventDetails.from(charge),
                Instant.now());

        return gatewayRequires3dsAuthorisation.toJsonString();
    }

    @PactVerifyProvider("a status corrected to captured event")
    public String verifyStatusCorrectedToCapturedToMatchGatewayStatusEvent() throws JsonProcessingException {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(ChargeStatus.CAPTURED)
                .withFee(Fee.of(null, 42L))
                .build();

        ChargeEventEntity chargeEventEntity = aValidChargeEventEntity()
                .withCharge(chargeEntity)
                .withGatewayEventDate(ZonedDateTime.now())
                .build();

        StatusCorrectedToCapturedToMatchGatewayStatus event = new StatusCorrectedToCapturedToMatchGatewayStatus(
                chargeEventEntity.getChargeEntity().getServiceId(),
                chargeEventEntity.getChargeEntity().getGatewayAccount().isLive(),
                chargeEventEntity.getChargeEntity().getGatewayAccount().getId(),
                resourceId,
                CaptureConfirmedEventDetails.from(chargeEventEntity),
                Instant.now()
        );

        return event.toJsonString();
    }

    @PactVerifyProvider("a fee incurred message")
    public String verifyFeeIncurredEvent() throws Exception {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withStatus(ChargeStatus.CAPTURED)
                .withFee(Fee.of(FeeType.TRANSACTION, 42L))
                .withFee(Fee.of(FeeType.RADAR, 4L))
                .withFee(Fee.of(FeeType.THREE_D_S, 5L))
                .build();

        var feeIncurredEvent = FeeIncurredEvent.from(chargeEntity);

        return feeIncurredEvent.toJsonString();
    }

    @PactVerifyProvider("a dispute created event")
    public String verifyDisputeCreatedEvent() throws JsonProcessingException {
        DisputeCreatedEventDetails eventDetails =
                new DisputeCreatedEventDetails(Instant.parse("2022-02-14T23:59:59.000Z"), "a-gateway-account-id",
                        6500L, "duplicate", "du_1LIaq8Dv3CZEaFO2MNQJK333");
        DisputeCreated disputeCreated =
                new DisputeCreated("resource-external-id", "external-id", "service-id",
                        true, eventDetails, Instant.ofEpochSecond(1642579160L));
        return disputeCreated.toJsonString();
    }

    @PactVerifyProvider("a dispute lost event")
    public String verifyDisputeLostEvent() throws JsonProcessingException {
        DisputeLostEventDetails eventDetails = new DisputeLostEventDetails("a-gateway-account-id",
                6500L, -8000L, 1500L);
        DisputeLost disputeLost = new DisputeLost("resource-external-id",
                "external-id", "service-id", true, eventDetails, Instant.ofEpochSecond(1642579160L));
        return disputeLost.toJsonString();
    }

    @PactVerifyProvider("a dispute won event")
    public String verifyDisputeWonEvent() throws JsonProcessingException {
        DisputeWonEventDetails eventDetails = new DisputeWonEventDetails("a-gateway-account-id");
        DisputeWon disputeWon = new DisputeWon("resource-external-id", "external-id",
                "service-id", true, eventDetails, Instant.ofEpochSecond(1642579160L));
        return disputeWon.toJsonString();
    }

    @PactVerifyProvider("a dispute evidence submitted event")
    public String verifyDisputeEvidenceSubmittedEvent() throws JsonProcessingException {
        DisputeEvidenceSubmittedEventDetails eventDetails = new DisputeEvidenceSubmittedEventDetails("a-gateway-account-id");
        DisputeEvidenceSubmitted disputeEvidenceSubmitted = new DisputeEvidenceSubmitted("resource-external-id",
                "external-id", "service-id", true, eventDetails, Instant.ofEpochSecond(1642579160L));
        return disputeEvidenceSubmitted.toJsonString();
    }

    @PactVerifyProvider("a payment details taken from payment instrument message")
    public String verifyPaymentDetailsTakenFromPaymentInstrumentEvent() throws JsonProcessingException {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId("payment-details-externalId")
                .withServiceId("a-service-id")
                .withGatewayTransactionId("a-provider-transaction-id")
                .withStatus(ChargeStatus.CREATED)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withCardDetails(defaultCardDetails())
                .withGatewayAccountEntity(defaultGatewayAccountEntity())
                .build();
        ChargeEventEntity eventEntity = aValidChargeEventEntity()
                .withCharge(chargeEntity)
                .withGatewayEventDate(ZonedDateTime.now())
                .build();
        PaymentDetailsTakenFromPaymentInstrument event = PaymentDetailsTakenFromPaymentInstrument.from(eventEntity);
        return event.toJsonString();
    }
}
