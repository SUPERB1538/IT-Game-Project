package ai;

import akka.actor.ActorRef;
import commands.BasicCommands;
import services.CombatResolver;
import services.DamageService;
import services.SummonService;
import structures.*;
import structures.basic.Position;
import structures.basic.Tile;
import structures.basic.UnitAnimationType;
import utils.BasicObjectBuilders;
import utils.StaticConfFiles;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal AI:
 * 1) Play 1 affordable creature card if possible
 * 2) Attack with all possible units
 */
public class AIDecisionEngine {

    private AIDecisionEngine() {}

    public static void playTurn(ActorRef out, GameState gameState) {
        if (gameState == null) return;
        if (gameState.getCurrentPlayerId() != 2) return;

        // Try to play one creature first
        tryPlayOneCreature(out, gameState);

        // Then attack with all AI units
        tryAttackAll(out, gameState);
    }

    // ----------------------------
    // Play one creature
    // ----------------------------

    private static void tryPlayOneCreature(ActorRef out, GameState gameState) {
        PlayerState ai = gameState.getP2State();
        if (ai == null || ai.getHand() == null) return;

        Hand hand = ai.getHand();

        // Choose the first affordable creature card
        int chosenSlot = -1;
        CardInstance chosen = null;
        for (int slot = Hand.MIN_SLOT; slot <= Hand.MAX_SLOT; slot++) {
            CardInstance ci = hand.getBySlot(slot);
            if (ci == null) continue;
            if (!isCreatureCard(ci)) continue;
            if (ci.getManaCost() > ai.getMana()) continue;

            chosenSlot = slot;
            chosen = ci;
            break;
        }

        if (chosen == null) return;

        // Find a valid summon tile adjacent to any AI unit
        Position summonPos = findSummonTileAdjacentToAnyAIUnit(gameState);
        if (summonPos == null) return;

        Tile summonTile = BasicObjectBuilders.loadTile(summonPos.getTilex(), summonPos.getTiley());

        // Spend mana
        if (!ai.spendMana(chosen.getManaCost())) return;
        BasicCommands.setPlayer2Mana(out, gameState.getPlayer2());

        // Resolve unit config
        String unitConfig = resolveUnitConfig(chosen.getConfigFile(), chosen.getCardKey());
        if (unitConfig == null || !fileExists(unitConfig)) {
            ai.setMana(Math.min(9, ai.getMana() + chosen.getManaCost()));
            BasicCommands.setPlayer2Mana(out, gameState.getPlayer2());
            return;
        }

        UnitEntity summoned;
        try {
            summoned = (UnitEntity) BasicObjectBuilders.loadUnit(unitConfig, gameState.nextUnitId(), UnitEntity.class);
        } catch (Exception e) {
            summoned = null;
        }

        if (summoned == null) {
            ai.setMana(Math.min(9, ai.getMana() + chosen.getManaCost()));
            BasicCommands.setPlayer2Mana(out, gameState.getPlayer2());
            return;
        }

        // Apply stats
        int[] stats = creatureStats(chosen.getCardKey());
        int atk = stats[0];
        int hp = stats[1];

        summoned.setOwnerPlayerId(2);
        summoned.setMaxHealth(hp);
        summoned.setHealth(hp);
        summoned.setAttack(atk);

        // Apply gameplay keywords such as PROVOKE, RUSH, ZEAL, FLYING
        SummonService.applyKeywordsForUnit(summoned, chosen.getCardKey());

        summoned.setPositionByTile(summonTile);
        summoned.setSummonedOnTurn(gameState.getGlobalTurnNumber());

        // Register unit in backend state
        gameState.getBoard().putUnit(summonPos, summoned);
        gameState.addUnit(summoned);

        // Draw summon effect and unit
        try { Thread.sleep(60); } catch (Exception ignored) {}
        BasicCommands.playEffectAnimation(out, BasicObjectBuilders.loadEffect(StaticConfFiles.f1_summon), summonTile);
        try { Thread.sleep(60); } catch (Exception ignored) {}

        BasicCommands.drawUnit(out, summoned, summonTile);
        try { Thread.sleep(60); } catch (Exception ignored) {}
        BasicCommands.setUnitHealth(out, summoned, summoned.getHealth());
        BasicCommands.setUnitAttack(out, summoned, summoned.getAttack());

        // Remove the played card from AI hand
        hand.removeFromSlot(chosenSlot);
        compactHandLeft(hand);
    }

    private static Position findSummonTileAdjacentToAnyAIUnit(GameState gameState) {
        Board board = gameState.getBoard();
        if (board == null) return null;

        List<UnitEntity> aiUnits = new ArrayList<>();
        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u != null && u.getOwnerPlayerId() == 2) {
                aiUnits.add(u);
            }
        }

        for (UnitEntity u : aiUnits) {
            Position p = u.getPosition();
            if (p == null) continue;

            List<Position> adj = adjacent8(p);
            for (Position t : adj) {
                if (!board.isValidPosition(t)) continue;
                if (board.isOccupied(t)) continue;
                return t;
            }
        }

        return null;
    }

    // ----------------------------
    // Attack
    // ----------------------------

    private static void tryAttackAll(ActorRef out, GameState gameState) {
        int t = gameState.getGlobalTurnNumber();

        List<UnitEntity> aiUnits = new ArrayList<>();
        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u != null && u.getOwnerPlayerId() == 2) {
                aiUnits.add(u);
            }
        }

        aiUnits.sort((a, b) -> Integer.compare(b.getAttack(), a.getAttack()));

        CombatResolver combatResolver = new CombatResolver();

        for (UnitEntity attacker : aiUnits) {
            if (attacker == null) continue;
            if (attacker.isDead()) continue;
            if (!attacker.canAttack(t)) continue;

            UnitEntity target = chooseBestAdjacentTarget(gameState, attacker);
            if (target == null) continue;

            combatResolver.tryAttack(out, gameState, attacker, target);
        }
    }

    private static UnitEntity chooseBestAdjacentTarget(GameState gameState, UnitEntity attacker) {
        CombatResolver combatResolver = new CombatResolver();
        UnitEntity best = null;

        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u == null) continue;
            if (u.isDead()) continue;
            if (u.getOwnerPlayerId() == attacker.getOwnerPlayerId()) continue;

            // Only consider targets that are legal under the shared combat rules
            if (!combatResolver.canAttackTarget(gameState, attacker, u)) continue;

            // Prefer the enemy avatar first
            AvatarUnit p1Avatar = gameState.getP1Avatar();
            if (p1Avatar != null && u.getId() == p1Avatar.getId()) {
                return u;
            }

            // Otherwise choose the lowest-health legal target
            if (best == null || u.getHealth() < best.getHealth()) {
                best = u;
            }
        }

        return best;
    }

    // ----------------------------
    // Legacy attack helpers
    // Kept for compatibility, but no longer used by tryAttackAll
    // ----------------------------

    private static void resolveAttack(ActorRef out, GameState gameState, UnitEntity attacker, UnitEntity defender, int t) {
        if (attacker == null || defender == null) return;

        DamageService damageService = new DamageService();

        BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.attack);

        damageService.dealDamage(out, gameState, defender, attacker.getAttack());
        attacker.markAttacked(t);

        if (gameState.isGameOver()) return;

        if (defender.isDead()) {
            killUnit(out, gameState, defender);
            BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.idle);
            return;
        }

        BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.attack);
        damageService.dealDamage(out, gameState, attacker, defender.getAttack());

        if (gameState.isGameOver()) return;

        if (attacker.isDead()) {
            killUnit(out, gameState, attacker);
            BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.idle);
            return;
        }

        BasicCommands.playUnitAnimation(out, attacker, UnitAnimationType.idle);
        BasicCommands.playUnitAnimation(out, defender, UnitAnimationType.idle);
    }

    private static void killUnit(ActorRef out, GameState gameState, UnitEntity dead) {
        if (dead == null) return;
        BasicCommands.playUnitAnimation(out, dead, UnitAnimationType.death);
        BasicCommands.deleteUnit(out, dead);
        gameState.getBoard().removeUnit(dead.getPosition());
        gameState.removeUnitById(dead.getId());
    }

    private static void syncAvatarHP(ActorRef out, GameState gameState, UnitEntity unit) {
        AvatarUnit p1 = gameState.getP1Avatar();
        AvatarUnit p2 = gameState.getP2Avatar();

        if (p1 != null && unit.getId() == p1.getId()) {
            gameState.getPlayer1().setHealth(unit.getHealth());
            BasicCommands.setPlayer1Health(out, gameState.getPlayer1());
            if (unit.getHealth() <= 0) BasicCommands.addPlayer1Notification(out, "You lose!", 3);
        }

        if (p2 != null && unit.getId() == p2.getId()) {
            gameState.getPlayer2().setHealth(unit.getHealth());
            BasicCommands.setPlayer2Health(out, gameState.getPlayer2());
            if (unit.getHealth() <= 0) BasicCommands.addPlayer1Notification(out, "You win!", 3);
        }
    }

    // ----------------------------
    // Utility helpers
    // ----------------------------

    private static List<Position> adjacent8(Position from) {
        int x = from.getTilex();
        int y = from.getTiley();

        List<Position> ps = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                ps.add(tilePos(x + dx, y + dy));
            }
        }
        return ps;
    }

    private static Position tilePos(int tilex, int tiley) {
        Position p = new Position();
        p.setTilex(tilex);
        p.setTiley(tiley);
        return p;
    }

    private static boolean isCreatureCard(CardInstance card) {
        return card != null && card.isCreatureCard();
    }

    private static void compactHandLeft(Hand hand) {
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

    private static int[] creatureStats(String cardKey) {
        if (cardKey == null) return new int[]{1, 1};
        String k = cardKey.toLowerCase();

        // Human deck
        if (k.contains("bad_omen")) return new int[]{0, 1};
        if (k.contains("gloom_chaser")) return new int[]{3, 1};
        if (k.contains("shadow_watcher")) return new int[]{3, 2};
        if (k.contains("nightsorrow_assassin")) return new int[]{4, 2};
        if (k.contains("rock_pulveriser")) return new int[]{1, 4};
        if (k.contains("bloodmoon_priestess")) return new int[]{3, 3};
        if (k.contains("shadowdancer")) return new int[]{5, 4};

        // AI deck
        if (k.contains("swamp_entangler")) return new int[]{0, 3};
        if (k.contains("silverguard_squire")) return new int[]{1, 1};
        if (k.contains("skyrock_golem")) return new int[]{4, 2};
        if (k.contains("saberspine_tiger")) return new int[]{3, 2};
        if (k.contains("silverguard_knight")) return new int[]{1, 5};
        if (k.contains("young_flamewing")) return new int[]{5, 4};
        if (k.contains("ironcliffe_guardian")) return new int[]{3, 10};

        return new int[]{1, 1};
    }

    private static String resolveUnitConfig(String cardConfigPath, String cardKey) {
        String derived = null;

        if (cardConfigPath != null) {
            derived = cardConfigPath.replace("/cards/", "/units/")
                    .replace("\\cards\\", "\\units\\")
                    .replace("_c_u_", "_u_");

            if (fileExists(derived)) return derived;
        }

        String token = extractToken(cardConfigPath, cardKey);
        if (token == null || token.isEmpty()) return derived;

        String unitsDir = "conf/gameconfs/units";
        File dir = new File(unitsDir);
        if (!dir.exists() || !dir.isDirectory()) return derived;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json") && name.contains(token));
        if (files != null && files.length > 0) {
            return unitsDir + "/" + files[0].getName();
        }

        return derived;
    }

    private static boolean fileExists(String pathStr) {
        if (pathStr == null) return false;
        try {
            Path p = Paths.get(pathStr);
            return Files.exists(p);
        } catch (Exception e) {
            return false;
        }
    }

    private static String extractToken(String cardConfigPath, String cardKey) {
        String source = (cardConfigPath != null) ? cardConfigPath : cardKey;
        if (source == null) return null;

        String s = source;
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) s = s.substring(slash + 1);
        if (s.endsWith(".json")) s = s.substring(0, s.length() - 5);

        s = s.replace("c_u_", "");
        s = s.replace("u_", "");

        String[] parts = s.split("_");
        if (parts.length >= 3 && isNumeric(parts[0]) && isNumeric(parts[1])) {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                if (sb.length() > 0) sb.append("_");
                sb.append(parts[i]);
            }
            s = sb.toString();
        }

        return s;
    }

    private static boolean isNumeric(String x) {
        if (x == null || x.isEmpty()) return false;
        for (int i = 0; i < x.length(); i++) {
            if (!Character.isDigit(x.charAt(i))) return false;
        }
        return true;
    }
}