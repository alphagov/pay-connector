package uk.gov.pay.connector.util;

import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.HashMap;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

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

        ChargeEntity charge = new ChargeEntity(1001L, 14000L, "CREATED", "222", "http://return.url.com", "A description", "reference", gatewayAccount);

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
        ChargeEntity charge1 = new ChargeEntity(null, 400L, "CREATED", "200", null, "A description", "ref", gatewayAccount);
        String expectedDateCharge1 = DateTimeUtils.toUTCDateString(charge1.getCreatedDate());
        charge1.setId(100L);


        GatewayAccountEntity gatewayAccount2 = new GatewayAccountEntity("SmartPay", null);
        gatewayAccount.setId(600L);
        ChargeEntity charge2 = new ChargeEntity(101L, 200L, "READY_FOR_CAPTURE", null, null, "Another description", "ref-2", gatewayAccount2);
        String expectedDateCharge2 = DateTimeUtils.toUTCDateString(charge2.getCreatedDate());

        String generate = ChargesCSVGenerator.generate(newArrayList(charge1, charge2));
        String expectedOutput = "Service Payment Reference,Amount,Status,Gateway Transaction ID,GOV.UK Pay ID,Date Created\n" +
                "ref,4.00,CREATED,200,100," + expectedDateCharge1 + "\n" +
                "ref-2,2.00,IN PROGRESS,,101," + expectedDateCharge2 + "\n";

        assertThat(generate, is(expectedOutput));
    }
}