package uk.gov.pay.connector.model;

public class Address {
    private String line1;
    private String line2;
    private String line3;
    private String postcode;
    private String city;
    private String county;
    private String country;
    
    public static Address anAddress(){
        return new Address();
    }

    public Address withLine1(String addressLine1) {
        this.line1 = addressLine1;
        return this;
    }

    public Address withLine2(String addressLine2) {
        this.line2 = addressLine2;
        return this;
    }

    public Address withLine3(String addressLine3) {
        this.line3 = addressLine3;
        return this;
    }

    public Address withZip(String addressZip) {
        this.postcode = addressZip;
        return this;
    }

    public Address withCity(String addressCity) {
        this.city = addressCity;
        return this;
    }

    public Address withCountry(String country) {
        this.country = country;
        return this;
    }

    public Address withCounty(String county) {
        this.county = county;
        return this;
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
