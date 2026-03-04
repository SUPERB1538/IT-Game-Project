package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.*;

import java.util.Objects;

public class CardPlayService {

    private final CommandDispatcher ui = new CommandDispatcher();
    private final EffectResolver effectResolver = new EffectResolver();

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

        if (ci == null) {
            clearSelection(out, gameState);
            return;
        }

        if (Objects.equals(gameState.getSelectedCardPos(), handPos)) {
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

        if (isCreatureCard(ci)) {
            gameState.setWaitingSpellTarget(false);
            gameState.setSelectedSpellCardPos(null);
            gameState.getHighlightedSpellTargets().clear();
            ui.highlightSummonTiles(out, gameState);
        } else {
            gameState.setWaitingSpellTarget(true);
            gameState.setSelectedSpellCardPos(handPos);
            ui.clearSummonTilesOnly(out, gameState);
            ui.highlightSpellTargetsEnemyUnits(out, gameState);
        }
    }

    public boolean castSelectedSpellOnTarget(ActorRef out, GameState gameState, UnitEntity target) {
        if (gameState.isGameOver()) return false;
        if (out == null || gameState == null || target == null) return false;

        if (gameState.getCurrentPlayerId() != 1) {
            ui.notifyP1(out, "Not your turn", 2);
            return false;
        }

        Integer spellPos = gameState.getSelectedSpellCardPos();
        if (spellPos == null) return false;

        PlayerState p1 = gameState.getP1State();
        if (p1 == null || p1.getHand() == null) return false;

        Hand hand = p1.getHand();
        CardInstance spellCard = hand.getBySlot(spellPos);
        if (spellCard == null) return false;

        // Safety: must be a spell (not creature)
        if (isCreatureCard(spellCard)) return false;

        int cost = spellCard.getManaCost();
        if (!p1.spendMana(cost)) {
            ui.notifyP1(out, "Not enough mana", 2);
            return false;
        }
        BasicCommands.setPlayer1Mana(out, gameState.getPlayer1());

        // Apply effect (currently safe stub)
        effectResolver.applySpellToUnit(out, gameState, spellCard, target);

        // Remove card from hand + compact + redraw
        hand.removeFromSlot(spellPos);
        compactHandLeft(hand);
        ui.redrawHandNormal(out, gameState);

        // Clear spell targeting state + highlights
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

    private boolean isCreatureCard(CardInstance card) {
        return card != null && card.isCreatureCard();
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