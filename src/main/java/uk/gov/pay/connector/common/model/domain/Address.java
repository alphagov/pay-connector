package uk.gov.pay.connector.common.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.paymentprocessor.model.AddressEntity;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Address {

    @Schema(example = "Address line 1")
    private String line1;
    @Schema(example = "Address line 2")
    private String line2;
    @Schema(example = "AB1 2CD")
    private String postcode;
    @Schema(example = "London")
    private String city;

    @Schema(example = "county")
    private String county;
    @Schema(example = "GB")
    private String country;

    public Address() {
    }

    public Address(String line1, String line2, String postcode, String city, String county, String country) {
        this.line1 = line1;
        this.line2 = line2;
        this.postcode = postcode;
        this.city = city;
        this.county = county;
        this.country = country;
    }

    public static Address from(AddressEntity addressEntity) {
        return new Address(addressEntity.getLine1(),
                addressEntity.getLine2(),
                addressEntity.getPostcode(),
                addressEntity.getCity(),
                addressEntity.getCounty(),
                addressEntity.getCountry());
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

    public void setLine1(String line1) {
        this.line1 = line1;
    }

    public void setLine2(String line2) {
        this.line2 = line2;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setCounty(String county) {
        this.county = county;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Address address = (Address) o;

        if (line1 != null ? !line1.equals(address.line1) : address.line1 != null) return false;
        if (line2 != null ? !line2.equals(address.line2) : address.line2 != null) return false;
        if (postcode != null ? !postcode.equals(address.postcode) : address.postcode != null) return false;
        if (city != null ? !city.equals(address.city) : address.city != null) return false;
        if (county != null ? !county.equals(address.county) : address.county != null) return false;
        return country != null ? country.equals(address.country) : address.country == null;

    }

    @Override
    public int hashCode() {
        int result = line1 != null ? line1.hashCode() : 0;
        result = 31 * result + (line2 != null ? line2.hashCode() : 0);
        result = 31 * result + (postcode != null ? postcode.hashCode() : 0);
        result = 31 * result + (city != null ? city.hashCode() : 0);
        result = 31 * result + (county != null ? county.hashCode() : 0);
        result = 31 * result + (country != null ? country.hashCode() : 0);
        return result;
    }
}
