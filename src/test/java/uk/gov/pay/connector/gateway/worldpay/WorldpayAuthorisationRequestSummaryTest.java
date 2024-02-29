package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

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
    
    @Mock private GatewayAccountEntity mockGatewayAccountEntity;
    @Mock private AuthCardDetails mockAuthCardDetails;
    
    @Test
    void billingAddressPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.of(mock(Address.class)));
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.billingAddress(), is(PRESENT));
    }

    @Test
    void billingAddressNotPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.empty());
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.billingAddress(), is(NOT_PRESENT));
    }

    @Test
    void requires3dsFalseMeansDataFor3dsNotPresent() {
        given(mockGatewayAccountEntity.getCardConfigurationEntity().isRequires3ds()).willReturn(false);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.dataFor3ds(), is(NOT_PRESENT));
    }

    @Test
    void deviceDataCollectionResultPresentMeansDataFor3dsPresent() {
        given(mockAuthCardDetails.getWorldpay3dsFlexDdcResult()).willReturn(Optional.of("DDC Result"));
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.dataFor3ds(), is(PRESENT));
    }

    @Test
    void deviceDataCollectionResultPresent() {
        given(mockAuthCardDetails.getWorldpay3dsFlexDdcResult()).willReturn(Optional.of("DDC Result"));
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.deviceDataCollectionResult(), is(PRESENT));
    }

    @Test
    void dataFor3ds2AlwaysNotApplicable() {
        given(mockGatewayAccountEntity.getCardConfigurationEntity().isRequires3ds()).willReturn(false);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.dataFor3ds2(), is(NOT_APPLICABLE));
    }

    @Test
    void isSetUpAgreementTrue() {
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails, true);
        assertThat(worldpayAuthorisationRequestSummary.setUpAgreement(), is(PRESENT));
    }

    @Test
    void isSetUpAgreementFalse() {
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.setUpAgreement(), is(NOT_PRESENT));
    }
}
