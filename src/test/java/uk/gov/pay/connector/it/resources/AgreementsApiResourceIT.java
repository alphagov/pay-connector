package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.pay.connector.util.AddPaymentInstrumentParams;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Map;

import static java.lang.String.format;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static uk.gov.pay.connector.matcher.ZoneDateTimeAsStringWithinMatcher.isWithin;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;

public class AgreementsApiResourceIT {
    public static final String VALID_SERVICE_ID = "a-valid-service-id";
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    public static GatewayAccountResourceITBaseExtensions testBaseExtension = new GatewayAccountResourceITBaseExtensions("stripe", app.getLocalPort());

    private static final String REFERENCE_ID = "1234";
    private static final String DESCRIPTION = "a valid description";
    private static final String USER_IDENTIFIER = "a-valid-user-identifier";
    private static final String REFERENCE_ID_TOO_LONG = new String(new char[260]).replace('\0', '1');
    private static final String REFERENCE_ID_EMPTY = "";
    private static final String CREATE_AGREEMENT_URL = "/v1/api/accounts/%s/agreements";
    private static final String CREATE_AGREEMENT_BY_SERVICE_ID_URL = "/v1/api/service/%s/%s/agreements";
    private static final String CANCEL_AGREEMENT_URL = "/v1/api/accounts/%s/agreements/%s/cancel";
    private DatabaseFixtures.TestAccount testAccount;
    private Long accountId;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    class ByGatewayAccountId {
        @BeforeEach
        void setUp() {
            testAccount = createTestAccount("worldpay", true);
            accountId = testAccount.getAccountId();
        }

        @Test
        void shouldCreateAgreement() throws JsonProcessingException {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "reference", REFERENCE_ID,
                    "description", DESCRIPTION,
                    "user_identifier", USER_IDENTIFIER
            ));

            ValidatableResponse o = app.givenSetup()
                    .body(payload)
                    .post(format(CREATE_AGREEMENT_URL, accountId))
                    .then()
                    .statusCode(SC_CREATED)
                    .body("reference", equalTo(REFERENCE_ID))
                    .body("service_id", equalTo("valid-external-service-id"))
                    .body("agreement_id", notNullValue())
                    .body("description", equalTo(DESCRIPTION))
                    .body("user_identifier", equalTo(USER_IDENTIFIER))
                    .body("created_date", notNullValue())
                    .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                    .body("created_date", isWithin(10, SECONDS));
        }

        @Test
        void shouldReturn422WhenReferenceIdTooLong() throws JsonProcessingException {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "reference", REFERENCE_ID_TOO_LONG,
                    "description", DESCRIPTION
            ));

            app.givenSetup()
                    .body(payload)
                    .post(format(CREATE_AGREEMENT_URL, accountId))
                    .then()
                    .statusCode(SC_UNPROCESSABLE_ENTITY);
        }

        @Test
        void shouldReturn422WhenReferenceIdEmpty() throws JsonProcessingException {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "reference", REFERENCE_ID_EMPTY,
                    "description", DESCRIPTION
            ));

            app.givenSetup()
                    .body(payload)
                    .post(format(CREATE_AGREEMENT_URL, accountId))
                    .then()
                    .statusCode(SC_UNPROCESSABLE_ENTITY);
        }

        @Test
        void shouldReturn422WhenDescriptionEmpty() throws JsonProcessingException {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "reference", REFERENCE_ID
            ));

            app.givenSetup()
                    .body(payload)
                    .post(format(CREATE_AGREEMENT_URL, accountId))
                    .then()
                    .statusCode(SC_UNPROCESSABLE_ENTITY);
        }

        @Test
        void shouldReturn422WhenRecurringDisabled() throws JsonProcessingException {
            testAccount = createTestAccount("worldpay", false);
            accountId = testAccount.getAccountId();
            String payload = objectMapper.writeValueAsString(Map.of(
                    "reference", REFERENCE_ID,
                    "description", DESCRIPTION,
                    "user_identifier", USER_IDENTIFIER
            ));
            app.givenSetup()
                    .body(payload)
                    .post(format(CREATE_AGREEMENT_URL, accountId))
                    .then()
                    .statusCode(SC_UNPROCESSABLE_ENTITY)
                    .body("message", contains("Recurring payment agreements are not enabled on this account"))
                    .body("error_identifier", is(ErrorIdentifier.RECURRING_CARD_PAYMENTS_NOT_ALLOWED.toString()));
        }

        @Test
        void shouldReturn204AndCancelAgreement() {
            var agreementId = "an-external-id";
            AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                    .withPaymentInstrumentId(nextLong())
                    .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE)
                    .build();
            app.getDatabaseTestHelper().addPaymentInstrument(paymentInstrumentParams);
            AddAgreementParams agreementParams = anAddAgreementParams()
                    .withGatewayAccountId(String.valueOf(accountId))
                    .withExternalAgreementId(agreementId)
                    .withPaymentInstrumentId(paymentInstrumentParams.getPaymentInstrumentId())
                    .build();
            app.getDatabaseTestHelper().addAgreement(agreementParams);

            app.givenSetup()
                    .post(format(CANCEL_AGREEMENT_URL, accountId, agreementId))
                    .then()
                    .statusCode(NO_CONTENT_204);

            var paymentInstrumentMap = app.getDatabaseTestHelper().getPaymentInstrument(paymentInstrumentParams.getPaymentInstrumentId());
            assertThat(paymentInstrumentMap.get("status"), is("CANCELLED"));
            var agreementMap = app.getDatabaseTestHelper().getAgreementByExternalId(agreementId);
            assertThat(agreementMap.get("cancelled_date"), is(notNullValue()));
        }

        private DatabaseFixtures.TestAccount createTestAccount(String paymentProvider, boolean recurringEnabled) {
            long accountId = nextLong(2, 10000);

            return app.getDatabaseFixtures().aTestAccount().withPaymentProvider(paymentProvider)
                    .withIntegrationVersion3ds(2)
                    .withAccountId(accountId)
                    .withRecurringEnabled(recurringEnabled)
                    .insert();
        }
    }

    @Nested
    class ByServiceIdAndAccountType {
        @Test
        void shouldCreateAgreement_forValidRequest() throws JsonProcessingException {
            String gatewayAccountId = testBaseExtension.createAGatewayAccountWithServiceId(VALID_SERVICE_ID);
            testBaseExtension.updateGatewayAccount(gatewayAccountId, "recurring_enabled", true);

            String createAgreementPayload = objectMapper.writeValueAsString(Map.of(
                    "reference", REFERENCE_ID,
                    "description", DESCRIPTION,
                    "user_identifier", USER_IDENTIFIER
            ));

            String agreementId = app.givenSetup()
                    .body(createAgreementPayload)
                    .post(format(CREATE_AGREEMENT_BY_SERVICE_ID_URL, VALID_SERVICE_ID, "test"))
                    .then()
                    .statusCode(SC_CREATED)
                    .body("reference", equalTo(REFERENCE_ID))
                    .body("service_id", equalTo(VALID_SERVICE_ID))
                    .body("agreement_id", notNullValue())
                    .body("description", equalTo(DESCRIPTION))
                    .body("user_identifier", equalTo(USER_IDENTIFIER))
                    .body("created_date", notNullValue())
                    .body("created_date", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(.\\d{1,3})?Z"))
                    .body("created_date", isWithin(10, SECONDS))
                    .extract().path("agreement_id");
            
            Map<String, Object> agreement = app.getDatabaseTestHelper().getAgreementByExternalId(agreementId);
            assertThat(agreement.get("reference"), is(REFERENCE_ID));
            assertThat(agreement.get("service_id"), is(VALID_SERVICE_ID));
            assertThat(agreement.get("description"), is(DESCRIPTION));
        }


        @Test
        void shouldReturn422WhenReferenceIdTooLong() throws JsonProcessingException {
            testBaseExtension.createAGatewayAccountWithServiceId(VALID_SERVICE_ID);

            String payload = objectMapper.writeValueAsString(Map.of(
                    "reference", REFERENCE_ID_TOO_LONG,
                    "description", DESCRIPTION
            ));

            app.givenSetup()
                    .body(payload)
                    .post(format(CREATE_AGREEMENT_BY_SERVICE_ID_URL, VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(SC_UNPROCESSABLE_ENTITY)
                    .body("message[0]", is("Field [reference] can have a size between 1 and 255"));
        }

        @Test
        void shouldReturn422WhenReferenceIdEmpty() throws JsonProcessingException {
            testBaseExtension.createAGatewayAccountWithServiceId(VALID_SERVICE_ID);

            String payload = objectMapper.writeValueAsString(Map.of(
                    "reference", REFERENCE_ID_EMPTY,
                    "description", DESCRIPTION
            ));

            app.givenSetup()
                    .body(payload)
                    .post(format(CREATE_AGREEMENT_BY_SERVICE_ID_URL, VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(SC_UNPROCESSABLE_ENTITY)
                    .body("message[0]", is("Field [reference] can have a size between 1 and 255"));
        }

        @Test
        void shouldReturn422WhenReferenceIdNotProvided() throws JsonProcessingException {
            testBaseExtension.createAGatewayAccountWithServiceId(VALID_SERVICE_ID);

            String payload = objectMapper.writeValueAsString(Map.of(
                    "description", DESCRIPTION
            ));

            app.givenSetup()
                    .body(payload)
                    .post(format(CREATE_AGREEMENT_BY_SERVICE_ID_URL, VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(SC_UNPROCESSABLE_ENTITY)
                    .body("message[0]", is("Field [reference] cannot be null"));

        }

        @Test
        void shouldReturn422WhenDescriptionNotProvided() throws JsonProcessingException {
            testBaseExtension.createAGatewayAccountWithServiceId(VALID_SERVICE_ID);

            String payload = objectMapper.writeValueAsString(Map.of(
                    "reference", REFERENCE_ID
            ));

            app.givenSetup()
                    .body(payload)
                    .post(format(CREATE_AGREEMENT_BY_SERVICE_ID_URL, VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(SC_UNPROCESSABLE_ENTITY)
                    .body("message[0]", is("Field [description] cannot be null"));


        }

        @Test
        void shouldReturn422WhenDescriptionEmpty() throws JsonProcessingException {
            testBaseExtension.createAGatewayAccountWithServiceId(VALID_SERVICE_ID);

            String payload = objectMapper.writeValueAsString(Map.of(
                    "reference", REFERENCE_ID,
                    "description", ""
            ));

            app.givenSetup()
                    .body(payload)
                    .post(format(CREATE_AGREEMENT_BY_SERVICE_ID_URL, VALID_SERVICE_ID, GatewayAccountType.TEST))
                    .then()
                    .statusCode(SC_UNPROCESSABLE_ENTITY)
                    .body("message[0]", is("Field [description] can have a size between 1 and 255"));
        }

        @Test
        void shouldReturn422WhenRecurringPaymentsDisabled() throws JsonProcessingException {
            testBaseExtension.createAGatewayAccountWithServiceId(VALID_SERVICE_ID);
            
            String payload = objectMapper.writeValueAsString(Map.of(
                    "reference", REFERENCE_ID,
                    "description", DESCRIPTION,
                    "user_identifier", USER_IDENTIFIER
            ));
                app.givenSetup()
                        .body(payload)
                        .post(format(CREATE_AGREEMENT_BY_SERVICE_ID_URL, VALID_SERVICE_ID, GatewayAccountType.TEST))
                        .then()
                        .statusCode(SC_UNPROCESSABLE_ENTITY)
                        .body("message", contains("Recurring payment agreements are not enabled on this account"))
                        .body("error_identifier", is(ErrorIdentifier.RECURRING_CARD_PAYMENTS_NOT_ALLOWED.toString()));
        }
    }
}
