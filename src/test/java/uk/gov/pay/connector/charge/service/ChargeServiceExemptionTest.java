package uk.gov.pay.connector.charge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.dao.AgreementDao;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.charge.util.AuthCardDetailsToCardDetailsEntityConverter;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture;
import uk.gov.pay.connector.gatewayaccountcredentials.service.GatewayAccountCredentialsService;
import uk.gov.pay.connector.idempotency.dao.IdempotencyDao;
import uk.gov.pay.connector.paymentinstrument.service.PaymentInstrumentService;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.queue.tasks.TaskQueueService;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.token.dao.TokenDao;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import javax.ws.rs.core.UriInfo;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.paymentprocessor.model.Exemption3ds.EXEMPTION_NOT_REQUESTED;

@ExtendWith(MockitoExtension.class)
class ChargeServiceExemptionTest {

    private static final String SERVICE_HOST = "http://my-service";
    private static final long GATEWAY_ACCOUNT_ID = 10L;
    private static ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TokenDao mockTokenDao;

    @Mock
    private ChargeDao mockChargeDao;

    @Mock
    private ChargeEventDao mockChargeEventDao;

    @Mock
    private LedgerService mockLedgerService;

    @Mock
    private GatewayAccountDao mockGatewayAccountDao;

    @Mock
    private CardTypeDao mockCardTypeDao;

    @Mock
    private AgreementDao mockAgreementDao;

    @Mock
    private ConnectorConfiguration mockConfig;

    @Mock
    private UriInfo mockUriInfo;

    @Mock
    private LinksConfig mockLinksConfig;

    @Mock
    private PaymentProviders mockProviders;

    @Mock
    private PaymentProvider mockPaymentProvider;

    @Mock
    private EventService mockEventService;

    @Mock
    private PaymentInstrumentService mockPaymentInstrumentService;

    @Mock
    private StateTransitionService mockStateTransitionService;

    @Mock
    private RefundService mockRefundService;

    @Mock
    private GatewayAccountCredentialsService mockGatewayAccountCredentialsService;

    @Mock
    private AuthCardDetailsToCardDetailsEntityConverter mockAuthCardDetailsToCardDetailsEntityConverter;

    @Mock
    private TaskQueueService mockTaskQueueService;

    @Mock
    private Worldpay3dsFlexJwtService mockWorldpay3dsFlexJwtService;
    
    @Mock
    private IdempotencyDao mockIdempotencyDao;

    @Mock
    private ExternalTransactionStateFactory mockExternalTransactionStateFactory;

    private final InstantSource fixedInstantSource = InstantSource.fixed(Instant.parse("2024-11-11T10:07:00Z"));

    private ChargeService chargeService;
    private GatewayAccountEntity gatewayAccount;
    private final Long chargeId = 101L;
    private ChargeEntity newCharge;
    private String externalId;

    @BeforeEach
    void setUp() {
        gatewayAccount = new GatewayAccountEntity(TEST);
        gatewayAccount.setId(GATEWAY_ACCOUNT_ID);

        var gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                .aGatewayAccountCredentialsEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider("sandbox")
                .withCredentials(Map.of())
                .withState(GatewayAccountCredentialState.ACTIVE)
                .build();

        List<GatewayAccountCredentialsEntity> gatewayAccountCredentialsEntities = new ArrayList<>();
        gatewayAccountCredentialsEntities.add(gatewayAccountCredentialsEntity);
        gatewayAccount.setGatewayAccountCredentials(gatewayAccountCredentialsEntities);

        newCharge = aValidChargeEntity()
                .withId(chargeId)
                .withGatewayAccountEntity(gatewayAccount)
                .withStatus(AUTHORISATION_SUCCESS)
                .withAuthorisationMode(AuthorisationMode.WEB)
                .build();
        externalId = newCharge.getExternalId();

        when(mockConfig.getLinks()).thenReturn(mockLinksConfig);
        doAnswer(invocation -> fromUri(SERVICE_HOST)).when(this.mockUriInfo).getBaseUriBuilder();
        when(mockProviders.byName(any(PaymentGatewayName.class))).thenReturn(mockPaymentProvider);
        when(mockLinksConfig.getFrontendUrl()).thenReturn("http://frontend.test");
        when(mockPaymentProvider.getExternalChargeRefundAvailability(any(Charge.class), anyList())).thenReturn(EXTERNAL_AVAILABLE);
        when(mockChargeDao.findByExternalIdAndGatewayAccount(externalId, GATEWAY_ACCOUNT_ID)).thenReturn(Optional.of(newCharge));
        when(mockExternalTransactionStateFactory.newExternalTransactionState(newCharge))
                .thenReturn(new ExternalTransactionState(EXTERNAL_SUBMITTED.getStatus(), false));

        chargeService = new ChargeService(mockTokenDao, mockChargeDao, mockChargeEventDao,
                mockCardTypeDao, mockAgreementDao, mockGatewayAccountDao, mockConfig, mockProviders,
                mockStateTransitionService, mockLedgerService, mockRefundService, mockEventService, mockPaymentInstrumentService,
                mockGatewayAccountCredentialsService, mockAuthCardDetailsToCardDetailsEntityConverter, mockTaskQueueService,
                mockWorldpay3dsFlexJwtService, mockIdempotencyDao, mockExternalTransactionStateFactory, objectMapper,
                null, fixedInstantSource);
    }

    @Test
    void shouldFindChargeWithOutExemptionObject_whenNoExemption3dsInformationAvailable() {
        ChargeResponse response = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockUriInfo).get();

        assertThat(response.getExemption(), is(nullValue()));
    }

    @Test
    void shouldFindChargeWithExemptionRequestedFalse_whenExemption3dsNotRequested() {
        newCharge.setExemption3ds(EXEMPTION_NOT_REQUESTED);
        newCharge.setExemption3dsRequested(null);

        ChargeResponse response = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockUriInfo).get();

        assertThat(response.getExemption(), is(notNullValue()));
        assertThat(response.getExemption().isRequested(), is(false));
        assertThat(response.getExemption().getType(), is(nullValue()));
        assertThat(response.getExemption().getOutcome(), is(nullValue()));
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, nullValues = "null", textBlock = """
        exemption3DsRequested, typeInExemptionResponseObject
        OPTIMISED, null
        CORPORATE, corporate
    """)
    void shouldFindChargeWithExemptionTrue_whenExemption3dsWasRequestedButNoResponseYet(Exemption3dsType exemption3DsRequested,
                                                                                     String typeInExemptionResponseObject) {
        newCharge.setExemption3ds(null);
        newCharge.setExemption3dsRequested(exemption3DsRequested);

        ChargeResponse response = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockUriInfo).get();

        assertThat(response.getExemption(), is(notNullValue()));
        assertThat(response.getExemption().isRequested(), is(true));
        assertThat(response.getExemption().getType(), is(typeInExemptionResponseObject));
        assertThat(response.getExemption().getOutcome(), is(nullValue()));
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, nullValues = "null", textBlock = """
        exemption3DsRequested, exemption3dsOutcome, typeInExemptionResponseObject
        OPTIMISED, EXEMPTION_HONOURED, null
        OPTIMISED, EXEMPTION_REJECTED, null
        OPTIMISED, EXEMPTION_OUT_OF_SCOPE, null
        CORPORATE, EXEMPTION_HONOURED, corporate
        CORPORATE, EXEMPTION_REJECTED, corporate
        CORPORATE, EXEMPTION_OUT_OF_SCOPE, corporate
    """)
    void shouldFindChargeWithExemption_whenOutcomeAvailable(Exemption3dsType exemption3DsRequested, Exemption3ds exemption3dsOutcome,
                                                            String typeInExemptionResponseObject) {
        newCharge.setExemption3ds(exemption3dsOutcome);
        newCharge.setExemption3dsRequested(exemption3DsRequested);

        ChargeResponse response = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockUriInfo).get();

        assertThat(response.getExemption(), is(notNullValue()));
        assertThat(response.getExemption().isRequested(), is(true));
        assertThat(response.getExemption().getType(), is(typeInExemptionResponseObject));
        assertThat(response.getExemption().getOutcome(), is(notNullValue()));
        assertThat(response.getExemption().getOutcome().getResult(), is(exemption3dsOutcome));
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, nullValues = "null", textBlock = """
        exemption3dsOutcome
        EXEMPTION_HONOURED
        EXEMPTION_REJECTED
        EXEMPTION_OUT_OF_SCOPE
    """)
    void shouldFindChargeWithExemption_fromBeforeWeRecordedExemption3dsType(Exemption3ds exemption3dsOutcome) {
        newCharge.setExemption3ds(exemption3dsOutcome);
        newCharge.setExemption3dsRequested(null);

        ChargeResponse response = chargeService.findChargeForAccount(externalId, GATEWAY_ACCOUNT_ID, mockUriInfo).get();

        assertThat(response.getExemption(), is(notNullValue()));
        assertThat(response.getExemption().isRequested(), is(true));
        assertThat(response.getExemption().getType(), is(nullValue()));
        assertThat(response.getExemption().getOutcome(), is(notNullValue()));
        assertThat(response.getExemption().getOutcome().getResult(), is(exemption3dsOutcome));
    }
}
