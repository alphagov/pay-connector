package uk.gov.pay.connector.charge.service;

import uk.gov.pay.connector.charge.exception.Worldpay3dsFlexDdcJwtCredentialsException;
import uk.gov.pay.connector.charge.exception.Worldpay3dsFlexDdcJwtPaymentProviderException;
import uk.gov.pay.connector.charge.util.JwtGenerator;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.inject.Inject;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.time.temporal.ChronoUnit.MINUTES;

public class Worldpay3dsFlexJwtService {

    private final JwtGenerator jwtGenerator;

    @Inject
    public Worldpay3dsFlexJwtService(JwtGenerator jwtGenerator) {
        this.jwtGenerator = jwtGenerator;
    }

    /**
     * Utility method to create a JWT for Worldpay 3DS Flex DDC based upon the required claims
     * shown in their documentation.
     *
     * @see <a href="https://beta.developer.worldpay.com/docs/wpg/directintegration/3ds2#device-data-collection-ddc-"
     * >Worldpay DDC Documentation</a>
     */
    public String generateDdcToken(GatewayAccount gatewayAccount) {
        if (!gatewayAccount.getGatewayName().equals(PaymentGatewayName.WORLDPAY.toString())) {
            throw new Worldpay3dsFlexDdcJwtPaymentProviderException(gatewayAccount.getId());
        }

        Map<String, String> credentials = gatewayAccount.getCredentials();
        String issuer = credentials.get("issuer");
        String organisationId = credentials.get("organisational_unit_id");
        String jwtMacId = credentials.get("jwt_mac_id");

        Set<String> missingCredentials = new HashSet<>();

        if (issuer == null) {
            missingCredentials.add("issuer");
        }

        if (organisationId == null) {
            missingCredentials.add("organisational_unit_id");
        }

        if (jwtMacId == null) {
            missingCredentials.add("jwt_mac_id");
        }

        if (missingCredentials.size() > 0) {
            throw new Worldpay3dsFlexDdcJwtCredentialsException(gatewayAccount.getId(), missingCredentials);
        }

        var claims = generateDdcClaims(issuer, organisationId);
        return jwtGenerator.createJwt(claims, jwtMacId);
    }

    private Map<String, Object> generateDdcClaims(String issuer, String organisationId) {
        return Map.of(
                "jti", RandomIdGenerator.newId(),
                "iat", Instant.now().toEpochMilli(),
                "exp", Instant.now().plus(90, MINUTES).toEpochMilli(),
                "iss", issuer,
                "OrgUnitId", organisationId);
    }
}
