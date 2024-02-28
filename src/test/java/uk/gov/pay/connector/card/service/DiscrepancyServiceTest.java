package uk.gov.pay.connector.card.service;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.model.response.BaseInquiryResponse;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;

@ExtendWith(MockitoExtension.class)
public class DiscrepancyServiceTest {

    private DiscrepancyService discrepancyService;
    @Mock
    private ChargeService chargeService;

    @Mock
    private QueryService queryService;

    @Mock
    private ChargeExpiryService expiryService;

    @Mock
    private GatewayAccountService gatewayAccountService;

    @Mock
    private BaseInquiryResponse mockGatewayResponse;

    @BeforeEach
    void beforeTest() {
        discrepancyService = new DiscrepancyService(chargeService, queryService, expiryService, gatewayAccountService);
    }

    @Test
    void aChargeShouldBeCancellable_whenPayAndGatewayStatusesAllowItAndIsOlderThan2Days() throws GatewayException {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.now().minus(Duration.ofDays(3)))
                .withStatus(EXPIRED)
                .build();
        Charge charge = Charge.from(chargeEntity);
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, mockGatewayResponse);
        when(chargeService.findCharge(chargeEntity.getExternalId())).thenReturn(Optional.of(charge));
        when(chargeService.findChargeByExternalId(chargeEntity.getExternalId())).thenReturn(chargeEntity);
        when(queryService.getChargeGatewayStatus(charge, chargeEntity.getGatewayAccount())).thenReturn(chargeQueryResponse);
        when(gatewayAccountService.getGatewayAccount(charge.getGatewayAccountId())).thenReturn(Optional.of(chargeEntity.getGatewayAccount()));
        when(expiryService.forceCancelWithGateway(chargeEntity)).thenReturn(true);

        discrepancyService.resolveDiscrepancies(Collections.singletonList(chargeEntity.getExternalId()));

        verify(expiryService).forceCancelWithGateway(chargeEntity);
    }

    @Test
    void aChargeShouldNotBeCancellable_whenPayAndGatewayStatusesMatch() throws GatewayException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.now().minus(Duration.ofDays(3)))
                .withStatus(AUTHORISATION_SUCCESS)
                .build();

        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, mockGatewayResponse);
        assertChargeIsNotCancelled(charge, chargeQueryResponse);
    }

    @Test
    void aChargeShouldNotBeCancellable_whenPayStatusIsSuccess() throws GatewayException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.now().minus(Duration.ofDays(3)))
                .withStatus(CAPTURED)
                .build();

        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, mockGatewayResponse);
        assertChargeIsNotCancelled(charge, chargeQueryResponse);
    }

    @Test
    void aChargeShouldNotBeCancellable_whenPayStatusIsUnfinished() throws GatewayException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.now().minus(Duration.ofDays(3)))
                .withStatus(AUTHORISATION_3DS_READY)
                .build();

        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, mockGatewayResponse);
        assertChargeIsNotCancelled(charge, chargeQueryResponse);
    }

    @Test
    void aChargeShouldNotBeCancellable_whenGatewayStatusIsFinished() throws GatewayException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.now().minus(Duration.ofDays(3)))
                .withStatus(EXPIRED)
                .build();

        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(CAPTURED, mockGatewayResponse);
        assertChargeIsNotCancelled(charge, chargeQueryResponse);
    }

    @Test
    void aChargeShouldNotBeCancellable_whenChargeIsLessThan2DaysOld() throws GatewayException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(Instant.now().minus(Duration.ofDays(1)))
                .withStatus(EXPIRED)
                .build();

        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, mockGatewayResponse);
        assertChargeIsNotCancelled(charge, chargeQueryResponse);
    }

    private void assertChargeIsNotCancelled(ChargeEntity chargeEntity, ChargeQueryResponse chargeQueryResponse) throws GatewayException {
        Charge charge = Charge.from(chargeEntity);
        when(chargeService.findCharge(chargeEntity.getExternalId())).thenReturn(Optional.of(charge));
        when(gatewayAccountService.getGatewayAccount(chargeEntity.getGatewayAccount().getId())).thenReturn(Optional.of(chargeEntity.getGatewayAccount()));
        when(queryService.getChargeGatewayStatus(charge, chargeEntity.getGatewayAccount())).thenReturn(chargeQueryResponse);

        discrepancyService.resolveDiscrepancies(Collections.singletonList(chargeEntity.getExternalId()));
        verifyNoMoreInteractions(expiryService);
    }
}
