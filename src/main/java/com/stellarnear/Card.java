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
    private String scryfall_id;

    public String getScryfall_id() {
        return scryfall_id;
    }
    public void setScryfall_id(String scryfall_id) {
        this.scryfall_id = scryfall_id;
    }
    public int getSynergyPercent() {
        return synergyPercent;
    }

    // Constructor
    public Card(String name,String rarity, String mana_cost, int cmc, String type_line, List<String> color_identity, String commanderLegality, String oracle_text, String scryfall_id) {
        this.name=name;
        this.rarity = rarity;
        this.mana_cost = mana_cost;
        this.cmc = cmc;
        this.type_line = type_line;
        this.color_identity = color_identity;
        this.commanderLegality=commanderLegality;
        this.oracleText = oracle_text;
        this.scryfall_id = scryfall_id;
    }
    public Card(String name2,int nDeck, int percent, int synergPercent, String id) {
        this.name=name2;
        this.nDeck=nDeck;
        this.percentPresentDeck=percent;
        this.synergyPercent=synergPercent;
        this.scryfall_id = id;
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
    public String getScryfallImg() {
        if (this.scryfall_id!=null) {
            return "https://api.scryfall.com/cards/" + this.scryfall_id + "?format=image";
        } else {
            return "https://ih1.redbubble.net/image.1861329518.2941/flat,750x,075,f-pad,750x1000,f8f8f8.jpg";
        }
    }
}
