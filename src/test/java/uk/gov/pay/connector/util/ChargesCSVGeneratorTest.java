package uk.gov.pay.connector.util;

import org.junit.Test;
import uk.gov.pay.connector.fixture.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.HashMap;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCELLED;

public class ChargesCSVGeneratorTest {

    @Test
    public void shouldGenerateCsvOnlyWithHeadersWhenListOfChargesIsEmpty() throws Exception {

        String generatedCsv = ChargesCSVGenerator.generate(newArrayList());
        String expectedOutput = "Service Payment Reference,Amount,State,Finished,Error Message,Error Code,Gateway Transaction ID,GOV.UK Pay ID,Date Created\n";

        assertThat(generatedCsv, is(expectedOutput));
    }

    @Test
    public void shouldGenerateCsvForACharge() throws Exception {

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("Provider", new HashMap<String, String>() {{
            put("username", "bla");
            put("password", "meh");
        }});
        gatewayAccount.setId(4000L);

        ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity()
                .withId(1001L)
                .withAmount(14000L)
                .withTransactionId("222")
                .withReference("reference")
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        String externalId = charge.getExternalId();

        String generatedCsv = ChargesCSVGenerator.generate(newArrayList(charge));

        String expectedDate = DateTimeUtils.toLondonZone(charge.getCreatedDate());
        String expectedOutput = "Service Payment Reference,Amount,State,Finished,Error Message,Error Code,Gateway Transaction ID,GOV.UK Pay ID,Date Created\n" +
                "reference,140.00,created,false,,,222," + externalId + "," + expectedDate + "\n";

        assertThat(generatedCsv, is(expectedOutput));
    }

    @Test
    public void shouldGenerateCsvForMultipleCharges() throws Exception {

        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity("Provider", null);
        gatewayAccount.setId(300L);

        ChargeEntity charge1 = ChargeEntityFixture.aValidChargeEntity()
                .withId(100L)
                .withAmount(400L)
                .withTransactionId("200")
                .withReference("ref")
                .withGatewayAccountEntity(gatewayAccount)
                .build();

        String externalId1 = charge1.getExternalId();
        String expectedDateCharge1 = DateTimeUtils.toLondonZone(charge1.getCreatedDate());

        GatewayAccountEntity gatewayAccount2 = new GatewayAccountEntity("SmartPay", null);
        gatewayAccount.setId(600L);

        ChargeEntity charge2 = ChargeEntityFixture.aValidChargeEntity()
                .withId(101L)
                .withAmount(200L)
                .withStatus(CAPTURE_READY)
                .withReference("ref-2")
                .withGatewayAccountEntity(gatewayAccount2)
                .build();

        String externalId2 = charge2.getExternalId();
        String expectedDateCharge2 = DateTimeUtils.toLondonZone(charge2.getCreatedDate());

        ChargeEntity charge3 = ChargeEntityFixture.aValidChargeEntity()
                .withId(101L)
                .withAmount(300L)
                .withStatus(USER_CANCELLED)
                .withReference("ref-7")
                .withGatewayAccountEntity(gatewayAccount2)
                .build();

        String externalId3 = charge3.getExternalId();
        String expectedDateCharge3 = DateTimeUtils.toLondonZone(charge3.getCreatedDate());

        String generate = ChargesCSVGenerator.generate(newArrayList(charge1, charge2, charge3));
        String expectedOutput =
                "Service Payment Reference,Amount,State,Finished,Error Message,Error Code,Gateway Transaction ID,GOV.UK Pay ID,Date Created\n" +
                "ref,4.00,created,false,,,200," + externalId1 + "," + expectedDateCharge1 + "\n" +
                "ref-2,2.00,submitted,false,,,," + externalId2 + "," + expectedDateCharge2 + "\n" +
                "ref-7,3.00,failed,true,Payment was cancelled by the user,P0030,," + externalId3 + "," + expectedDateCharge3 + "\n";

        assertThat(generate, is(expectedOutput));
    }
}
