package uk.gov.pay.connector.gatewayaccount.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.gatewayaccount.validation.ValidWorldpay3dsFlexIssuerOrOrganisationalUnitId;
import uk.gov.pay.connector.gatewayaccount.validation.ValidWorldpay3dsFlexJwtMacKey;

public class Worldpay3dsFlexCredentialsRequest {

    @JsonProperty("issuer")
    @ValidWorldpay3dsFlexIssuerOrOrganisationalUnitId(message = "Field [issuer] must be 24 lower-case hexadecimal characters")
    @Schema(description = "Lower-case hexadecimal characters. Should only contain characters [0-9a-f]", minLength = 24, maxLength = 24,
            example = "53f0917f101a4428b69d5fb0")
    private String issuer;

    @JsonProperty("organisational_unit_id")
    @ValidWorldpay3dsFlexIssuerOrOrganisationalUnitId(message = "Field [organisational_unit_id] must be 24 lower-case hexadecimal characters")
    @Schema(description = "Lower-case hexadecimal characters. Should only contain characters [0-9a-f]", minLength = 24, maxLength = 24,
            example = "57992a087a0c4849895ab8a2")
    private String organisationalUnitId;

    @JsonProperty("jwt_mac_key")
    @ValidWorldpay3dsFlexJwtMacKey
    @Schema(description = "UUID in lowercase canonical representation", example = "4cabd5d2-0133-4e82-b0e5-2024dbeddaa9")
    private String jwtMacKey;

    public Worldpay3dsFlexCredentialsRequest() {
        //Blank Constructor Needed For Instantiation
    }

    private Worldpay3dsFlexCredentialsRequest(WorldpayUpdate3dsFlexCredentialsRequestBuilder builder) {
        this.issuer = builder.issuer;
        this.organisationalUnitId = builder.organisationalUnitId;
        this.jwtMacKey = builder.jwtMacKey;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getOrganisationalUnitId() {
        return organisationalUnitId;
    }

    public String getJwtMacKey() {
        return jwtMacKey;
    }

    public static final class WorldpayUpdate3dsFlexCredentialsRequestBuilder {
        private String issuer;
        private String organisationalUnitId;
        private String jwtMacKey;

        private WorldpayUpdate3dsFlexCredentialsRequestBuilder() {
        }

        public WorldpayUpdate3dsFlexCredentialsRequestBuilder withIssuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public WorldpayUpdate3dsFlexCredentialsRequestBuilder withOrganisationalUnitId(String organisationalUnitId) {
            this.organisationalUnitId = organisationalUnitId;
            return this;
        }

        public WorldpayUpdate3dsFlexCredentialsRequestBuilder withJwtMacKey(String jwtMacKey) {
            this.jwtMacKey = jwtMacKey;
            return this;
        }

        public Worldpay3dsFlexCredentialsRequest build() {
            return new Worldpay3dsFlexCredentialsRequest(this);
        }
    }
}

