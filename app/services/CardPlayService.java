package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.*;
import structures.basic.Position; // Person B: Required for spatial coordinate handling
import java.util.List;            // Person B: Required for dynamic target lists
import java.util.Objects;

/**
 * Person B: Service responsible for the card-playing workflow.
 * This version integrates TargetingService to decouple targeting rules from the play flow.
 */
public class CardPlayService {

    private final CommandDispatcher ui = new CommandDispatcher();
    private final EffectResolver effectResolver = new EffectResolver();

    // Person B: Encapsulated targeting logic into a dedicated service.
    private final TargetingService targetingService = new TargetingService();

    public void onCardClicked(ActorRef out, GameState gameState, int handPos) {
        if (gameState.isGameOver()) return;
        if (out == null || gameState == null) return;

        if (gameState.getCurrentPlayerId() != 1) {
            ui.notifyP1(out, "Not your turn", 2);
            return;
        }

        PlayerState p1 = gameState.getP1State();
        if (p1 == null || p1.getHand() == null) return;

        Hand hand = p1.getHand();
        CardInstance ci = hand.getBySlot(handPos);

        if (ci == null || Objects.equals(gameState.getSelectedCardPos(), handPos)) {
            clearSelection(out, gameState);
            return;
        }

        ui.clearAllHighlights(out, gameState);
        gameState.setSelectedUnitId(null);

        if (!p1.canAfford(ci.getManaCost())) {
            ui.notifyP1(out, "Not enough mana", 2);
            ui.highlightHandCard(out, hand, handPos);
            gameState.setSelectedCardPos(null);
            return;
        }

        gameState.setSelectedCardPos(handPos);
        ui.highlightHandCard(out, hand, handPos);

        if (ci.isCreatureCard()) {
            gameState.setWaitingSpellTarget(false);
            gameState.setSelectedSpellCardPos(null);
            gameState.getHighlightedSpellTargets().clear();
            ui.highlightSummonTiles(out, gameState);
        } else {
            // Person B: Spell targeting flow.
            gameState.setWaitingSpellTarget(true);
            gameState.setSelectedSpellCardPos(handPos);
            ui.clearSummonTilesOnly(out, gameState);
            
            // Person B: Dynamically retrieve valid targets based on the specific spell card.
            // This architecture supports units, avatars, and empty tiles.
            List<Position> validTargets = targetingService.getValidTargets(ci, gameState);
            
            for (Position p : validTargets) {
                ui.drawTileMode(out, p.getTilex(), p.getTiley(), CommandDispatcher.TILE_SPELL_TARGET_HIGHLIGHT);
                gameState.getHighlightedSpellTargets().add(p.getTilex() + "," + p.getTiley());
            }
            
            if (validTargets.isEmpty()) {
                ui.notifyP1(out, "No valid targets for this spell", 2);
                gameState.setWaitingSpellTarget(false);
                gameState.setSelectedSpellCardPos(null);
            }
        }
    }

    /**
     * Person B: Finalized spell casting method.
     * Supports targeting positions rather than just units to allow for ground-targeted spells.
     */
    public boolean castSelectedSpell(ActorRef out, GameState gameState, Position targetPos) {
        if (gameState.isGameOver()) return false;
        
        Integer spellPos = gameState.getSelectedSpellCardPos();
        if (spellPos == null) return false;

        PlayerState p1 = gameState.getP1State();
        Hand hand = p1.getHand();
        CardInstance spellCard = hand.getBySlot(spellPos);
        if (spellCard == null) return false;

        int cost = spellCard.getManaCost();
        if (!p1.spendMana(cost)) {
            ui.notifyP1(out, "Not enough mana", 2);
            return false;
        }
        BasicCommands.setPlayer1Mana(out, gameState.getPlayer1());

        // Person B: Dynamically resolve if a unit exists at the target position.
        UnitEntity targetUnit = gameState.getBoard().getUnitAt(targetPos).orElse(null);

        // Person B: Forward to the resolver. 
        effectResolver.applySpell(out, gameState, spellCard, targetUnit, targetPos);

        hand.removeFromSlot(spellPos);
        compactHandLeft(hand);
        ui.redrawHandNormal(out, gameState);

        gameState.setSelectedCardPos(null);
        gameState.setWaitingSpellTarget(false);
        gameState.setSelectedSpellCardPos(null);
        ui.clearSpellTargetsOnly(out, gameState);
        ui.clearAllHighlights(out, gameState);

        return true;
    }

    public void clearSelection(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;
        gameState.setSelectedCardPos(null);
        gameState.setWaitingSpellTarget(false);
        gameState.setSelectedSpellCardPos(null);
        ui.clearSpellTargetsOnly(out, gameState);
        ui.clearSummonTilesOnly(out, gameState);
        ui.redrawHandNormal(out, gameState);
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
}