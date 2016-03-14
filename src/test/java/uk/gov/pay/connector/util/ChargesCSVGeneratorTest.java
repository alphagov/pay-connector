package uk.gov.pay.connector.util;

import org.junit.Test;
import uk.gov.pay.connector.fixture.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.HashMap;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.READY_FOR_CAPTURE;

public class ChargesCSVGeneratorTest {

    @Test
    public void shouldGenerateCsvOnlyWithHeadersWhenListOfChargesIsEmpty() throws Exception {

        String generatedCsv = ChargesCSVGenerator.generate(newArrayList());
        String expectedOutput = "Service Payment Reference,Amount,Status,Gateway Transaction ID,GOV.UK Pay ID,Date Created\n";

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

        String generatedCsv = ChargesCSVGenerator.generate(newArrayList(charge));

        String expectedDate = DateTimeUtils.toUTCDateString(charge.getCreatedDate());
        String expectedOutput = "Service Payment Reference,Amount,Status,Gateway Transaction ID,GOV.UK Pay ID,Date Created\n" +
                "reference,140.00,CREATED,222,1001," + expectedDate + "\n";

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

        String expectedDateCharge1 = DateTimeUtils.toUTCDateString(charge1.getCreatedDate());

        GatewayAccountEntity gatewayAccount2 = new GatewayAccountEntity("SmartPay", null);
        gatewayAccount.setId(600L);

        ChargeEntity charge2 = ChargeEntityFixture.aValidChargeEntity()
                .withId(101L)
                .withAmount(200L)
                .withStatus(READY_FOR_CAPTURE)
                .withReference("ref-2")
                .withGatewayAccountEntity(gatewayAccount2)
                .build();

        String expectedDateCharge2 = DateTimeUtils.toUTCDateString(charge2.getCreatedDate());

        String generate = ChargesCSVGenerator.generate(newArrayList(charge1, charge2));
        String expectedOutput = "Service Payment Reference,Amount,Status,Gateway Transaction ID,GOV.UK Pay ID,Date Created\n" +
                "ref,4.00,CREATED,200,100," + expectedDateCharge1 + "\n" +
                "ref-2,2.00,IN PROGRESS,,101," + expectedDateCharge2 + "\n";

        assertThat(generate, is(expectedOutput));
    }
}