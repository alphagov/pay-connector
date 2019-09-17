package uk.gov.pay.connector.charge.service;

import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.charge.exception.Worldpay3dsFlexJwtCredentialsException;
import uk.gov.pay.connector.charge.exception.Worldpay3dsFlexJwtPaymentProviderException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.util.JwtGenerator;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.inject.Inject;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;

public class Worldpay3dsFlexJwtService {

    private final JwtGenerator jwtGenerator;
    private final int tokenExpiryDurationSeconds;
    private final LinksConfig linksConfig;

    @Inject
    public Worldpay3dsFlexJwtService(JwtGenerator jwtGenerator,
                                     ConnectorConfiguration config) {
        this.jwtGenerator = jwtGenerator;
        this.tokenExpiryDurationSeconds = config.getChargeSweepConfig().getDefaultChargeExpiryThreshold();
        this.linksConfig = config.getLinks();
    }

    /**
     * Utility method to create a JWT for Worldpay 3DS Flex DDC based upon the required claims
     * shown in their documentation.
     *
     * @see <a href="https://beta.developer.worldpay.com/docs/wpg/directintegration/3ds2#device-data-collection-ddc-"
     * >Worldpay DDC Documentation</a>
     */
    public String generateDdcToken(GatewayAccount gatewayAccount, ZonedDateTime chargeCreatedTime) {
        validateGatewayIsWorldpay(gatewayAccount);

        var claims = generateDdcClaims(gatewayAccount, chargeCreatedTime);
        return createJwt(gatewayAccount, claims);
    }

    public Optional<String> generateChallengeTokenIfAppropriate(ChargeEntity chargeEntity) {
        if (shouldGenerateChallengeToken(chargeEntity)) {
            return Optional.of(generateChallengeToken(chargeEntity));
        }
        return Optional.empty();
    }
    
    private boolean shouldGenerateChallengeToken(ChargeEntity chargeEntity) {
        return chargeEntity.getStatus().equals(ChargeStatus.AUTHORISATION_3DS_REQUIRED.toString()) &&
                chargeEntity.get3dsDetails() != null &&
                chargeEntity.get3dsDetails().getWorldpayChallengeAcsUrl() != null &&
                chargeEntity.get3dsDetails().getWorldpayChallengePayload() != null &&
                chargeEntity.get3dsDetails().getWorldpayChallengeTransactionId() != null;
    }

    private String generateChallengeToken(ChargeEntity chargeEntity) {
        GatewayAccount gatewayAccount = GatewayAccount.valueOf(chargeEntity.getGatewayAccount());
        validateGatewayIsWorldpay(gatewayAccount);

        var claims = generateChallengeClaims(chargeEntity, gatewayAccount);
        return createJwt(gatewayAccount, claims);
    }

    private void validateGatewayIsWorldpay(GatewayAccount gatewayAccount) {
        if (!gatewayAccount.getGatewayName().equals(PaymentGatewayName.WORLDPAY.getName())) {
            throw new Worldpay3dsFlexJwtPaymentProviderException(gatewayAccount.getId());
        }
    }

    private String getCredential(GatewayAccount gatewayAccount, String issuer) {
        return Optional.ofNullable(gatewayAccount.getCredentials().get(issuer))
                .orElseThrow(() -> new Worldpay3dsFlexJwtCredentialsException(gatewayAccount.getId(), issuer));
    }

    private Map<String, Object> generateDdcClaims(GatewayAccount gatewayAccount, ZonedDateTime chargeCreatedTime) {
        Map<String, Object> commonClaims = generateCommonClaims(gatewayAccount);
        Map<String, Object> claims = new HashMap<>(commonClaims);
        claims.put("exp", chargeCreatedTime.plusSeconds(tokenExpiryDurationSeconds).toInstant().getEpochSecond());
        return claims;
    }

    private Map<String, Object> generateChallengeClaims(ChargeEntity chargeEntity, GatewayAccount gatewayAccount) {
        Map<String, Object> commonClaims = generateCommonClaims(gatewayAccount);
        Map<String, Object> claims = new HashMap<>(commonClaims);
        claims.put("ReturnUrl", format("%s/card_details/%s/3ds_required_in", linksConfig.getFrontendUrl(), chargeEntity.getExternalId()));
        claims.put("ObjectifyPayload", true);
        claims.put("Payload", Map.of(
                "ACSUrl", chargeEntity.get3dsDetails().getWorldpayChallengeAcsUrl(),
                "Payload", chargeEntity.get3dsDetails().getWorldpayChallengePayload(),
                "TransactionId", chargeEntity.get3dsDetails().getWorldpayChallengeTransactionId()));
        return claims;
    }

    private Map<String, Object> generateCommonClaims(GatewayAccount gatewayAccount) {
        String issuer = getCredential(gatewayAccount, "issuer");
        String organisationId = getCredential(gatewayAccount, "organisational_unit_id");

        return Map.of(
                "jti", RandomIdGenerator.newId(),
                "iat", Instant.now().getEpochSecond(),
                "iss", issuer,
                "OrgUnitId", organisationId);
    }

    private String createJwt(GatewayAccount gatewayAccount, Map<String, Object> claims) {
        String jwtMacId = getCredential(gatewayAccount, "jwt_mac_id");
        return jwtGenerator.createJwt(claims, jwtMacId);
    }
}
