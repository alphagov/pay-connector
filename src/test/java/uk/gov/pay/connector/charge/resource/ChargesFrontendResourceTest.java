package uk.gov.pay.connector.charge.resource;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.cardtype.dao.CardTypeDao;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.common.model.api.ExternalTransactionStateFactory;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

@ExtendWith(DropwizardExtensionsSupport.class)
class ChargesFrontendResourceTest {
    private static final ChargeService CHARGE_SERVICE = mock(ChargeService.class);
    private static final ChargeDao CHARGE_DAO = mock(ChargeDao.class);
    private static final CardTypeDao CARD_TYPE_DAO = mock(CardTypeDao.class);
    private static final Worldpay3dsFlexJwtService WORLDPAY_3DS_FLEX_JWT_SERVICE = mock(Worldpay3dsFlexJwtService.class);
    private static final AgreementService AGREEMENT_SERVICE = mock(AgreementService.class);
    private static final ExternalTransactionStateFactory EXTERNAL_TRANSACTION_STATE_FACTORY = mock(ExternalTransactionStateFactory.class);

    private static final ResourceExtension resources = ResourceTestRuleWithCustomExceptionMappersBuilder
            .getBuilder()
            .addResource(new ChargesFrontendResource(CHARGE_DAO, CHARGE_SERVICE, CARD_TYPE_DAO,
                    WORLDPAY_3DS_FLEX_JWT_SERVICE, AGREEMENT_SERVICE, EXTERNAL_TRANSACTION_STATE_FACTORY))
            .build();

    @Test
    void shouldReturn400_whenPutToChargeStatus_emptyPayload() {
        Response response = resources.client()
                .target("/v1/frontend/charges/irrelevant_charge_id/status")
                .request()
                .put(Entity.json(""));

        assertThat(response.getStatus(), is(422));

        List<String> listOfErrors = (List) response.readEntity(Map.class).get("message");
        assertThat(listOfErrors.size(), is(1));
        assertThat(listOfErrors, hasItem("must not be null"));
    }

    @Test
    void shouldReturn422_whenPutToChargeStatus_emptyNewStatus() {
        Response response = resources.client()
                .target("/v1/frontend/charges/irrelevant_charge_id/status")
                .request()
                .put(Entity.json(Collections.singletonMap("new_status", "")));

        assertThat(response.getStatus(), is(422));

        List<String> listOfErrors = (List) response.readEntity(Map.class).get("message");
        assertThat(listOfErrors.size(), is(2));
        assertThat(listOfErrors, hasItem("invalid new status"));
        assertThat(listOfErrors, hasItem("may not be empty"));

    }

    @Test
    void shouldReturn422_whenPutToChargeStatus_invalidNewStatus() {
        Response response = resources.client()
                .target("/v1/frontend/charges/irrelevant_charge_id/status")
                .request()
                .put(Entity.json(Collections.singletonMap("new_status", "invalid_status")));

        assertThat(response.getStatus(), is(422));

        List<String> listOfErrors = (List) response.readEntity(Map.class).get("message");
        assertThat(listOfErrors.size(), is(1));
        assertThat(listOfErrors, hasItem("invalid new status"));
    }
}
