package uk.gov.pay.connector.gateway.smartpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
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
class SmartpayAuthorisationRequestSummaryTest {

    @Mock private ChargeEntity mockChargeEntity;
    @Mock private GatewayAccountEntity mockGatewayAccountEntity;
    @Mock private AuthCardDetails mockAuthCardDetails;

    @BeforeEach
    void setUp() {
        given(mockChargeEntity.getGatewayAccount()).willReturn(mockGatewayAccountEntity);
    }

    @Test
    void billingAddressPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.of(mock(Address.class)));
        var smartpayAuthorisationRequestSummary = new SmartpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(smartpayAuthorisationRequestSummary.billingAddress(), is(PRESENT));
    }

    @Test
    void billingAddressNotPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.empty());
        var smartpayAuthorisationRequestSummary = new SmartpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(smartpayAuthorisationRequestSummary.billingAddress(), is(NOT_PRESENT));
    }

    @Test
    void requires3dsTrueMeansDataFor3dsPresent() {
        given(mockGatewayAccountEntity.isRequires3ds()).willReturn(true);
        var smartpayAuthorisationRequestSummary = new SmartpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(smartpayAuthorisationRequestSummary.dataFor3ds(), is(PRESENT));
    }

    @Test
    void requires3dsFalseMeansDataFor3dsNotPresent() {
        given(mockGatewayAccountEntity.isRequires3ds()).willReturn(false);
        var smartpayAuthorisationRequestSummary = new SmartpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(smartpayAuthorisationRequestSummary.dataFor3ds(), is(NOT_PRESENT));
    }

    @Test
    void dataFor3ds2AlwaysNotApplicable() {
        var smartpayAuthorisationRequestSummary = new SmartpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(smartpayAuthorisationRequestSummary.dataFor3ds2(), is(NOT_APPLICABLE));
    }

    @Test
    void deviceDataCollectionResultAlwaysNotApplicable() {
        var smartpayAuthorisationRequestSummary = new SmartpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails);
        assertThat(smartpayAuthorisationRequestSummary.deviceDataCollectionResult(), is(NOT_APPLICABLE));
    }

}
