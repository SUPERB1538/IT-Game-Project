package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;

/**
 * Manages player mana updates and synchronises mana values with the UI.
 */
public class ManaService {

    public void clearUnspentManaAndUpdateUI(ActorRef out, GameState gameState, int playerId) {
        if (gameState == null) return;
        gameState.clearCurrentMana();
        updateManaUI(out, gameState, playerId);
    }

    public void updateManaUI(ActorRef out, GameState gameState, int playerId) {
        if (gameState == null) return;
        if (playerId == 1) {
            BasicCommands.setPlayer1Mana(out, gameState.getPlayer1());
        } else {
            BasicCommands.setPlayer2Mana(out, gameState.getPlayer2());
        }
    }
}