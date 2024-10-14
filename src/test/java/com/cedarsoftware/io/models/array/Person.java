package com.cedarsoftware.io.models.array;

import java.util.Arrays;

public class Person {
    private String firstName;
    private String lastName;
    private Pet[] pets;
    private Object[] petz;

    // Constructors
    public Person() {}

    public Person(String firstName, String lastName, Pet[] pets, Object[] petz) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.pets = pets;
        this.petz = petz;
    }

    // Getters and Setters
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Pet[] getPets() {
        return pets;
    }

    public void setPets(Pet[] pets) {
        this.pets = pets;
    }

    public Object[] getPetz() {
        return petz;
    }

    public void setPetz(Object[] petz) {
        this.petz = petz;
    }

    @Override
    public String toString() {
        return "Person{" +
                "firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", pets=" + Arrays.toString(pets) +
                ", petz=" + Arrays.toString(petz) +
                '}';
    }
}
