package com.stellarnear;

import java.time.Instant;

public class CardChange {
    private String name;
    private Instant updatedAtUtc;
    private int quantityDelta;

    public int getQuantityDelta() {
        return quantityDelta;
    }

    public CardChange(String name, Instant updatedAtUtc, int quantityDelta) {
        this.name = name;
        this.updatedAtUtc = updatedAtUtc;
        this.quantityDelta = quantityDelta;
    }

    // Getters and toString() method for easy logging and testing
    public String getName() {
        return name;
    }

    public Instant getUpdatedAtUtc() {
        return updatedAtUtc;
    }

}