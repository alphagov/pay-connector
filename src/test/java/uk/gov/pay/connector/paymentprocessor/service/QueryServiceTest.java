package uk.gov.pay.connector.paymentprocessor.service;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.response.BaseInquiryResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;

@ExtendWith(MockitoExtension.class)
public class QueryServiceTest {
    
    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private PaymentProviders paymentProviders;

    @Mock
    private GatewayAccountCredentialsService gatewayAccountCredentialsService;

    @Mock
    private BaseInquiryResponse mockGatewayResponse;

    @InjectMocks
    private QueryService queryService;

    @BeforeEach
    void setUp() {
        when(paymentProviders.byName(any())).thenReturn(paymentProvider);
    }

    @Test
    void returnSuccessfulGatewayResponse() throws Exception {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(defaultGatewayAccountEntity())
                .build();
        Charge charge = Charge.from(chargeEntity);
        GatewayAccountEntity gatewayAccountEntity = chargeEntity.getGatewayAccount();
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = chargeEntity.getGatewayAccountCredentialsEntity();
        ChargeQueryResponse response = new ChargeQueryResponse(AUTHORISATION_3DS_REQUIRED, mockGatewayResponse);

        when(paymentProvider.queryPaymentStatus(any(ChargeQueryGatewayRequest.class))).thenReturn(response);
        when(gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));

        assertThat(queryService.getChargeGatewayStatus(chargeEntity), is(response));
    }

    @Test
    void returnChargeStatusFromGateway() throws Exception {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(defaultGatewayAccountEntity())
                .withGatewayAccountCredentialsEntity(defaultGatewayAccountCredentialsEntity())
                .build();
        Charge charge = Charge.from(chargeEntity);
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = chargeEntity.getGatewayAccountCredentialsEntity();
        GatewayAccountEntity gatewayAccountEntity = chargeEntity.getGatewayAccount();
        ChargeQueryResponse response = new ChargeQueryResponse(AUTHORISATION_3DS_REQUIRED, mockGatewayResponse);

        when(gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));
        when(paymentProvider.queryPaymentStatus(any(ChargeQueryGatewayRequest.class))).thenReturn(response);
        Optional<ChargeStatus> chargeStatus = queryService.getMappedGatewayStatus(chargeEntity);

        assertThat(chargeStatus.isPresent(), is(true));
        assertThat(chargeStatus.get(), is(AUTHORISATION_3DS_REQUIRED));
    }

    @Test
    void returnEmptyOptionalIfGatewayThrowsAnException() throws Exception {
        ChargeEntity chargeEntity = aValidChargeEntity().build();
        Charge charge = Charge.from(chargeEntity);
        GatewayAccountEntity gatewayAccountEntity = chargeEntity.getGatewayAccount();
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = chargeEntity.getGatewayAccountCredentialsEntity();

        when(paymentProvider.queryPaymentStatus(any(ChargeQueryGatewayRequest.class))).thenThrow(UnsupportedOperationException.class);
        when(gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));
        Optional<ChargeStatus> chargeStatus = queryService.getMappedGatewayStatus(chargeEntity);

        assertThat(chargeStatus.isPresent(), is(false));
    }

    @Test
    void isTerminableWithGateway_returnsTrueForNotFinishedExternalStatus() throws Exception {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(defaultGatewayAccountEntity())
                .build();
        Charge charge = Charge.from(chargeEntity);
        GatewayAccountEntity gatewayAccountEntity = chargeEntity.getGatewayAccount();
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = chargeEntity.getGatewayAccountCredentialsEntity();

        ChargeQueryResponse response = new ChargeQueryResponse(AUTHORISATION_3DS_REQUIRED, mockGatewayResponse);
        when(paymentProvider.queryPaymentStatus(any(ChargeQueryGatewayRequest.class))).thenReturn(response);
        when(gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));

        assertThat(queryService.isTerminableWithGateway(chargeEntity), is(true));
    }

    @Test
    void isTerminableWithGateway_returnsFalseForFinishedExternalStatus() throws Exception {
        ChargeEntity chargeEntity = aValidChargeEntity().build();
        Charge charge = Charge.from(chargeEntity);
        GatewayAccountEntity gatewayAccountEntity = chargeEntity.getGatewayAccount();
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = chargeEntity.getGatewayAccountCredentialsEntity();

        ChargeQueryResponse response = new ChargeQueryResponse(CAPTURED, mockGatewayResponse);
        when(paymentProvider.queryPaymentStatus(any(ChargeQueryGatewayRequest.class))).thenReturn(response);
        when(gatewayAccountCredentialsService.findCredentialFromCharge(charge, gatewayAccountEntity)).thenReturn(Optional.of(gatewayAccountCredentialsEntity));

        assertThat(queryService.isTerminableWithGateway(chargeEntity), is(false));
    }
}
