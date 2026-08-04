package uk.gov.pay.connector.queue.tasks.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.model.request.DeleteStoredPaymentDetailsGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntityFixture.aPaymentInstrumentEntity;

@ExtendWith(MockitoExtension.class)
class DeleteStoredPaymentDetailsTaskHandlerTest {

    private static final String AGREEMENT_EXTERNAL_ID = "agreement-external-id-123";
    private static final String PAYMENT_INSTRUMENT_EXTERNAL_ID = "payment-instrument-ext-id-123";

    @Mock
    private AgreementService agreementService;
    @Mock
    private PaymentInstrumentService paymentInstrumentService;
    @Mock
    private PaymentProviders paymentProviders;
    @Mock
    private PaymentProvider paymentProvider;

    private DeleteStoredPaymentDetailsTaskHandler taskHandler;

    @BeforeEach
    void setUp() {
        taskHandler = new DeleteStoredPaymentDetailsTaskHandler(agreementService, paymentInstrumentService, paymentProviders);
    }
    @Test
    void shouldBuildRequestAndDelegateToPaymentProvider() throws Exception {
        AgreementEntity agreementEntity = createAgreement();
        PaymentInstrumentEntity paymentInstrumentEntity = createPaymentInstrument();

        when(agreementService.findByExternalId(AGREEMENT_EXTERNAL_ID)).thenReturn(agreementEntity);
        when(paymentInstrumentService.findByExternalId(PAYMENT_INSTRUMENT_EXTERNAL_ID)).thenReturn(paymentInstrumentEntity);
        when(paymentProviders.byName(ADYEN)).thenReturn(paymentProvider);

        taskHandler.process(AGREEMENT_EXTERNAL_ID, PAYMENT_INSTRUMENT_EXTERNAL_ID);

        ArgumentCaptor<DeleteStoredPaymentDetailsGatewayRequest> captor = ArgumentCaptor.forClass(DeleteStoredPaymentDetailsGatewayRequest.class);
        verify(paymentProvider).deleteStoredPaymentDetails(captor.capture());

        DeleteStoredPaymentDetailsGatewayRequest request = captor.getValue();
        assertThat(request.getAgreementExternalId(), is(AGREEMENT_EXTERNAL_ID));
        assertThat(request.getRecurringAuthToken(), hasEntry("storedPaymentMethodId", "stored-payment-method-id-123"));
        assertThat(request.getGatewayAccountType(), is("test"));
        assertThat(request.isLive(), is(false));
    }

    @Test
    void shouldPropagateExceptionWhenPaymentProviderDeleteFails() throws Exception {
        AgreementEntity agreementEntity = createAgreement();
        PaymentInstrumentEntity paymentInstrumentEntity = createPaymentInstrument();

        when(agreementService.findByExternalId(AGREEMENT_EXTERNAL_ID)).thenReturn(agreementEntity);
        when(paymentInstrumentService.findByExternalId(PAYMENT_INSTRUMENT_EXTERNAL_ID)).thenReturn(paymentInstrumentEntity);
        when(paymentProviders.byName(ADYEN)).thenReturn(paymentProvider);
        doThrow(new GatewayException.GatewayErrorException("Non-success HTTP status code 500 from gateway", "{}", 500))
                .when(paymentProvider).deleteStoredPaymentDetails(any(DeleteStoredPaymentDetailsGatewayRequest.class));

        assertThrows(GatewayException.GatewayErrorException.class, () ->
                taskHandler.process(AGREEMENT_EXTERNAL_ID, PAYMENT_INSTRUMENT_EXTERNAL_ID));
    }

    private static PaymentInstrumentEntity createPaymentInstrument() {
        return aPaymentInstrumentEntity()
                .withExternalId(PAYMENT_INSTRUMENT_EXTERNAL_ID)
                .withRecurringAuthToken(Map.of("storedPaymentMethodId", "stored-payment-method-id-123"))
                .build();
    }

    private static AgreementEntity createAgreement() {
        GatewayAccountCredentialsEntity credentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(ADYEN.getName())
                .withCredentials(Map.of(
                        "legal_entity_id", "a-legal-entity-id",
                        "store_id", "a-store-id",
                        "account_holder_id", "an-account-holder-id",
                        "balance_account_id", "a-balance-account-id"))
                .build();

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(ADYEN.getName())
                .withType(TEST)
                .withGatewayAccountCredentials(List.of(credentialsEntity))
                .build();

        return anAgreementEntity()
                .withExternalId(AGREEMENT_EXTERNAL_ID)
                .withGatewayAccount(gatewayAccountEntity)
                .withLive(false)
                .build();
    }
}
