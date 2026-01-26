package com.cedarsoftware.io.models;

import java.util.Arrays;

public enum FoodType {
    MEATS {
        @Override
        public String getId() {
            return "Meat";
        }
    },
    MILKS {
        @Override
        public String getId() {
            return "Milk";
        }
    },
    VEGETABLES {
        @Override
        public String getId() {
            return "Vegetable";
        }

    },
    FRUITS {
        @Override
        public String getId() {
            return "Fruits";
        }
    };

    public abstract String getId();

    public String getName() {
        return name();
    }

    public String getDisplayId() {
        return getId();
    }

    public static FoodType findById(String id) {
        return Arrays.stream(values()).filter(f -> f.getId().equals(id)).findFirst().orElse(null);
    }
}
