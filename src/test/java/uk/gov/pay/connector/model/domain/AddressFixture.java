package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.common.model.domain.Address;

public class AddressFixture {
    private String line1 = "125 Kingsway";
    private String line2 = "Aviation House";
    private String postcode = "WC2B 6NH";
    private String city = "London";
    private String county = "London";
    private String country = "United Kingdom";

    public static AddressFixture anAddress() {
        return new AddressFixture();
    }

    public Address build() {
        Address address = new Address();
        address.setLine1(line1);
        address.setLine2(line2);
        address.setPostcode(postcode);
        address.setCity(city);
        address.setCounty(county);
        address.setCountry(country);
        return address;
    }

    public AddressFixture withLine1(String line1) {
        this.line1 = line1;
        return this;
    }

    public AddressFixture withLine2(String line2) {
        this.line2 = line2;
        return this;
    }

    public AddressFixture withPostcode(String postcode) {
        this.postcode = postcode;
        return this;
    }

    public AddressFixture withCity(String city) {
        this.city = city;
        return this;
    }

    public AddressFixture withCounty(String county) {
        this.county = county;
        return this;
    }

    public AddressFixture withCountry(String country) {
        this.country = country;
        return this;
    }
}
