package uk.gov.pay.connector.paymentprocessor.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeExpiryService;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;

@RunWith(MockitoJUnitRunner.class)
public class DiscrepancyServiceTest {

    private DiscrepancyService discrepancyService;
    @Mock
    private ChargeService chargeService;

    @Mock
    private QueryService queryService;

    @Mock
    private ChargeExpiryService expiryService;

    @Before
    public void beforeTest() {
        discrepancyService = new DiscrepancyService(chargeService, queryService, expiryService);
    }
    
    @Test
    public void aChargeShouldBeCancellable_whenPayAndGatewayStatusesAllowItAndIsOlderThan7Days() throws GatewayException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.now().minusDays(8))
                .withStatus(EXPIRED)
                .build();
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, "Raw response");

        when(chargeService.findChargeById(charge.getExternalId())).thenReturn(charge);
        when(queryService.getChargeGatewayStatus(charge)).thenReturn(chargeQueryResponse);

        discrepancyService.resolveDiscrepancies(Collections.singletonList(charge.getExternalId()));
        verify(expiryService).forceCancelWithGateway(charge);
    }

    @Test
    public void aChargeShouldNotBeCancellable_whenPayAndGatewayStatusesMatch() throws GatewayException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.now().minusDays(8))
                .withStatus(AUTHORISATION_SUCCESS)
                .build();
        
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, "Raw response");
        assertChargeIsNotCancelled(charge, chargeQueryResponse);
    }

    @Test
    public void aChargeShouldNotBeCancellable_whenPayStatusIsSuccess() throws GatewayException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.now().minusDays(8))
                .withStatus(CAPTURED)
                .build();
        
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, "Raw response");
        assertChargeIsNotCancelled(charge, chargeQueryResponse);
    }

    @Test
    public void aChargeShouldNotBeCancellable_whenPayStatusIsUnfinished() throws GatewayException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.now().minusDays(8))
                .withStatus(AUTHORISATION_3DS_READY)
                .build();

        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, "Raw response");
        assertChargeIsNotCancelled(charge, chargeQueryResponse);
    }

    @Test
    public void aChargeShouldNotBeCancellable_whenGatewayStatusIsFinished() throws GatewayException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.now().minusDays(8))
                .withStatus(EXPIRED)
                .build();
        
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(CAPTURED, "Raw response");
        assertChargeIsNotCancelled(charge, chargeQueryResponse);
    }

    @Test
    public void aChargeShouldNotBeCancellable_whenChargeIsLessThan7DaysOld() throws GatewayException {
        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withCreatedDate(ZonedDateTime.now().minusDays(6))
                .withStatus(EXPIRED)
                .build();
        
        ChargeQueryResponse chargeQueryResponse = new ChargeQueryResponse(AUTHORISATION_SUCCESS, "Raw response");
        assertChargeIsNotCancelled(charge, chargeQueryResponse);
    }

    private void assertChargeIsNotCancelled(ChargeEntity charge, ChargeQueryResponse chargeQueryResponse) throws GatewayException {
        when(chargeService.findChargeById(charge.getExternalId())).thenReturn(charge);
        when(queryService.getChargeGatewayStatus(charge)).thenReturn(chargeQueryResponse);

        discrepancyService.resolveDiscrepancies(Collections.singletonList(charge.getExternalId()));
        verifyNoMoreInteractions(expiryService);
    }
}
