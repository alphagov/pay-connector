package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.service.payments.commons.model.AgreementPaymentType;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_APPLICABLE;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.NOT_PRESENT;
import static uk.gov.pay.connector.gateway.model.AuthorisationRequestSummary.Presence.PRESENT;

@ExtendWith(MockitoExtension.class)
class WorldpayAuthorisationRequestSummaryTest {

    @Mock private ChargeEntity mockChargeEntity;
    @Mock private GatewayAccountEntity mockGatewayAccountEntity;
    @Mock private AuthCardDetails mockAuthCardDetails;
    
    @BeforeEach
    public void setUp() {
        lenient().when(mockChargeEntity.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
    }

    @Test
    void billingAddressPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.of(mock(Address.class)));
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.billingAddress(), is(PRESENT));
    }

    @Test
    void billingAddressNotPresent() {
        given(mockAuthCardDetails.getAddress()).willReturn(Optional.empty());
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.billingAddress(), is(NOT_PRESENT));
    }

    @Test
    void requires3dsFalseMeansDataFor3dsNotPresent() {
        given(mockGatewayAccountEntity.isRequires3ds()).willReturn(false);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.dataFor3ds(), is(NOT_PRESENT));
    }

    @Test
    void deviceDataCollectionResultPresentMeansDataFor3dsPresent() {
        given(mockAuthCardDetails.getWorldpay3dsFlexDdcResult()).willReturn(Optional.of("DDC Result"));
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.dataFor3ds(), is(PRESENT));
    }

    @Test
    void deviceDataCollectionResultPresent() {
        given(mockAuthCardDetails.getWorldpay3dsFlexDdcResult()).willReturn(Optional.of("DDC Result"));
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.deviceDataCollectionResult(), is(PRESENT));
    }

    @Test
    void dataFor3ds2AlwaysNotApplicable() {
        given(mockGatewayAccountEntity.isRequires3ds()).willReturn(false);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.dataFor3ds2(), is(NOT_APPLICABLE));
    }

    @Test
    void isSetUpAgreementTrue() {
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, true);
        assertThat(worldpayAuthorisationRequestSummary.setUpAgreement(), is(PRESENT));
    }

    @Test
    void isSetUpAgreementFalse() {
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.setUpAgreement(), is(NOT_PRESENT));
    }

    @Test
    void corporateCardUsed() {
        given(mockAuthCardDetails.isCorporateCard()).willReturn(true);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.corporateCard(), is(true));
    }

    @Test
    void corporateCardNotUsed() {
        given(mockAuthCardDetails.isCorporateCard()).willReturn(false);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.corporateCard(), is(false));
    }

    @Test
    void corporateExemptionRequested() {
        given(mockChargeEntity.getExemption3dsRequested()).willReturn(Exemption3dsType.CORPORATE);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.corporateExemptionRequested(), is(Optional.of(Boolean.TRUE)));
    }

    @Test
    void optimisedExemptionRequested() {
        given(mockChargeEntity.getExemption3dsRequested()).willReturn(Exemption3dsType.OPTIMISED);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.corporateExemptionRequested(), is(Optional.of(Boolean.FALSE)));
    }

    @Test
    void noExemptionRequested() {
        given(mockChargeEntity.getExemption3dsRequested()).willReturn(null);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.corporateExemptionRequested(), is(Optional.of(Boolean.FALSE)));
    }

    @Test
    void corporateExemptionHonoured() {
        given(mockChargeEntity.getExemption3dsRequested()).willReturn(Exemption3dsType.CORPORATE);
        given(mockChargeEntity.getExemption3ds()).willReturn(Exemption3ds.EXEMPTION_HONOURED);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.corporateExemptionResult(), is(Optional.of(Exemption3ds.EXEMPTION_HONOURED)));
    }

    @Test
    void corporateExemptionRejected() {
        given(mockChargeEntity.getExemption3dsRequested()).willReturn(Exemption3dsType.CORPORATE);
        given(mockChargeEntity.getExemption3ds()).willReturn(Exemption3ds.EXEMPTION_REJECTED);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.corporateExemptionResult(), is(Optional.of(Exemption3ds.EXEMPTION_REJECTED)));
    }

    @Test
    void corporateExemptionOutOfScope() {
        given(mockChargeEntity.getExemption3dsRequested()).willReturn(Exemption3dsType.CORPORATE);
        given(mockChargeEntity.getExemption3ds()).willReturn(Exemption3ds.EXEMPTION_OUT_OF_SCOPE);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.corporateExemptionResult(), is(Optional.of(Exemption3ds.EXEMPTION_OUT_OF_SCOPE)));
    }

    @Test
    void corporateExemptionHasNoResult() {
        given(mockChargeEntity.getExemption3dsRequested()).willReturn(Exemption3dsType.CORPORATE);
        given(mockChargeEntity.getExemption3ds()).willReturn(null);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, false);
        assertThat(worldpayAuthorisationRequestSummary.corporateExemptionResult(), is(Optional.empty()));
    }
    
    @Test
    void agreementPaymentTypePresent() {
        given(mockChargeEntity.getAgreementPaymentType()).willReturn(AgreementPaymentType.UNSCHEDULED);
        var worldpayAuthorisationRequestSummary = new WorldpayAuthorisationRequestSummary(mockChargeEntity, mockAuthCardDetails, true);
        assertThat(worldpayAuthorisationRequestSummary.setUpAgreement(), is(PRESENT));
        assertThat(worldpayAuthorisationRequestSummary.agreementPaymentType().isPresent(), is(true));
        assertThat(worldpayAuthorisationRequestSummary.agreementPaymentType().get(), is(AgreementPaymentType.UNSCHEDULED));

    }
}
