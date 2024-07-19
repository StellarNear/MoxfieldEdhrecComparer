package com.stellarnear;

import java.util.List;

public class Deck {
    private String name;
    private String moxfieldId;
    private List<String> edhreclinks;

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMoxfieldId() {
        return moxfieldId;
    }

    public void setMoxfieldId(String moxfieldId) {
        this.moxfieldId = moxfieldId;
    }

    public List<String> getEdhreclinks() {
        return edhreclinks;
    }

    public void setEdhreclinks(List<String> edhreclinks) {
        this.edhreclinks = edhreclinks;
    }
}