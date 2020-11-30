package uk.gov.pay.connector.it.contract;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.util.JwtGenerator;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
public class Validate3dsFlexCredentialsTest {

    // ORGANISATIONAL_UNIT_ID, ISSUER and JWT_MAC_KEY can be found in the Worldpay MAI. Login, go to Integration -> 3DS Flex
    private final static String ORGANISATIONAL_UNIT_ID = "";
    private final static String ISSUER = "";
    private final static String JWT_MAC_KEY = "";
    private final static String DDC_URL = "https://centinelapi.cardinalcommerce.com/V1/Cruise/Collect";
    private final static String FEW_CARD_NUM_DIGITS = "424242";

    @Test
    public void should_process_server_side_ddc_change_with_correct_values() {
        assertEquals(200, invokeDDCRequest(ORGANISATIONAL_UNIT_ID, ISSUER, JWT_MAC_KEY));
    }
    
    @Test
    public void should_not_process_server_side_ddc_change_with_incorrect_org_unit_id() {
        assertEquals(400, invokeDDCRequest("xxxx", ISSUER, JWT_MAC_KEY));
    }
    
    @Test
    public void should_not_process_server_side_ddc_change_with_incorrect_issuer() {
        assertEquals(400, invokeDDCRequest(ORGANISATIONAL_UNIT_ID, "xxxx", JWT_MAC_KEY));
    }
    
    @Test
    public void should_not_process_server_side_ddc_change_with_incorrect_jwt_mac_key() {
        assertEquals(400, invokeDDCRequest(ORGANISATIONAL_UNIT_ID, ISSUER, "b41d22ba-260d-43f2-b771-db771a7d46ee"));
    }

    private int invokeDDCRequest(String organisationalUnitId, String issuer, String jwtMacKey){
        long expiryEpochSecond = ZonedDateTime.ofInstant(Instant.ofEpochSecond(1881821916), UTC).plusSeconds(5400).toInstant().getEpochSecond();

        Map<String, Object> claims = Map.of(
                "jti", RandomIdGenerator.newId(),
                "iat", Instant.now().getEpochSecond(),
                "iss", issuer,
                "OrgUnitId", organisationalUnitId,
                "exp", expiryEpochSecond);

        String jwt = new JwtGenerator().createJwt(claims, jwtMacKey);

        WebTarget webTarget = ClientBuilder.newClient().target(DDC_URL);
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
//        formData.add("Bin", FEW_CARD_NUM_DIGITS); // Bin is not needed
        formData.add("JWT", jwt);

        Response response = webTarget.request().post(Entity.form(formData));
        return response.getStatus();
    }

}
