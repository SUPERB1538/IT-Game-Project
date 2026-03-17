package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.*;
import structures.basic.Position;
import structures.basic.Tile;
import utils.BasicObjectBuilders;

/**
 * Creature summoning logic. 
 * This service handles both standard card summoning and direct token creation for spell effects.
 */
public class SummonService {

    private final CommandDispatcher ui = new CommandDispatcher();
   
    // =========================================================
    // START OF PERSON B'S ADDITION
    // =========================================================
    
    /**
     * Person B: Direct summoning for spell tokens (e.g., Wraithlings). 
     * This method allows spells to create units directly on the board without 
     * consuming a card from the hand, essential for Wraithling Swarm and Dark Terminus.
     */
    public void summonToken(ActorRef out, GameState gameState, String unitConfig, Position targetPos, int ownerId) {
        UnitEntity summoned;
        try {
            // Person B: Load unit configuration dynamically using the global nextUnitId
            summoned = (UnitEntity) BasicObjectBuilders.loadUnit(unitConfig, gameState.nextUnitId(), UnitEntity.class);
        } catch (Exception e) {
            return;
        }

        if (summoned == null) return;

        // Person B: Set default token stats (Wraithlings are standard 1/1 units)
        summoned.setOwnerPlayerId(ownerId);
        summoned.setMaxHealth(1);
        summoned.setHealth(1);
        summoned.setAttack(1);
        applyKeywordsForUnit(summoned, unitConfig);
        // Person B: Bind unit to the physical board location based on target coordinates
        Tile tile = BasicObjectBuilders.loadTile(targetPos.getTilex(), targetPos.getTiley());
        summoned.setPositionByTile(tile);
        
        // Person B: Enforce summoning sickness tracking for the current turn
        summoned.setSummonedOnTurn(gameState.getGlobalTurnNumber());

        // Person B: Update backend state - registry in board occupancy and global unit index
        gameState.getBoard().putUnit(targetPos, summoned);
        gameState.addUnit(summoned);

        // Person B: Synchronize frontend UI - drawing and setting stats
        try { Thread.sleep(50); } catch (Exception ignored) {}
        BasicCommands.drawUnit(out, summoned, tile);
        try { Thread.sleep(50); } catch (Exception ignored) {}
        BasicCommands.setUnitHealth(out, summoned, summoned.getHealth());
        BasicCommands.setUnitAttack(out, summoned, summoned.getAttack());
    }
    
    // =========================================================
    // END OF PERSON B'S ADDITION
    // =========================================================


    /**
     * Logic written by others: Handles summoning from a creature card in hand.
     */
    public boolean trySummonFromSelectedCard(ActorRef out, GameState gameState, Tile targetTile, Position targetPos) {
        if (out == null || gameState == null) return false;

        if (gameState.getCurrentPlayerId() != 1) {
            ui.notifyP1(out, "Not your turn", 2);
            return false;
        }

        Integer handPos = gameState.getSelectedCardPos();
        if (handPos == null) return false;

        PlayerState p1 = gameState.getP1State();
        if (p1 == null || p1.getHand() == null) return false;

        Hand hand = p1.getHand();
        CardInstance card = hand.getBySlot(handPos);
        if (card == null) return false;

        if (!gameState.getBoard().isValidPosition(targetPos) || gameState.getBoard().isOccupied(targetPos)) {
            ui.notifyP1(out, "Invalid target", 2);
            return false;
        }

        int cost = card.getManaCost();
        if (!p1.spendMana(cost)) {
            ui.notifyP1(out, "Not enough mana", 2);
            return false;
        }
        BasicCommands.setPlayer1Mana(out, gameState.getPlayer1());

        if (!isCreatureCard(card)) {
            p1.setMana(Math.min(9, p1.getMana() + cost));
            BasicCommands.setPlayer1Mana(out, gameState.getPlayer1());
            ui.notifyP1(out, "Spell cards not implemented", 2);
            return false;
        }

        String unitConfig = unitConfFromCardConf(card.getConfigFile());
        if (unitConfig == null) {
            p1.setMana(Math.min(9, p1.getMana() + cost));
            BasicCommands.setPlayer1Mana(out, gameState.getPlayer1());
            ui.notifyP1(out, "Unit config missing for: " + card.getCardKey(), 3);
            return false;
        }

        UnitEntity summoned;
        try {
            summoned = (UnitEntity) BasicObjectBuilders.loadUnit(unitConfig, gameState.nextUnitId(), UnitEntity.class);
        } catch (Exception e) {
            summoned = null;
        }

        if (summoned == null) {
            p1.setMana(Math.min(9, p1.getMana() + cost));
            BasicCommands.setPlayer1Mana(out, gameState.getPlayer1());
            ui.notifyP1(out, "Summon failed, cannot load: " + unitConfig, 3);
            return false;
        }

        int[] stats = creatureStats(card.getConfigFile());
        int atk = stats[0];
        int hp  = stats[1];

        summoned.setOwnerPlayerId(1);
        summoned.setMaxHealth(hp);
        summoned.setHealth(hp);
        summoned.setAttack(atk);
        summoned.setPositionByTile(targetTile);
        summoned.setSummonedOnTurn(gameState.getGlobalTurnNumber());
        
        applyKeywordsForUnit(summoned, card.getConfigFile());
        
        gameState.getBoard().putUnit(targetPos, summoned);
        gameState.addUnit(summoned);

        try { Thread.sleep(80); } catch (Exception ignored) {}
        BasicCommands.drawUnit(out, summoned, targetTile);

        try { Thread.sleep(80); } catch (Exception ignored) {}
        BasicCommands.setUnitHealth(out, summoned, summoned.getHealth());
        BasicCommands.setUnitAttack(out, summoned, summoned.getAttack());

        new TriggerSystem().onUnitSummoned(out, gameState, summoned);

        hand.removeFromSlot(handPos);
        compactHandLeft(hand);
        ui.redrawHandNormal(out, gameState);

        ui.notifyP1(out, "Summoned!", 2);
        return true;
    }

    private boolean isCreatureCard(CardInstance card) {
        return card != null && card.getConfigFile() != null && card.getConfigFile().contains("_c_u_");
    }

    private int[] creatureStats(String tokenSource) {
        if (tokenSource == null) return new int[]{1, 1};
        String k = tokenSource.toLowerCase();

        if (k.contains("bad_omen")) return new int[]{0, 1};
        if (k.contains("gloom_chaser")) return new int[]{3, 1};
        if (k.contains("shadow_watcher")) return new int[]{3, 2};
        if (k.contains("nightsorrow_assassin")) return new int[]{4, 2};
        if (k.contains("rock_pulveriser")) return new int[]{1, 4};
        if (k.contains("bloodmoon_priestess")) return new int[]{3, 3};
        if (k.contains("shadowdancer")) return new int[]{5, 4};

        return new int[]{1, 1};
    }
    public static void applyKeywordsForUnit(UnitEntity unit, String filePath) {
        if (unit == null || filePath == null) return;

        String k = filePath.toLowerCase();

        // Human deck
        if (k.contains("bad_omen")) {
            unit.addKeyword("DEATHWATCH");
            unit.addKeyword("BAD_OMEN_DW");
        }

        if (k.contains("gloom_chaser")) {
            unit.addKeyword("OPENING_GAMBIT");
            unit.addKeyword("GLOOM_CHASER_OG");
        }

        if (k.contains("rock_pulveriser")) {
            unit.addKeyword("PROVOKE");
        }

        if (k.contains("shadow_watcher")) {
            unit.addKeyword("DEATHWATCH");
            unit.addKeyword("SHADOW_WATCHER_DW");
        }

        if (k.contains("nightsorrow_assassin")) {
            unit.addKeyword("OPENING_GAMBIT");
            unit.addKeyword("NIGHTSORROW_ASSASSIN_OG");
        }

        if (k.contains("bloodmoon_priestess")) {
            unit.addKeyword("DEATHWATCH");
            unit.addKeyword("BLOODMOON_PRIESTESS_DW");
        }

        if (k.contains("shadowdancer")) {
            unit.addKeyword("DEATHWATCH");
            unit.addKeyword("SHADOWDANCER_DW");
        }

        // AI deck
        if (k.contains("swamp_entangler")) {
            unit.addKeyword("PROVOKE");
        }

        if (k.contains("silverguard_squire")) {
            unit.addKeyword("OPENING_GAMBIT");
            unit.addKeyword("SILVERGUARD_SQUIRE_OG");
        }

        if (k.contains("saberspine_tiger")) {
            unit.addKeyword("RUSH");
        }

        if (k.contains("silverguard_knight")) {
            unit.addKeyword("PROVOKE");
            unit.addKeyword("ZEAL");
        }

        if (k.contains("young_flamewing")) {
            unit.addKeyword("FLYING");
        }

        if (k.contains("ironcliff_guardian") || k.contains("ironcliffe_guardian")) {
            unit.addKeyword("PROVOKE");
        }
    }
    private void compactHandLeft(Hand hand) {
        if (hand == null) return;
        for (int slot = Hand.MIN_SLOT; slot < Hand.MAX_SLOT; slot++) {
            if (hand.getBySlot(slot) != null) continue;
            CardInstance next = hand.getBySlot(slot + 1);
            if (next == null) continue;
            hand.removeFromSlot(slot + 1);
            hand.putIntoSlot(slot, next);
            slot = Math.max(Hand.MIN_SLOT - 1, slot - 1);
        }
    }

    private static String unitConfFromCardConf(String cardConfigFile) {
        if (cardConfigFile == null) return null;
        int slash = Math.max(cardConfigFile.lastIndexOf('/'), cardConfigFile.lastIndexOf('\\'));
        String file = (slash >= 0) ? cardConfigFile.substring(slash + 1) : cardConfigFile;
        int idx = file.indexOf("_c_u_");
        if (idx < 0) return null;
        String unitName = file.substring(idx + "_c_u_".length());
        if (unitName.endsWith(".json")) unitName = unitName.substring(0, unitName.length() - 5);
        return "conf/gameconfs/units/" + unitName + ".json";
    }
}