package structures;

import structures.basic.Card;


public class CardInstance {

    private final String cardKey;
    private final int manaCost;

    private final String configFile;

    private final Card visual;

    public CardInstance(String cardKey, int manaCost, String configFile, Card visual) {
        this.cardKey = cardKey;
        this.manaCost = manaCost;
        this.configFile = configFile;
        this.visual = visual;
    }

    public String getCardKey() {
        return cardKey;
    }

    public int getManaCost() {
        return manaCost;
    }

    public String getConfigFile() {
        return configFile;
    }

    public Card getVisual() {
        return visual;
    }

    public boolean isCreatureCard() {
        return configFile != null && configFile.contains("_c_u_");
    }

    public boolean isSpellCard() {
        return configFile != null && configFile.contains("_c_s_");
    }
}