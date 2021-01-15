package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

@ExtendWith(MockitoExtension.class)
class WorldpayAuthorisationRequestSummaryTest {

    @Mock private ChargeEntity mockChargeEntity;
    @Mock private GatewayAccountEntity mockGatewayAccountEntity;
    @Mock private AuthCardDetails mockAuthCardDetails;
    @Mock private Worldpay3dsFlexCredentials mockWorldpay3dsFlexCredentials;
    
    @Test
    void billingAddressPresent() {
        given(mockChargeEntity.getGatewayAccount()).willReturn(mockGatewayAccountEntity);
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.of(mock(Address.class)));
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(worldpayAuthorisationRequestSummary.billingAddress(), is(PRESENT));
    }

    @Test
    void billingAddressNotPresent() {
        given(mockChargeEntity.getGatewayAccount()).willReturn(mockGatewayAccountEntity);
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.empty());
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(worldpayAuthorisationRequestSummary.billingAddress(), is(NOT_PRESENT));
    }

    @Test
    void requires3dsFalseMeansDataFor3dsNotPresent() {
        given(mockChargeEntity.getGatewayAccount()).willReturn(mockGatewayAccountEntity);
        given(mockGatewayAccountEntity.isRequires3ds()).willReturn(false);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(worldpayAuthorisationRequestSummary.dataFor3ds(), is(NOT_PRESENT));
    }

    @Test
    void deviceDataCollectionResultPresentMeansDataFor3dsPresent() {
        given(mockChargeEntity.getGatewayAccount()).willReturn(mockGatewayAccountEntity);
        given(mockAuthCardDetails.getWorldpay3dsFlexDdcResult()).willReturn(Optional.of("DDC Result"));
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(worldpayAuthorisationRequestSummary.dataFor3ds(), is(PRESENT));
    }

    @Test
    void deviceDataCollectionResultPresent() {
        given(mockChargeEntity.getGatewayAccount()).willReturn(mockGatewayAccountEntity);
        given(mockAuthCardDetails.getWorldpay3dsFlexDdcResult()).willReturn(Optional.of("DDC Result"));
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(worldpayAuthorisationRequestSummary.deviceDataCollectionResult(), is(PRESENT));
    }

    @Test
    void dataFor3ds2AlwaysNotApplicable() {
        given(mockChargeEntity.getGatewayAccount()).willReturn(mockGatewayAccountEntity);
        given(mockGatewayAccountEntity.isRequires3ds()).willReturn(false);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(worldpayAuthorisationRequestSummary.dataFor3ds2(), is(NOT_APPLICABLE));
    }

}
