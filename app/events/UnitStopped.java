package events;

import com.fasterxml.jackson.databind.JsonNode;
import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.UnitEntity;
import structures.basic.Position;
import structures.basic.Tile;
import structures.basic.UnitAnimationType;
import utils.BasicObjectBuilders;

/**
 * Indicates that a unit instance has stopped moving.
 * This is where the backend board array is officially updated.
 * * {
 * messageType = “unitStopped”
 * id = <unit id>
 * }
 * * @author Dr. Richard McCreadie
 *
 */
public class UnitStopped implements EventProcessor {

	@Override
	public void processEvent(ActorRef out, GameState gameState, JsonNode message) {
		if (gameState == null || message == null || !message.has("id")) return;

		int unitid = message.get("id").asInt();
		UnitEntity unit = gameState.getUnitById(unitid);
		if (unit == null) {
			gameState.setAnimationInProgress(false);
			gameState.clearPendingMove();
			return;
		}

		Position from = gameState.getPendingMoveFrom();
		Position to = gameState.getPendingMoveTo();

		if (from != null && to != null) {
			gameState.getBoard().moveUnit(from, to);

			Tile targetTile = BasicObjectBuilders.loadTile(to.getTilex(), to.getTiley());
			unit.moveTo(targetTile);
			unit.markMoved(gameState.getGlobalTurnNumber());

			BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.idle);
		}

		gameState.setAnimationInProgress(false);
		gameState.clearPendingMove();

		try {
			Thread.sleep(250);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		if (!gameState.isGameOver() && gameState.getCurrentPlayerId() == 2) {
			ai.AIDecisionEngine.playTurn(out, gameState);
		}
	}
}