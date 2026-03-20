package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.AvatarUnit;
import structures.GameState;
import structures.UnitEntity;

/**
 * Handles damage and healing logic, including health updates,
 * UI synchronisation, and triggering related effects.
 */
public class DamageService {

    private final GameEndChecker gameEndChecker = new GameEndChecker();
    private final TriggerSystem triggerSystem = new TriggerSystem();

    public void dealDamage(ActorRef out, GameState gameState, UnitEntity target, int amount) {
        if (out == null || gameState == null || target == null) return;
        if (amount <= 0) return;

        target.applyDamage(amount);
        BasicCommands.setUnitHealth(out, target, target.getHealth());

        syncIfAvatar(out, gameState, target);

        if (target instanceof AvatarUnit) {
            triggerSystem.onAvatarDamaged(out, gameState, (AvatarUnit) target);
        }

        gameEndChecker.checkAndHandle(out, gameState);
    }

    public void healUnit(ActorRef out, GameState gameState, UnitEntity target, int amount) {
        if (out == null || gameState == null || target == null) return;
        if (amount <= 0) return;
        if (target.isDead()) return;

        int newHealth = Math.min(target.getMaxHealth(), target.getHealth() + amount);
        target.setHealth(newHealth);

        BasicCommands.setUnitHealth(out, target, target.getHealth());
        syncIfAvatar(out, gameState, target);
    }

    public void syncIfAvatar(ActorRef out, GameState gameState, UnitEntity unit) {
        if (out == null || gameState == null || unit == null) return;

        AvatarUnit p1 = gameState.getP1Avatar();
        AvatarUnit p2 = gameState.getP2Avatar();

        if (p1 != null && unit.getId() == p1.getId()) {
            gameState.getPlayer1().setHealth(unit.getHealth());
            BasicCommands.setPlayer1Health(out, gameState.getPlayer1());
        }

        if (p2 != null && unit.getId() == p2.getId()) {
            gameState.getPlayer2().setHealth(unit.getHealth());
            BasicCommands.setPlayer2Health(out, gameState.getPlayer2());
        }
    }
}