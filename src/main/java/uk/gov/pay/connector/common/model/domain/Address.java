package uk.gov.pay.connector.common.model.domain;

import java.util.Objects;

public class Address {

    private String line1;
    private String line2;
    private String postcode;
    private String city;
    private String county;
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

        return Objects.equals(line1, address.line1)
            && Objects.equals(line2, address.line2)
            && Objects.equals(postcode, address.postcode)
            && Objects.equals(city, address.city)
            && Objects.equals(county, address.county)
            && Objects.equals(country, address.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(line1, line2, postcode, city, county, country);
    }
}
