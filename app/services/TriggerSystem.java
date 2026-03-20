package services;

import akka.actor.ActorRef;
import structures.AvatarUnit;
import structures.GameState;
import structures.UnitEntity;
import structures.basic.Position;
import commands.BasicCommands;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles all trigger-based game events such as Opening Gambit,
 * Deathwatch, On Hit, and Zeal effects.
 */
public class TriggerSystem {

    private final PersistentEffectManager persistentEffectManager = new PersistentEffectManager();

    public void onUnitSummoned(ActorRef out, GameState gameState, UnitEntity unit) {
        if (out == null || gameState == null || unit == null) return;
        if (!unit.hasKeyword("OPENING_GAMBIT")) return;

        handleOpeningGambit(out, gameState, unit);
    }

    public void onUnitDied(ActorRef out, GameState gameState, UnitEntity deadUnit) {
        if (out == null || gameState == null || deadUnit == null) return;

        List<UnitEntity> snapshot = new ArrayList<>(gameState.getUnitsById().values());

        for (UnitEntity unit : snapshot) {
            if (unit == null) continue;
            if (unit.isDead()) continue;
            if (!unit.hasKeyword("DEATHWATCH")) continue;

            handleDeathwatch(out, gameState, unit, deadUnit);
        }
    }

    public void onUnitDealtDamage(ActorRef out, GameState gameState, UnitEntity attacker, UnitEntity target) {
        if (out == null || gameState == null || attacker == null || target == null) return;

        handleOnHit(out, gameState, attacker, target);
    }

    public void onAvatarDamaged(ActorRef out, GameState gameState, AvatarUnit avatar) {
        if (out == null || gameState == null || avatar == null) return;

        int ownerId = avatar.getOwnerPlayerId();
        List<UnitEntity> snapshot = new ArrayList<>(gameState.getUnitsById().values());

        for (UnitEntity unit : snapshot) {
            if (unit == null) continue;
            if (unit.isDead()) continue;
            if (unit.getOwnerPlayerId() != ownerId) continue;
            if (!unit.hasKeyword("ZEAL")) continue;

            persistentEffectManager.applyZealBuff(unit);
        }
    }

    private void handleOpeningGambit(ActorRef out, GameState gameState, UnitEntity unit) {

        // 1) Gloom Chaser
        if (unit.hasKeyword("GLOOM_CHASER_OG")) {
            Position summonPos = getBehindPosition(unit);
            if (summonPos != null
                    && gameState.getBoard().isValidPosition(summonPos)
                    && !gameState.getBoard().isOccupied(summonPos)) {

                SummonService summonService = new SummonService();
                summonService.summonToken(
                        out,
                        gameState,
                        "conf/gameconfs/units/wraithling.json",
                        summonPos,
                        unit.getOwnerPlayerId()
                );               
            }
        }

        // 2) Nightsorrow Assassin
        if (unit.hasKeyword("NIGHTSORROW_ASSASSIN_OG")) {
            UnitEntity target = findAdjacentDamagedEnemy(unit, gameState);
            if (target != null) {
                new UnitRemovalService().removeUnit(out, gameState, target, "Unit destroyed");
            }
        }
    }

    private void handleDeathwatch(ActorRef out, GameState gameState, UnitEntity watcher, UnitEntity deadUnit) {

        // 3) Bad Omen
    	if (watcher.hasKeyword("BAD_OMEN_DW")) {
    	    persistentEffectManager.applyPermanentAttackBuff(watcher, 1);
    	    BasicCommands.setUnitAttack(out, watcher, watcher.getAttack());
    	}

        // 4) Shadow Watcher
    	if (watcher.hasKeyword("SHADOW_WATCHER_DW")) {
    	    persistentEffectManager.applyPermanentAttackAndHealthBuff(watcher, 1, 1);
    	    BasicCommands.setUnitAttack(out, watcher, watcher.getAttack());
    	    BasicCommands.setUnitHealth(out, watcher, watcher.getHealth());
    	}
        // 5) Bloodmoon Priestess
        if (watcher.hasKeyword("BLOODMOON_PRIESTESS_DW")) {
            Position summonPos = findRandomAdjacentEmpty(gameState, watcher.getPosition());
            if (summonPos != null) {
                SummonService summonService = new SummonService();
                summonService.summonToken(
                        out,
                        gameState,
                        "conf/gameconfs/units/wraithling.json",
                        summonPos,
                        watcher.getOwnerPlayerId()
                );
            }
        }

        // 6) Shadowdancer
        if (watcher.hasKeyword("SHADOWDANCER_DW")) {
            AvatarUnit enemyAvatar = (watcher.getOwnerPlayerId() == 1)
                    ? gameState.getP2Avatar()
                    : gameState.getP1Avatar();

            AvatarUnit ownAvatar = (watcher.getOwnerPlayerId() == 1)
                    ? gameState.getP1Avatar()
                    : gameState.getP2Avatar();

            DamageService damageService = new DamageService();

            if (ownAvatar != null) {
                damageService.healUnit(out, gameState, ownAvatar, 1);
            }

            if (enemyAvatar != null) {
                damageService.dealDamage(out, gameState, enemyAvatar, 1);
            }
        }
    }

    private void handleOnHit(ActorRef out, GameState gameState, UnitEntity attacker, UnitEntity target) {
        if (attacker == null || target == null) return;

        // 7) Horn of the Forsaken
        if (attacker instanceof AvatarUnit && attacker.hasHornOfForsaken()) {
            Position summonPos = findRandomAdjacentEmpty(gameState, attacker.getPosition());
            if (summonPos != null) {
                SummonService summonService = new SummonService();
                summonService.summonToken(
                        out,
                        gameState,
                        "conf/gameconfs/units/wraithling.json",
                        summonPos,
                        attacker.getOwnerPlayerId()
                );
            }
        }
    }

    private Position getBehindPosition(UnitEntity unit) {
        if (unit == null || unit.getPosition() == null) return null;

        int x = unit.getPosition().getTilex();
        int y = unit.getPosition().getTiley();

        Position p = new Position();

        if (unit.getOwnerPlayerId() == 1) {
            // Human player: behind = left
            p.setTilex(x + 1);
            p.setTiley(y);
        } else {
            // AI player: behind = right
            p.setTilex(x - 1);
            p.setTiley(y);
        }

        return p;
    }

    private UnitEntity findAdjacentDamagedEnemy(UnitEntity source, GameState gameState) {
        if (source == null || source.getPosition() == null) return null;

        int sx = source.getPosition().getTilex();
        int sy = source.getPosition().getTiley();

        for (UnitEntity other : gameState.getUnitsById().values()) {
            if (other == null) continue;
            if (other.isDead()) continue;
            if (other.getOwnerPlayerId() == source.getOwnerPlayerId()) continue;
            if (other.getPosition() == null) continue;
            if (other.getHealth() >= other.getMaxHealth()) continue;

            int ox = other.getPosition().getTilex();
            int oy = other.getPosition().getTiley();

            int dx = Math.abs(sx - ox);
            int dy = Math.abs(sy - oy);

            boolean adjacent = (dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0));
            if (adjacent) return other;
        }

        return null;
    }

    private Position findRandomAdjacentEmpty(GameState gameState, Position center) {
        if (gameState == null || center == null || gameState.getBoard() == null) return null;

        List<Position> candidates = new ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                Position p = new Position();
                p.setTilex(center.getTilex() + dx);
                p.setTiley(center.getTiley() + dy);

                if (!gameState.getBoard().isValidPosition(p)) continue;
                if (gameState.getBoard().isOccupied(p)) continue;

                candidates.add(p);
            }
        }

        if (candidates.isEmpty()) return null;

        Collections.shuffle(candidates);
        return candidates.get(0);
    }
}