package services;

import akka.actor.ActorRef;
import structures.AvatarUnit;
import structures.GameState;

/**
 * Checks win/loss conditions and handles game termination logic
 * when an avatar dies.
 */
public class GameEndChecker {

    private final CommandDispatcher ui = new CommandDispatcher();
    private final VisualFeedbackManager vfx = new VisualFeedbackManager(ui);

    public boolean checkAndHandle(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return false;

        // only once
        if (gameState.isGameOver()) return true;

        AvatarUnit p1 = gameState.getP1Avatar();
        AvatarUnit p2 = gameState.getP2Avatar();
        if (p1 == null || p2 == null) return false;

        boolean p1Dead = p1.getHealth() <= 0;
        boolean p2Dead = p2.getHealth() <= 0;

        if (!p1Dead && !p2Dead) return false;

        gameState.setGameOver(true);

        gameState.setSelectedUnitId(null);
        gameState.setSelectedCardPos(null);

        gameState.setWaitingSpellTarget(false);
        gameState.setSelectedSpellCardPos(null);
        gameState.getHighlightedSpellTargets().clear();

        vfx.clearAllHighlights(out, gameState);

        if (p2Dead && !p1Dead) {
            ui.notifyP1(out, "You win!", 5);
        } else if (p1Dead && !p2Dead) {
            ui.notifyP1(out, "You lose!", 5);
        } else {
            ui.notifyP1(out, "Draw!", 5);
        }

        return true;
    }
}