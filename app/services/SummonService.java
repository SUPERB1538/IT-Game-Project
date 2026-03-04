package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.*;
import structures.basic.Position;
import structures.basic.Tile;
import utils.BasicObjectBuilders;

/**
 * Creature summoning logic. Reliably derives the corresponding unit configuration file path based on the card configuration file name.
 */
public class SummonService {

    private final CommandDispatcher ui = new CommandDispatcher();

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

        // Only creature summon handled here
        if (!isCreatureCard(card)) {
            // refund mana for now (spells not handled here)
            p1.setMana(Math.min(9, p1.getMana() + cost));
            BasicCommands.setPlayer1Mana(out, gameState.getPlayer1());
            ui.notifyP1(out, "Spell cards not implemented", 2);
            return false;
        }

        String unitConfig = unitConfFromCardConf(card.getConfigFile());
        if (unitConfig == null) {
            // refund mana
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
            // refund mana
            p1.setMana(Math.min(9, p1.getMana() + cost));
            BasicCommands.setPlayer1Mana(out, gameState.getPlayer1());
            ui.notifyP1(out, "Summon failed, cannot load: " + unitConfig, 3);
            return false;
        }

        // Stats derived from config file token
        int[] stats = creatureStats(card.getConfigFile());
        int atk = stats[0];
        int hp  = stats[1];

        summoned.setOwnerPlayerId(1);
        summoned.setMaxHealth(hp);
        summoned.setHealth(hp);
        summoned.setAttack(atk);
        summoned.setPositionByTile(targetTile);

        // Summoning sickness tracking
        summoned.setSummonedOnTurn(gameState.getGlobalTurnNumber());

        // Put on board + index
        gameState.getBoard().putUnit(targetPos, summoned);
        gameState.addUnit(summoned);

        // Draw unit + stats
        try { Thread.sleep(80); } catch (Exception ignored) {}
        BasicCommands.drawUnit(out, summoned, targetTile);
        try { Thread.sleep(80); } catch (Exception ignored) {}
        BasicCommands.setUnitHealth(out, summoned, summoned.getHealth());
        BasicCommands.setUnitAttack(out, summoned, summoned.getAttack());

        // Remove card + compact hand
        hand.removeFromSlot(handPos);
        compactHandLeft(hand);

        // Redraw hand
        ui.redrawHandNormal(out, gameState);

        ui.notifyP1(out, "Summoned!", 2);
        return true;
    }

    private boolean isCreatureCard(CardInstance card) {
        return card != null
                && card.getConfigFile() != null
                && card.getConfigFile().contains("_c_u_");
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