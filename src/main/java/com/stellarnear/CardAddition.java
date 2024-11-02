package com.stellarnear;

import java.time.Instant;

public class CardAddition {
    private String name;
    private Instant updatedAtUtc;

    public CardAddition(String name, Instant updatedAtUtc) {
        this.name = name;
        this.updatedAtUtc = updatedAtUtc;
    }

    // Getters and toString() method for easy logging and testing
    public String getName() {
        return name;
    }

    public Instant getUpdatedAtUtc() {
        return updatedAtUtc;
    }

    @Override
    public String toString() {
        return "CardAddition{name='" + name + "', updatedAtUtc=" + updatedAtUtc + "}";
    }
}