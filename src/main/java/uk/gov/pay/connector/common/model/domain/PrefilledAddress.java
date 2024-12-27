package uk.gov.pay.connector.common.model.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrefilledAddress {

    @Size(max = 255, message = "Field [line1] can have a size between 0 and 255")
    @Schema(example = "address line 1")
    private final String line1;
    @Size(max = 255, message = "Field [line2] can have a size between 0 and 255")
    @Schema(example = "address line 2")
    private final String line2;
    @Size(max = 25, message = "Field [postcode] can have a size between 0 and 25")
    @Schema(example = "AB1 2CD")
    private final String postcode;
    @Size(max = 255, message = "Field [city] can have a size between 0 and 255")
    @Schema(example = "London")
    private final String city;
    @Size(max = 255, message = "Field [county] can have a size between 0 and 255")
    @Schema(example = "country")
    private final String county;
    @Schema(example = "GB")
    private final String country;

    @JsonCreator
    public PrefilledAddress(@JsonProperty("line1") String line1,
                            @JsonProperty("line2") String line2,
                            @JsonProperty("postcode") String postcode,
                            @JsonProperty("city") String city,
                            @JsonProperty("county") String county,
                            @JsonProperty("country") String country) {
        this.line1 = line1;
        this.line2 = line2;
        this.postcode = postcode;
        this.city = city;
        this.county = county;
        this.country = normalizeCountry(country);
    }

    public Address toAddress() {
        return new Address(line1, line2, postcode, city, county, country);
    }

    private String normalizeCountry(String country) {
        if (country == null || country.length() != 2) {
            return null;
        }
        return country;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getPostcode() {
        return postcode;
    }

    public String getCity() {
        return city;
    }

    public String getCounty() {
        return county;
    }

    public String getCountry() {
        return country;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PrefilledAddress that = (PrefilledAddress) o;
        return Objects.equals(line1, that.line1) &&
                Objects.equals(line2, that.line2) &&
                Objects.equals(postcode, that.postcode) &&
                Objects.equals(city, that.city) &&
                Objects.equals(county, that.county) &&
                Objects.equals(country, that.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(line1, line2, postcode, city, county, country);
    }
}
