package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Address {

    private String line1;
    private String line2;
    private String line3;
    private String postcode;
    private String city;
    private String county;
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
    public void setLine3(String line3) {
        this.line3 = line3;
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

    public String getLine3() {
        return line3;
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
