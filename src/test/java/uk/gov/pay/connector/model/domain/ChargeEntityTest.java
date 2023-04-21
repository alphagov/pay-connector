package uk.gov.pay.connector.model.domain;

import org.junit.Test;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.time.Instant;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.ArrayMatching.arrayContainingInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.service.payments.logging.LoggingKeys.AGREEMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.AUTHORISATION_MODE;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_TYPE;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PROVIDER;

public class ChargeEntityTest {

    @Test
    public void shouldHaveTheGivenStatus() {
        assertEquals(aValidChargeEntity().withStatus(CREATED).build().getStatus(), CREATED.toString());
        assertEquals(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().getStatus(), ENTERING_CARD_DETAILS.toString());
    }


    @Test
    public void shouldHaveAtLeastOneOfTheGivenStatuses() {
        assertEquals(aValidChargeEntity().withStatus(CREATED).build().getStatus(), CREATED.toString());
        assertEquals(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().getStatus(), ENTERING_CARD_DETAILS.toString());
    }


    @Test
    public void shouldHaveTheExternalGivenStatus() {
        assertTrue(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(EXTERNAL_CREATED));
        assertTrue(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(EXTERNAL_STARTED));
    }

    @Test
    public void shouldHaveAtLeastOneOfTheExternalGivenStatuses() {
        assertTrue(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(EXTERNAL_CREATED, EXTERNAL_STARTED, EXTERNAL_SUBMITTED));
        assertTrue(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(EXTERNAL_STARTED, EXTERNAL_SUCCESS));
    }

    @Test
    public void shouldHaveNoneOfTheExternalGivenStatuses() {
        assertFalse(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus());
        assertFalse(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(EXTERNAL_STARTED, EXTERNAL_SUBMITTED, EXTERNAL_SUCCESS));
        assertFalse(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(EXTERNAL_CREATED, EXTERNAL_SUCCESS));
    }

    @Test
    public void shouldAllowAValidStatusTransition() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(CREATED).build();
        chargeCreated.setStatus(ENTERING_CARD_DETAILS);
        assertThat(chargeCreated.getStatus(), is(ENTERING_CARD_DETAILS.toString()));
    }

    @Test(expected = InvalidStateTransitionException.class)
    public void shouldRejectAnInvalidStatusTransition() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(CREATED).build();
        chargeCreated.setStatus(CAPTURED);
    }

    @Test
    public void shouldReturnRightAmountOfFees() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity()
                .withFee(Fee.of(FeeType.TRANSACTION, 100L))
                .withFee(Fee.of(FeeType.RADAR, 10L))
                .withFee(Fee.of(FeeType.THREE_D_S, 20L))
                .withStatus(CAPTURED).build();

        Optional<Long> totalFee = chargeCreated.getFeeAmount();
        assertTrue(totalFee.isPresent());
        assertEquals(totalFee.get().longValue(), 130L);
    }

    @Test
    public void shouldReturnNoFee() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(CREATED).build();
        Optional<Long> totalFee = chargeCreated.getFeeAmount();
        assertFalse(totalFee.isPresent());
    }

    @Test
    public void shouldReturnAmountMinusFeeForSuccessfulPayment() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(1000L)
                .withFee(Fee.of(FeeType.TRANSACTION, 100L))
                .withFee(Fee.of(FeeType.RADAR, 10L))
                .withFee(Fee.of(FeeType.THREE_D_S, 20L))
                .withStatus(CAPTURED).build();

        Optional<Long> netAmount = chargeCreated.getNetAmount();
        assertThat(netAmount.isPresent(), is(true));
        assertThat(netAmount.get(), is(870L));
    }

    @Test
    public void shouldReturnTotalAmountMinusFeeForSuccessfulPaymentWithCorporateSurcharge() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(1000L)
                .withCorporateSurcharge(250L)
                .withFee(Fee.of(FeeType.TRANSACTION, 100L))
                .withFee(Fee.of(FeeType.RADAR, 10L))
                .withFee(Fee.of(FeeType.THREE_D_S, 20L))
                .withStatus(CAPTURED).build();

        Optional<Long> netAmount = chargeCreated.getNetAmount();
        assertThat(netAmount.isPresent(), is(true));
        assertThat(netAmount.get(), is(1120L));
    }

    @Test
    public void shouldReturnNegativeNetAmountForFailedPaymentWithFees() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(1000L)
                .withCorporateSurcharge(250L)
                .withFee(Fee.of(FeeType.TRANSACTION, 100L))
                .withFee(Fee.of(FeeType.RADAR, 10L))
                .withFee(Fee.of(FeeType.THREE_D_S, 20L))
                .withStatus(AUTHORISATION_REJECTED).build();

        Optional<Long> netAmount = chargeCreated.getNetAmount();
        assertThat(netAmount.isPresent(), is(true));
        assertThat(netAmount.get(), is(-130L));
    }

    @Test
    public void shouldReturnEmptyOptionalWhenThereAreNoFees() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(1000L)
                .withStatus(CAPTURED).build();

        Optional<Long> netAmount = chargeCreated.getNetAmount();
        assertThat(netAmount.isPresent(), is(false));
    }

    @Test
    public void shouldReturnLoggingFieldsWithoutAgreementId() {
        Long gatewayAccountId = 12L;
        String externalId = "anExternalId";
        String paymentProvider = "sandbox";
        GatewayAccountType gatewayAccountType = GatewayAccountType.LIVE;
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withId(gatewayAccountId)
                .withType(gatewayAccountType)
                .build();
        AuthorisationMode authorisationMode = AuthorisationMode.WEB;
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalId)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(paymentProvider)
                .withAuthorisationMode(authorisationMode)
                .build();
        Object[] structuredLoggingArgs = chargeEntity.getStructuredLoggingArgs();
        assertThat(structuredLoggingArgs, arrayContainingInAnyOrder(
                kv(PAYMENT_EXTERNAL_ID, externalId),
                kv(GATEWAY_ACCOUNT_ID, gatewayAccountId),
                kv(PROVIDER, paymentProvider),
                kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountType.toString()),
                kv(AUTHORISATION_MODE, authorisationMode)
        ));
    }

    @Test
    public void shouldReturnLoggingFieldsWithAgreementId() {
        Long gatewayAccountId = 12L;
        String externalId = "anExternalId";
        String paymentProvider = "sandbox";
        String agreementId = "anAgreementId";
        GatewayAccountType gatewayAccountType = GatewayAccountType.LIVE;
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withId(gatewayAccountId)
                .withType(gatewayAccountType)
                .build();
        AuthorisationMode authorisationMode = AuthorisationMode.WEB;
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId(externalId)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(paymentProvider)
                .withAuthorisationMode(authorisationMode)
                .withAgreementEntity(anAgreementEntity().withExternalId(agreementId).build())
                .build();
        Object[] structuredLoggingArgs = chargeEntity.getStructuredLoggingArgs();
        assertThat(structuredLoggingArgs, arrayContainingInAnyOrder(
                kv(PAYMENT_EXTERNAL_ID, externalId),
                kv(GATEWAY_ACCOUNT_ID, gatewayAccountId),
                kv(PROVIDER, paymentProvider),
                kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountType.toString()),
                kv(AUTHORISATION_MODE, authorisationMode),
                kv(AGREEMENT_EXTERNAL_ID, agreementId)
        ));
    }
    
    @Test
    public void shouldAssignAgreementIdToBothAgreementIdAndAgreementExternalIdForWebCharges() {
        String testAgreementId = "test-agreement-id-123";
        AgreementEntity testAgreement = AgreementEntity.AgreementEntityBuilder.anAgreementEntity(Instant.now()).build();
        testAgreement.setExternalId(testAgreementId);

        ChargeEntity chargeCreated = ChargeEntity.WebChargeEntityBuilder.aWebChargeEntity().withAgreementEntity(testAgreement).build();
        
        assertThat(chargeCreated.getExternalAgreement().isPresent(), is(true));
        assertThat(chargeCreated.getExternalAgreement(), is(chargeCreated.getAgreement()));
        assertThat(chargeCreated.getExternalAgreement().get().getExternalId(), is(testAgreementId));
    }
}
