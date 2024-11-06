package uk.gov.pay.connector.model.domain;


import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.common.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.ArrayMatching.arrayContainingInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

class ChargeEntityTest {

    @Test
    void shouldHaveTheGivenStatus() {
        assertEquals(aValidChargeEntity().withStatus(CREATED).build().getStatus(), CREATED.toString());
        assertEquals(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().getStatus(), ENTERING_CARD_DETAILS.toString());
    }


    @Test
    void shouldHaveAtLeastOneOfTheGivenStatuses() {
        assertEquals(aValidChargeEntity().withStatus(CREATED).build().getStatus(), CREATED.toString());
        assertEquals(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().getStatus(), ENTERING_CARD_DETAILS.toString());
    }


    @Test
    void shouldHaveTheExternalGivenStatus() {
        assertTrue(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(EXTERNAL_CREATED));
        assertTrue(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(EXTERNAL_STARTED));
    }

    @Test
    void shouldHaveAtLeastOneOfTheExternalGivenStatuses() {
        assertTrue(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(EXTERNAL_CREATED, EXTERNAL_STARTED, EXTERNAL_SUBMITTED));
        assertTrue(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(EXTERNAL_STARTED, EXTERNAL_SUCCESS));
    }

    @Test
    void shouldHaveNoneOfTheExternalGivenStatuses() {
        assertFalse(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus());
        assertFalse(aValidChargeEntity().withStatus(CREATED).build().hasExternalStatus(EXTERNAL_STARTED, EXTERNAL_SUBMITTED, EXTERNAL_SUCCESS));
        assertFalse(aValidChargeEntity().withStatus(ENTERING_CARD_DETAILS).build().hasExternalStatus(EXTERNAL_CREATED, EXTERNAL_SUCCESS));
    }

    @Test
    void shouldAllowAValidStatusTransition() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(CREATED).build();
        chargeCreated.setStatus(ENTERING_CARD_DETAILS);
        assertThat(chargeCreated.getStatus(), is(ENTERING_CARD_DETAILS.toString()));
    }

    @Test
    void shouldRejectAnInvalidStatusTransition() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(CREATED).build();
        assertThrows( InvalidStateTransitionException.class, () -> {
            chargeCreated.setStatus(CAPTURED);
        });
    }

    @Test
    void shouldReturnRightAmountOfFees() {
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
    void shouldReturnNoFee() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(CREATED).build();
        Optional<Long> totalFee = chargeCreated.getFeeAmount();
        assertFalse(totalFee.isPresent());
    }

    @Test
    void shouldReturnAmountMinusFeeForSuccessfulPayment() {
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
    void shouldReturnTotalAmountMinusFeeForSuccessfulPaymentWithCorporateSurcharge() {
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
    void shouldReturnNegativeNetAmountForFailedPaymentWithFees() {
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
    void shouldReturnEmptyOptionalWhenThereAreNoFees() {
        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(1000L)
                .withStatus(CAPTURED).build();

        Optional<Long> netAmount = chargeCreated.getNetAmount();
        assertThat(netAmount.isPresent(), is(false));
    }

    @Test
    void shouldReturnLoggingFieldsWithoutAgreementExternalId() {
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
    void shouldReturnLoggingFieldsWithAgreementExternalId() {
        Long gatewayAccountId = 12L;
        String externalId = "anExternalId";
        String paymentProvider = "sandbox";
        String agreementExternalId = "anAgreementExternalId";
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
                .withAgreementEntity(anAgreementEntity().withExternalId(agreementExternalId).build())
                .build();
        Object[] structuredLoggingArgs = chargeEntity.getStructuredLoggingArgs();
        assertThat(structuredLoggingArgs, arrayContainingInAnyOrder(
                kv(PAYMENT_EXTERNAL_ID, externalId),
                kv(GATEWAY_ACCOUNT_ID, gatewayAccountId),
                kv(PROVIDER, paymentProvider),
                kv(GATEWAY_ACCOUNT_TYPE, gatewayAccountType.toString()),
                kv(AUTHORISATION_MODE, authorisationMode),
                kv(AGREEMENT_EXTERNAL_ID, agreementExternalId)
        ));
    }

    @Test
    void shouldSetRequires3dsToTrueWhenSettingAuth3dsRequiredEntity() {
        Auth3dsRequiredEntity auth3dsRequiredEntity = new Auth3dsRequiredEntity();
        auth3dsRequiredEntity.setThreeDsVersion("2.1");
        ChargeEntity chargeEntity = aValidChargeEntity().build();

        assertThat(chargeEntity.getRequires3ds(), is(nullValue()));

        chargeEntity.set3dsRequiredDetails(auth3dsRequiredEntity);

        assertTrue(chargeEntity.getRequires3ds());
    }
}
