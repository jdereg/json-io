package com.cedarsoftware.io.models;

import java.beans.Transient;

public class ObjectSerializationIssue {

    FoodType food;

    public FoodType getFoodType() {
        return food;
    }

    public void setFoodType(FoodType food) {
        this.food = food;
    }

    @Transient
    public String getFood() {
        return food.getDisplayId();
    }

    public void setFood(String id) {
        this.food = FoodType.findById(id);
    }
}
