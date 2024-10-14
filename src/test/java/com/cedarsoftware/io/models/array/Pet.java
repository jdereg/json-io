package com.cedarsoftware.io.models.array;

import java.util.Date;

public class Pet {
    private String name;
    private String type;
    private Date birthday;

    // Constructors
    public Pet() {}

    public Pet(String name, String type, Date birthday) {
        this.name = name;
        this.type = type;
        this.birthday = birthday;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Date getBirthday() {
        return birthday;
    }

    public void setBirthday(Date birthday) {
        this.birthday = birthday;
    }

    @Override
    public String toString() {
        return "Pet{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", birthday=" + birthday +
                '}';
    }
}
