package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class Address {

    @Column(name = "address_line1")
    private String line1;
    @Column(name = "address_line2")
    private String line2;
    @Column(name = "address_postcode")
    private String postcode;
    @Column(name = "address_city")
    private String city;
    @Column(name = "address_county")
    private String county;
    @Column(name = "address_country")
    private String country;


    public static Address anAddress() {
        return new Address();
    }

    @JsonProperty
    public void setLine1(String line1) {
        this.line1 = line1;
    }

    @JsonProperty
    public void setLine2(String line2) {
        this.line2 = line2;
    }

    @JsonProperty
    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    @JsonProperty
    public void setCity(String city) {
        this.city = city;
    }

    @JsonProperty
    public void setCounty(String county) {
        this.county = county;
    }

    @JsonProperty
    public void setCountry(String country) {
        this.country = country;
    }

    public String getCountry() {
        return country;
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
