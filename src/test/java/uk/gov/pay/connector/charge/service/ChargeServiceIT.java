package uk.gov.pay.connector.charge.service;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.exception.IdempotencyKeyUsedException;
import uk.gov.pay.connector.charge.model.ChargeCreateRequest;
import uk.gov.pay.connector.charge.model.ChargeCreateRequestBuilder;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import javax.ws.rs.core.UriInfo;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

@RunWith(MockitoJUnitRunner.class)
public class ChargeServiceIT {

    @ClassRule
    public static DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Mock
    private UriInfo mockUriInfo;

    private ChargeService chargeService;

    private DatabaseTestHelper databaseTestHelper;

    private final long GATEWAY_ACCOUNT_ID = 123L;

    @Before
    public void setUp() throws Exception {
        chargeService = app.getInstanceFromGuiceContainer(ChargeService.class);
        databaseTestHelper = app.getDatabaseTestHelper();

        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(String.valueOf(GATEWAY_ACCOUNT_ID))
                .withRecurringEnabled(true)
                .build());
    }

    @Test
    public void shouldThrowIdempotencyKeyUsedExceptionWhenConflictingDatabaseEntryExists() {
        String idempotencyKey = "an-idempotency-key";
        String agreementExternalId = "an-agreement-id";
        databaseTestHelper.insertIdempotency(idempotencyKey, GATEWAY_ACCOUNT_ID, "a-charge-id", Map.of("amount", 1L));
        DatabaseFixtures.TestPaymentInstrument paymentInstrument = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestPaymentInstrument()
                .withStatus(PaymentInstrumentStatus.ACTIVE)
                .insert();
        DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestAgreement()
                .withExternalId(agreementExternalId)
                .withGatewayAccountId(GATEWAY_ACCOUNT_ID)
                .withPaymentInstrumentId(paymentInstrument.getPaymentInstrumentId())
                .insert();

        ChargeCreateRequest chargeCreateRequest = ChargeCreateRequestBuilder.aChargeCreateRequest()
                .withAmount(1000L)
                .withReference("ref")
                .withDescription("descr")
                .withAgreementId(agreementExternalId)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .build();
        assertThrows(IdempotencyKeyUsedException.class, () -> chargeService.create(chargeCreateRequest, GATEWAY_ACCOUNT_ID, mockUriInfo, idempotencyKey));
    }
}
