package uk.gov.pay.connector.it.util;

import org.hamcrest.core.Is;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.pay.connector.util.AddPaymentInstrumentParams;

import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static org.apache.http.HttpStatus.SC_CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_USER_NOT_PRESENT_QUEUED;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory.STORED_PAYMENT_METHOD_ID;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AGREEMENT_PAYMENT_TYPE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AMOUNT_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_AUTH_MODE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_DESCRIPTION_VALUE;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_KEY;
import static uk.gov.pay.connector.it.base.ITestBaseExtension.JSON_REFERENCE_VALUE;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;
import static uk.gov.service.payments.commons.model.AgreementPaymentType.UNSCHEDULED;

public class RecurringPaymentSetupUtil {

    public static final String JSON_AGREEMENT_ID_KEY = "agreement_id";
    public static final String JSON_VALID_AGREEMENT_ID_VALUE = "12345678901234567890123456";
    public static final String JSON_AUTH_MODE_AGREEMENT = "agreement";
    public static final String JSON_AGREEMENT_PAYMENT_TYPE = UNSCHEDULED.getName();
    public static final String CHARGE_ID = String.valueOf(secureRandomLong());

    public static String setupChargeWithAgreementAndPaymentInstrument(ITestBaseExtension testBaseExtension, AppWithPostgresAndSqsExtension app, String storedPaymentMethodId) {
        Long paymentInstrumentId = secureRandomLong();

        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE)
                .withRecurringAuthToken(Map.of(
                        STORED_PAYMENT_METHOD_ID, storedPaymentMethodId))
                .withChargeExternalId(CHARGE_ID)
                .build();
        app.getDatabaseTestHelper().addPaymentInstrument(paymentInstrumentParams);

        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(testBaseExtension.getAccountId())
                .withExternalAgreementId(JSON_VALID_AGREEMENT_ID_VALUE)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build();
        app.getDatabaseTestHelper().addAgreement(agreementParams);

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_AGREEMENT_ID_KEY, JSON_VALID_AGREEMENT_ID_VALUE,
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_AGREEMENT,
                JSON_AGREEMENT_PAYMENT_TYPE_KEY, JSON_AGREEMENT_PAYMENT_TYPE

        ));

        String chargeId = testBaseExtension.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(SC_CREATED)
                .body(JSON_AGREEMENT_ID_KEY, Is.is(JSON_VALID_AGREEMENT_ID_VALUE))
                .contentType(JSON)
                .extract().path("charge_id");

        testBaseExtension.assertFrontendChargeStatusIs(chargeId, AUTHORISATION_USER_NOT_PRESENT_QUEUED.getValue());
        testBaseExtension.assertApiStateIs(chargeId, EXTERNAL_STARTED.getStatus());
        return chargeId;
    }
}
