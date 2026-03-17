package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.AvatarUnit;
import structures.GameState;
import structures.UnitEntity;
import structures.basic.UnitAnimationType;

public class UnitRemovalService {

    private final CommandDispatcher ui = new CommandDispatcher();
    private final TriggerSystem triggerSystem = new TriggerSystem();

    public void removeUnit(ActorRef out, GameState gameState, UnitEntity dead, String reasonNotification) {
        if (out == null || gameState == null || dead == null) return;

        if (dead instanceof AvatarUnit) {
            return;
        }

        BasicCommands.playUnitAnimation(out, dead, UnitAnimationType.death);
        BasicCommands.deleteUnit(out, dead);

        gameState.getBoard().removeUnit(dead.getPosition());
        gameState.removeUnitById(dead.getId());

        if (reasonNotification != null && !reasonNotification.isEmpty()) {
            ui.notifyP1(out, reasonNotification, 2);
        }

        triggerSystem.onUnitDied(out, gameState, dead);
    }
}