package com.stellarnear;

import java.util.List;

public class Card {
    private String rarity;
    private String mana_cost;
    private int cmc;
    private String type_line;
    private List<String> color_identity;
    private String name;
    private String commanderLegality;
    private String oracleText;

    int percentPresentDeck;
    public int getPercentPresentDeck() {
        return percentPresentDeck;
    }
    public void setPercentPresentDeck(int higestPercentPresentDeck) {
        this.percentPresentDeck = higestPercentPresentDeck;
    }

    int nDeck;
    public int getnDeck() {
        return nDeck;
    }
    public void setnDeck(int nDeck) {
        this.nDeck = nDeck;
    }

    int synergyPercent;

    public int getSynergyPercent() {
        return synergyPercent;
    }

    // Constructor
    public Card(String name,String rarity, String mana_cost, int cmc, String type_line, List<String> color_identity, String commanderLegality, String oracle_text) {
        this.name=name;
        this.rarity = rarity;
        this.mana_cost = mana_cost;
        this.cmc = cmc;
        this.type_line = type_line;
        this.color_identity = color_identity;
        this.commanderLegality=commanderLegality;
        this.oracleText = oracle_text;
    }
    public Card(String name2,int nDeck, int percent, int synergPercent) {
        this.name=name2;
        this.nDeck=nDeck;
        this.percentPresentDeck=percent;
        this.synergyPercent=synergPercent;
    }
    public String getName() {
        return name;
    }

    // Getters and setters
    public String getRarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    public String getMana_cost() {
        return mana_cost;
    }

    public void setMana_cost(String mana_cost) {
        this.mana_cost = mana_cost;
    }

    public int getCmc() {
        return cmc;
    }

    public void setCmc(int cmc) {
        this.cmc = cmc;
    }

    public String getType_line() {
        return type_line;
    }

    public void setType_line(String type_line) {
        this.type_line = type_line;
    }

    public List<String> getColor_identity() {
        return color_identity;
    }

    public void setColor_identity(List<String> color_identity) {
        this.color_identity = color_identity;
    }

    public String getCommanderLegality() {
        return commanderLegality;
    }

    public String getOracleText() {
        return oracleText;
    }
    public void setSynergyPercent(int synergPercent) {
        this.synergyPercent=synergPercent;
    }
}
