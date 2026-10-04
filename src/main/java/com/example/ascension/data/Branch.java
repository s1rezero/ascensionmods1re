package com.example.ascension.data;

public enum Branch {
    COMBAT("Combat"),
    MINING("Mining"),
    EXPLORATION("Exploration"),
    INVENTORY("Inventory");

    public final String label;

    Branch(String label) {
        this.label = label;
    }

    public static Branch of(int index) {
        return values()[index];
    }
}
