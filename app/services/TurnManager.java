package services;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.JsonNode;
import commands.BasicCommands;
import structures.GameState;
import ai.AIDecisionEngine;

/**
 * Turn flow orchestrator (TurnService removed).
 * Keeps behaviour same as your original code.
 */
public class TurnManager {

    private final ManaService manaService = new ManaService();
    private final CardDrawService cardDrawService = new CardDrawService();

    public void onEndTurn(ActorRef out, GameState gameState, JsonNode message) {

        if (gameState == null) return;
        if (gameState.isGameOver()) return;

        // Clear UI interaction state
        GameRulesEngine.clearAllHighlightsUI(out, gameState);
        gameState.setSelectedUnitId(null);
        gameState.setSelectedCardPos(null);

        // 1) Current player loses any unspent mana
        int current = gameState.getCurrentPlayerId();
        manaService.clearUnspentManaAndUpdateUI(out, gameState, current);

        if (gameState.isGameOver()) return;

        // 2) Switch current player
        int next = (current == 1) ? 2 : 1;
        gameState.setCurrentPlayerId(next);

        // 3) Begin next player's turn (refill mana, reset units, etc.)
        gameState.beginTurn();
        manaService.updateManaUI(out, gameState, next);

        // 4) Draw 1 card for the player whose turn is starting
        cardDrawService.drawOneCardAtTurnStart(out, gameState, next);

        if (gameState.isGameOver()) return;

        // 5) AI flow: AI takes its whole turn immediately, then return to human
        if (next == 2) {
            try { Thread.sleep(200); } catch (Exception ignored) {}
            AIDecisionEngine.playTurn(out, gameState);
            try { Thread.sleep(200); } catch (Exception ignored) {}

            if (gameState.isGameOver()) return;
            manaService.clearUnspentManaAndUpdateUI(out, gameState, 2);
            if (gameState.isGameOver()) return;

            gameState.setCurrentPlayerId(1);
            gameState.beginTurn();
            manaService.updateManaUI(out, gameState, 1);
            cardDrawService.drawOneCardAtTurnStart(out, gameState, 1);

            if (gameState.isGameOver()) return;
            BasicCommands.addPlayer1Notification(out, "Your turn", 2);
            return;
        }

        if (gameState.isGameOver()) return;
        if (next == 1) {
            BasicCommands.addPlayer1Notification(out, "Your turn", 2);
        } else {
            BasicCommands.addPlayer1Notification(out, "Opponent's turn", 2);
        }

        System.out.println("[EndTurn] " + current + " -> " + next
                + " | P1mana=" + gameState.getPlayer1().getMana()
                + " | P2mana=" + gameState.getPlayer2().getMana());
    }
}