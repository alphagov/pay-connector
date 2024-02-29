package uk.gov.pay.connector.gateway.epdq;

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
class EpdqAuthorisationRequestSummaryTest {
    
    @Mock private GatewayAccountEntity mockGatewayAccountEntity;
    @Mock private AuthCardDetails mockAuthCardDetails;

    @Test
    void billingAddressPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.of(mock(Address.class)));
        var epdqAuthorisationRequestSummary = new EpdqAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails);
        assertThat(epdqAuthorisationRequestSummary.billingAddress(), is(PRESENT));
    }

    @Test
    void billingAddressNotPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.empty());
        var epdqAuthorisationRequestSummary = new EpdqAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails);
        assertThat(epdqAuthorisationRequestSummary.billingAddress(), is(NOT_PRESENT));
    }

    @Test
    void requires3dsTrueMeansDataFor3dsPresent() {
        given(mockGatewayAccountEntity.getCardConfigurationEntity().isRequires3ds()).willReturn(true);
        var epdqAuthorisationRequestSummary = new EpdqAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails);
        assertThat(epdqAuthorisationRequestSummary.dataFor3ds(), is(PRESENT));
    }

    @Test
    void requires3dsFalseMeansDataFor3dsNotPresent() {
        given(mockGatewayAccountEntity.getCardConfigurationEntity().isRequires3ds()).willReturn(false);
        var epdqAuthorisationRequestSummary = new EpdqAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails);
        assertThat(epdqAuthorisationRequestSummary.dataFor3ds(), is(NOT_PRESENT));
    }

    @Test
    void requires3dsFalseMeansDataFor3ds2NotPresent() {
        given(mockGatewayAccountEntity.getCardConfigurationEntity().isRequires3ds()).willReturn(false);
        var epdqAuthorisationRequestSummary = new EpdqAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails);
        assertThat(epdqAuthorisationRequestSummary.dataFor3ds2(), is(NOT_PRESENT));
    }
    
    @Test
    void requires3dsTrueAnd3dsVersion1MeansDataFor3ds2NotPresent() {
        given(mockGatewayAccountEntity.getCardConfigurationEntity().isRequires3ds()).willReturn(true);
        given(mockGatewayAccountEntity.getCardConfigurationEntity().getIntegrationVersion3ds()).willReturn(1);
        var epdqAuthorisationRequestSummary = new EpdqAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails);
        assertThat(epdqAuthorisationRequestSummary.dataFor3ds2(), is(NOT_PRESENT));
    }

    @Test
    void requires3dsTrueAnd3dsVersion2MeansDataFor3ds2Present() {
        given(mockGatewayAccountEntity.getCardConfigurationEntity().isRequires3ds()).willReturn(true);
        given(mockGatewayAccountEntity.getCardConfigurationEntity().getIntegrationVersion3ds()).willReturn(2);
        var epdqAuthorisationRequestSummary = new EpdqAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails);
        assertThat(epdqAuthorisationRequestSummary.dataFor3ds2(), is(PRESENT));
    }

    @Test
    void deviceDataCollectionResultAlwaysNotApplicable() {
        var epdqAuthorisationRequestSummary = new EpdqAuthorisationRequestSummary(mockGatewayAccountEntity, mockAuthCardDetails);
        assertThat(epdqAuthorisationRequestSummary.deviceDataCollectionResult(), is(NOT_APPLICABLE));
    }

}
