package services;

import akka.actor.ActorRef;
import commands.BasicCommands;
import structures.GameState;
import structures.UnitEntity;
import structures.basic.Position;
import structures.basic.UnitAnimationType;

import java.util.HashSet;
import java.util.Set;

/**
 * Resolves combat interactions between units, including attack validation,
 * damage exchange, counterattacks, and death handling.
 */
public class CombatResolver {

    private final CommandDispatcher ui = new CommandDispatcher();
    private final DamageService damageService = new DamageService();
    private final UnitRemovalService unitRemovalService = new UnitRemovalService();
    private final GameEndChecker gameEndChecker = new GameEndChecker();
    private final TriggerSystem triggerSystem = new TriggerSystem();

    public boolean tryAttack(ActorRef out, GameState gameState, UnitEntity attacker, UnitEntity defender) {
        if (gameState == null || gameState.isGameOver()) return false;
        if (out == null || attacker == null || defender == null) return false;

        if (!canAttackTarget(gameState, attacker, defender)) {
            ui.notifyP1(out, "Illegal attack target", 2);
            return false;
        }

        // attacker attack animation
        BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.attack);
        sleep(650);

        damageService.dealDamage(out, gameState, defender, attacker.getAttack());
        if (attacker.getAttack() > 0) {
            triggerSystem.onUnitDealtDamage(out, gameState, attacker, defender);
        }
        attacker.markAttacked(gameState.getGlobalTurnNumber());

        // defender dead after being attacked
        if (defender.isDead()) {
            sleep(350);

            if (defender instanceof structures.AvatarUnit) {
                gameEndChecker.checkAndHandle(out, gameState);
                BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.idle);
                return true;
            }

            unitRemovalService.removeUnit(out, gameState, defender, "Unit destroyed");
            BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.idle);
            return true;
        }

        // defender counterattacks
        BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.attack);
        sleep(650);

        damageService.dealDamage(out, gameState, attacker, defender.getAttack());
        if (defender.getAttack() > 0) {
            triggerSystem.onUnitDealtDamage(out, gameState, defender, attacker);
        }

        // attacker dead after counterattack
        if (attacker.isDead()) {
            sleep(350);

            if (attacker instanceof structures.AvatarUnit) {
                gameEndChecker.checkAndHandle(out, gameState);
                if (!defender.isDead()) {
                    sleep(250);
                    BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.idle);
                }
                return true;
            }

            unitRemovalService.removeUnit(out, gameState, attacker, "Unit destroyed");
            if (!defender.isDead()) {
                sleep(250);
                BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.idle);
            }
            return true;
        }

        // both survive
        sleep(250);
        BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.idle);
        BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.idle);
        return true;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean canAttackTarget(GameState gameState, UnitEntity attacker, UnitEntity defender) {
        if (gameState == null || attacker == null || defender == null) return false;
        if (attacker.isDead() || defender.isDead()) return false;
        if (attacker.getOwnerPlayerId() == defender.getOwnerPlayerId()) return false;
        if (attacker.getPosition() == null || defender.getPosition() == null) return false;

        int currentTurn = gameState.getGlobalTurnNumber();
        if (!attacker.canAttack(currentTurn)) return false;

        if (!isAdjacent8(attacker, defender)) return false;

        return !isAttackBlockedByProvoke(gameState, attacker, defender);
    }

    public Set<Position> computeLegalAttackTargets(GameState gameState, UnitEntity attacker) {
        Set<Position> result = new HashSet<>();
        if (gameState == null || attacker == null) return result;
        if (attacker.isDead() || attacker.getPosition() == null) return result;

        for (UnitEntity other : gameState.getUnitsById().values()) {
            if (other == null) continue;
            if (other.isDead()) continue;
            if (other.getPosition() == null) continue;

            if (canAttackTarget(gameState, attacker, other)) {
                result.add(copyPosition(other.getPosition()));
            }
        }

        return result;
    }

    private boolean isAttackBlockedByProvoke(GameState gameState, UnitEntity attacker, UnitEntity defender) {
        if (gameState == null || attacker == null || defender == null) return false;
        if (attacker.getPosition() == null || defender.getPosition() == null) return false;

        boolean hasAdjacentEnemyProvoke = false;

        for (UnitEntity other : gameState.getUnitsById().values()) {
            if (other == null) continue;
            if (other.isDead()) continue;
            if (other.getOwnerPlayerId() == attacker.getOwnerPlayerId()) continue;
            if (other.getPosition() == null) continue;

            boolean adjacent = isAdjacent8(attacker, other);

            if (!other.hasKeyword("PROVOKE")) continue;

            if (adjacent) {
                hasAdjacentEnemyProvoke = true;

                if (defender.getId() == other.getId()) {

                    return false;
                }
            }
        }

        return hasAdjacentEnemyProvoke;
    }
    private boolean isAdjacent8(UnitEntity a, UnitEntity b) {
        if (a == null || b == null) return false;
        if (a.getPosition() == null || b.getPosition() == null) return false;

        int ax = a.getPosition().getTilex();
        int ay = a.getPosition().getTiley();
        int bx = b.getPosition().getTilex();
        int by = b.getPosition().getTiley();

        int dx = Math.abs(ax - bx);
        int dy = Math.abs(ay - by);

        return dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0);
    }

    private Position copyPosition(Position p) {
        Position copy = new Position();
        copy.setTilex(p.getTilex());
        copy.setTiley(p.getTiley());
        return copy;
    }
}