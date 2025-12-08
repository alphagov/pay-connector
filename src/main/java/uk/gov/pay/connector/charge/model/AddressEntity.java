package uk.gov.pay.connector.charge.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import uk.gov.pay.connector.common.model.domain.Address;

import static uk.gov.pay.connector.common.model.domain.NumbersInStringsSanitizer.sanitize;

@Embeddable
public class AddressEntity {

    @Column(name = "address_line1")
    @Schema(example = "address line 1")
    private String line1;
    @Column(name = "address_line2")
    @Schema(example = "address line 2")
    private String line2;
    @Column(name = "address_postcode")
    @Schema(example = "AB1 2CD")
    private String postcode;
    @Column(name = "address_city")
    @Schema(example = "London")
    private String city;
    @Column(name = "address_county")
    @Schema(example = "London")
    private String county;
    @Column(name = "address_country")
    @Schema(example = "GB")
    private String country;
    @Column(name = "address_state_province")
    @Schema(example = "")
    private String stateOrProvince;

    public AddressEntity() {
        //for jpa
    }

    public AddressEntity(Address address) {
        this.line1 = sanitize(address.getLine1());
        this.line2 = sanitize(address.getLine2());
        this.postcode = sanitize(address.getPostcode());
        this.city = sanitize(address.getCity());
        this.county = sanitize(address.getCounty());
        this.country = sanitize(address.getCountry());
    }

    Address toAddress() {
        return new Address(line1, line2, postcode, city, county, country);
    }

    public static AddressEntity anAddress() {
        return new AddressEntity();
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

    public String getCountry() {
        return country;
    }

    public String getStateOrProvince() {
        return stateOrProvince;
    }

    public void setStateOrProvince(String stateOrProvince) {
        this.stateOrProvince = stateOrProvince;
    }

    public String getCounty() {
        return county;
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

}
