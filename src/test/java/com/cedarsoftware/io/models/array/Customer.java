package com.cedarsoftware.io.models.array;

import java.util.Arrays;

public class Customer {
    private String customerName;
    private String zipCode;
    private Location[] locations;
    private Object[] locationz;
    private Person[] employees;
    private Object[] employeez;

    // Constructors
    public Customer() {}

    public Customer(String customerName, String zipCode, Location[] locations, Object[] locationz,
                    Person[] employees, Object[] employeez) {
        this.customerName = customerName;
        this.zipCode = zipCode;
        this.locations = locations;
        this.locationz = locationz;
        this.employees = employees;
        this.employeez = employeez;
    }

    // Getters and Setters
    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public Location[] getLocations() {
        return locations;
    }

    public void setLocations(Location[] locations) {
        this.locations = locations;
    }

    public Object[] getLocationz() {
        return locationz;
    }

    public void setLocationz(Object[] locationz) {
        this.locationz = locationz;
    }

    public Person[] getEmployees() {
        return employees;
    }

    public void setEmployees(Person[] employees) {
        this.employees = employees;
    }

    public Object[] getEmployeez() {
        return employeez;
    }

    public void setEmployeez(Object[] employeez) {
        this.employeez = employeez;
    }

    @Override
    public String toString() {
        return "Customer{" +
                "customerName='" + customerName + '\'' +
                ", zipCode='" + zipCode + '\'' +
                ", locations=" + Arrays.toString(locations) +
                ", locationz=" + Arrays.toString(locationz) +
                ", employees=" + Arrays.toString(employees) +
                ", employeez=" + Arrays.toString(employeez) +
                '}';
    }
}
