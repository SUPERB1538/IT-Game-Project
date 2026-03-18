package ai;

import akka.actor.ActorRef;
import commands.BasicCommands;
import services.CombatResolver;
import services.EffectResolver;
import services.MovementService;
import services.SummonService;
import services.TriggerSystem;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * AI turn logic for Player 2.
 *
 * Behaviour:
 * 1) Play useful spells if available
 * 2) Play affordable creature cards while mana allows
 * 3) Move units toward good attacks / the enemy avatar
 * 4) Attack with all units when legal
 */
public class AIDecisionEngine {

    private static final int AI_PLAYER_ID = 2;
    private static final int HUMAN_PLAYER_ID = 1;

    private AIDecisionEngine() {}

    public static void playTurn(ActorRef out, GameState gameState) {
        if (out == null || gameState == null) return;
        if (gameState.isGameOver()) return;
        if (gameState.getCurrentPlayerId() != AI_PLAYER_ID) return;

        // Phase 1: play cards while mana allows
        int guard = 0;
        boolean playedSomething;
        do {
            playedSomething = false;

            if (tryCastBestSpell(out, gameState)) {
                playedSomething = true;
                pause(120);
            } else if (tryPlayBestCreature(out, gameState)) {
                playedSomething = true;
                pause(120);
            }

            guard++;
        } while (playedSomething && !gameState.isGameOver() && guard < 8);

        if (gameState.isGameOver()) return;

        // Phase 2: move + attack
        tryMoveAndAttackAll(out, gameState);
    }

    // =========================================================
    // CARD PLAY
    // =========================================================

    private static boolean tryCastBestSpell(ActorRef out, GameState gameState) {
        PlayerState ai = gameState.getP2State();
        if (ai == null || ai.getHand() == null) return false;

        Hand hand = ai.getHand();
        SpellChoice best = null;

        for (int slot = Hand.MIN_SLOT; slot <= Hand.MAX_SLOT; slot++) {
            CardInstance card = hand.getBySlot(slot);
            if (card == null) continue;
            if (!card.isSpellCard()) continue;
            if (card.getManaCost() > ai.getMana()) continue;

            SpellChoice current = evaluateSpellChoice(gameState, slot, card);
            if (current == null) continue;

            if (best == null || current.score > best.score) {
                best = current;
            }
        }

        if (best == null) return false;

        if (!ai.spendMana(best.card.getManaCost())) return false;
        BasicCommands.setPlayer2Mana(out, gameState.getPlayer2());

        UnitEntity targetUnit = null;
        if (gameState.getBoard() != null) {
            targetUnit = gameState.getBoard().getUnitAt(best.targetPos).orElse(null);
        }

        new EffectResolver().applySpell(out, gameState, best.card, targetUnit, best.targetPos);

        hand.removeFromSlot(best.slot);
        compactHandLeft(hand);

        return true;
    }

    private static SpellChoice evaluateSpellChoice(GameState gameState, int slot, CardInstance card) {
        String key = safeUpper(card.getCardKey());

        if (key.contains("TRUE_STRIKE")) {
            return chooseTrueStrikeTarget(gameState, slot, card);
        }

        if (key.contains("BEAM_SHOCK")) {
            return chooseBeamShockTarget(gameState, slot, card);
        }

        if (key.contains("SUNDROP_ELIXIR")) {
            return chooseSundropTarget(gameState, slot, card);
        }

        // Not currently using other spell types for AI
        return null;
    }

    private static SpellChoice chooseTrueStrikeTarget(GameState gameState, int slot, CardInstance card) {
        SpellChoice best = null;

        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u == null || u.isDead()) continue;
            if (u.getOwnerPlayerId() != HUMAN_PLAYER_ID) continue;
            if (u.getPosition() == null) continue;

            int score = 0;

            if (u instanceof AvatarUnit) {
                score = (u.getHealth() <= 2) ? 10000 : 120;
            } else {
                boolean kill = u.getHealth() <= 2;
                score = 100 + (u.getAttack() * 20) + (u.hasKeyword("PROVOKE") ? 40 : 0);
                if (kill) score += 400;
            }

            SpellChoice sc = new SpellChoice(slot, card, copyPosition(u.getPosition()), score);
            if (best == null || sc.score > best.score) {
                best = sc;
            }
        }

        return best;
    }

    private static SpellChoice chooseBeamShockTarget(GameState gameState, int slot, CardInstance card) {
        SpellChoice best = null;

        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u == null || u.isDead()) continue;
            if (u.getOwnerPlayerId() != HUMAN_PLAYER_ID) continue;
            if (u instanceof AvatarUnit) continue;
            if (u.getPosition() == null) continue;
            if (u.isStunned()) continue;

            int score = 100 + (u.getAttack() * 25) + (u.hasKeyword("PROVOKE") ? 50 : 0);
            SpellChoice sc = new SpellChoice(slot, card, copyPosition(u.getPosition()), score);

            if (best == null || sc.score > best.score) {
                best = sc;
            }
        }

        return best;
    }

    private static SpellChoice chooseSundropTarget(GameState gameState, int slot, CardInstance card) {
        SpellChoice best = null;

        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u == null || u.isDead()) continue;
            if (u.getOwnerPlayerId() != AI_PLAYER_ID) continue;
            if (u.getPosition() == null) continue;

            int missing = u.getMaxHealth() - u.getHealth();
            if (missing <= 0) continue;

            int score = missing * 20;

            if (u instanceof AvatarUnit) {
                score += 60;
                if (u.getHealth() <= 8) score += 100;
            } else {
                score += u.getAttack() * 10;
                if (u.hasKeyword("PROVOKE")) score += 30;
            }

            SpellChoice sc = new SpellChoice(slot, card, copyPosition(u.getPosition()), score);

            if (best == null || sc.score > best.score) {
                best = sc;
            }
        }

        return best;
    }

    private static boolean tryPlayBestCreature(ActorRef out, GameState gameState) {
        PlayerState ai = gameState.getP2State();
        if (ai == null || ai.getHand() == null) return false;

        Hand hand = ai.getHand();
        CreatureChoice best = null;

        for (int slot = Hand.MIN_SLOT; slot <= Hand.MAX_SLOT; slot++) {
            CardInstance ci = hand.getBySlot(slot);
            if (ci == null) continue;
            if (!ci.isCreatureCard()) continue;
            if (ci.getManaCost() > ai.getMana()) continue;

            Position summonPos = chooseBestSummonTile(gameState, ci);
            if (summonPos == null) continue;

            int score = scoreCreatureCard(ci);
            CreatureChoice choice = new CreatureChoice(slot, ci, summonPos, score);

            if (best == null || choice.score > best.score) {
                best = choice;
            }
        }

        if (best == null) return false;

        return summonCreature(out, gameState, best.slot, best.card, best.summonPos);
    }

    private static boolean summonCreature(ActorRef out, GameState gameState, int chosenSlot, CardInstance chosen, Position summonPos) {
        PlayerState ai = gameState.getP2State();
        if (ai == null || chosen == null || summonPos == null) return false;

        Tile summonTile = BasicObjectBuilders.loadTile(summonPos.getTilex(), summonPos.getTiley());

        if (!ai.spendMana(chosen.getManaCost())) return false;
        BasicCommands.setPlayer2Mana(out, gameState.getPlayer2());

        String unitConfig = resolveUnitConfig(chosen.getConfigFile(), chosen.getCardKey());
        if (unitConfig == null || !fileExists(unitConfig)) {
            ai.setMana(Math.min(9, ai.getMana() + chosen.getManaCost()));
            BasicCommands.setPlayer2Mana(out, gameState.getPlayer2());
            return false;
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
            return false;
        }

        int[] stats = creatureStats(chosen.getCardKey());
        summoned.setOwnerPlayerId(AI_PLAYER_ID);
        summoned.setMaxHealth(stats[1]);
        summoned.setHealth(stats[1]);
        summoned.setAttack(stats[0]);
        SummonService.applyKeywordsForUnit(summoned, chosen.getCardKey());

        summoned.setPositionByTile(summonTile);
        summoned.setSummonedOnTurn(gameState.getGlobalTurnNumber());

        gameState.getBoard().putUnit(summonPos, summoned);
        gameState.addUnit(summoned);

        pause(60);
        BasicCommands.playEffectAnimation(out, BasicObjectBuilders.loadEffect(StaticConfFiles.f1_summon), summonTile);
        pause(60);

        BasicCommands.drawUnit(out, summoned, summonTile);
        pause(60);
        BasicCommands.setUnitHealth(out, summoned, summoned.getHealth());
        BasicCommands.setUnitAttack(out, summoned, summoned.getAttack());

        // Fire summon triggers like human summoning does
        new TriggerSystem().onUnitSummoned(out, gameState, summoned);

        ai.getHand().removeFromSlot(chosenSlot);
        compactHandLeft(ai.getHand());

        return true;
    }

    private static Position chooseBestSummonTile(GameState gameState, CardInstance card) {
        Board board = gameState.getBoard();
        if (board == null) return null;

        List<UnitEntity> aiUnits = new ArrayList<>();
        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u != null && !u.isDead() && u.getOwnerPlayerId() == AI_PLAYER_ID && u.getPosition() != null) {
                aiUnits.add(u);
            }
        }

        UnitEntity enemyAvatar = gameState.getP1Avatar();
        Position avatarPos = (enemyAvatar != null) ? enemyAvatar.getPosition() : null;

        Position best = null;
        int bestScore = Integer.MIN_VALUE;

        for (UnitEntity u : aiUnits) {
            for (Position p : adjacent8(u.getPosition())) {
                if (!board.isValidPosition(p)) continue;
                if (board.isOccupied(p)) continue;

                int score = 0;

                if (avatarPos != null) {
                    score -= manhattan(p, avatarPos);
                }

                // Slightly prefer more central tiles
                score -= Math.abs(5 - p.getTilex());
                score -= Math.abs(3 - p.getTiley());

                String key = safeUpper(card.getCardKey());
                if (key.contains("PROVOKE") || key.contains("SILVERGUARD_KNIGHT") || key.contains("SWAMP_ENTANGLER") || key.contains("IRONCLIFFE_GUARDIAN")) {
                    score += 10;
                }

                if (score > bestScore) {
                    bestScore = score;
                    best = copyPosition(p);
                }
            }
        }

        return best;
    }

    private static int scoreCreatureCard(CardInstance ci) {
        if (ci == null) return Integer.MIN_VALUE;
        String k = safeUpper(ci.getCardKey());

        int[] stats = creatureStats(ci.getCardKey());
        int atk = stats[0];
        int hp = stats[1];

        int score = atk * 18 + hp * 12 - ci.getManaCost() * 4;

        if (k.contains("RUSH")) score += 35;
        if (k.contains("FLAMEWING")) score += 30;
        if (k.contains("PROVOKE") || k.contains("ENTANGLER") || k.contains("KNIGHT") || k.contains("IRONCLIFFE")) score += 25;
        if (k.contains("OPENING_GAMBIT")) score += 10;

        return score;
    }

    // =========================================================
    // MOVE + ATTACK
    // =========================================================

    private static void tryMoveAndAttackAll(ActorRef out, GameState gameState) {
        int currentTurn = gameState.getGlobalTurnNumber();
        CombatResolver combatResolver = new CombatResolver();
        MovementService movementService = new MovementService();

        List<UnitEntity> aiUnits = new ArrayList<>();
        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u != null && !u.isDead() && u.getOwnerPlayerId() == AI_PLAYER_ID) {
                aiUnits.add(u);
            }
        }

        aiUnits.sort(Comparator.comparingInt(UnitEntity::getAttack).reversed());

        for (UnitEntity attacker : aiUnits) {
            if (gameState.isGameOver()) return;
            if (attacker == null || attacker.isDead() || attacker.getPosition() == null) continue;

            // 1) Attack immediately if already legal
            if (attacker.canAttack(currentTurn)) {
                UnitEntity targetNow = chooseBestLegalTarget(gameState, combatResolver, attacker);
                if (targetNow != null) {
                    combatResolver.tryAttack(out, gameState, attacker, targetNow);
                    pause(120);
                    continue;
                }
            }

            // 2) Move if useful
            if (attacker.canMove(currentTurn)) {
                Position bestMove = chooseBestMove(gameState, movementService, attacker);
                if (bestMove != null) {
                    moveUnit(out, gameState, attacker, bestMove);
                    pause(120);
                }
            }

            // 3) Attack after move
            if (attacker.canAttack(currentTurn) && !attacker.isDead()) {
                UnitEntity targetAfterMove = chooseBestLegalTarget(gameState, combatResolver, attacker);
                if (targetAfterMove != null) {
                    combatResolver.tryAttack(out, gameState, attacker, targetAfterMove);
                    pause(120);
                }
            }
        }
    }

    private static Position chooseBestMove(GameState gameState, MovementService movementService, UnitEntity unit) {
        List<Position> legalMoves = movementService.computeMovesForUnit(gameState, unit);
        if (legalMoves.isEmpty()) return null;

        Position original = unit.getPosition();
        Position bestMove = null;
        int bestScore = Integer.MIN_VALUE;

        for (Position p : legalMoves) {
            int score = scoreMove(gameState, unit, original, p);
            if (score > bestScore) {
                bestScore = score;
                bestMove = copyPosition(p);
            }
        }

        return bestMove;
    }

    private static int scoreMove(GameState gameState, UnitEntity unit, Position from, Position to) {
        int score = 0;

        // Prefer moves that create a legal attack
        UnitEntity attackTarget = chooseBestTargetFromPosition(gameState, unit, to);
        if (attackTarget != null) {
            score += 300 + scoreTarget(unit, attackTarget);
        }

        // Otherwise move toward enemy avatar
        AvatarUnit enemyAvatar = gameState.getP1Avatar();
        if (enemyAvatar != null && enemyAvatar.getPosition() != null) {
            int before = manhattan(from, enemyAvatar.getPosition());
            int after = manhattan(to, enemyAvatar.getPosition());
            score += (before - after) * 20;
        }

        // Prefer central-ish board positions
        score -= Math.abs(5 - to.getTilex());
        score -= Math.abs(3 - to.getTiley());

        return score;
    }

    private static void moveUnit(ActorRef out, GameState gameState, UnitEntity unit, Position targetPos) {
        if (out == null || gameState == null || unit == null || targetPos == null) return;

        int t = gameState.getGlobalTurnNumber();
        if (!unit.canMove(t)) return;
        if (!gameState.getBoard().isValidPosition(targetPos)) return;
        if (gameState.getBoard().isOccupied(targetPos)) return;

        Position from = copyPosition(unit.getPosition());
        Tile targetTile = BasicObjectBuilders.loadTile(targetPos.getTilex(), targetPos.getTiley());

        gameState.getBoard().moveUnit(from, targetPos);
        unit.moveTo(targetTile);
        unit.markMoved(t);

        BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.move);
        BasicCommands.moveUnitToTile(out, unit, targetTile);
        BasicCommands.playUnitAnimation(out, unit, UnitAnimationType.idle);
    }

    // =========================================================
    // TARGET CHOICE
    // =========================================================

    private static UnitEntity chooseBestLegalTarget(GameState gameState, CombatResolver combatResolver, UnitEntity attacker) {
        UnitEntity best = null;
        int bestScore = Integer.MIN_VALUE;

        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u == null || u.isDead()) continue;
            if (u.getOwnerPlayerId() == attacker.getOwnerPlayerId()) continue;
            if (!combatResolver.canAttackTarget(gameState, attacker, u)) continue;

            int score = scoreTarget(attacker, u);
            if (score > bestScore) {
                bestScore = score;
                best = u;
            }
        }

        return best;
    }

    private static UnitEntity chooseBestTargetFromPosition(GameState gameState, UnitEntity attacker, Position fromPos) {
        UnitEntity best = null;
        int bestScore = Integer.MIN_VALUE;

        for (UnitEntity u : gameState.getUnitsById().values()) {
            if (u == null || u.isDead()) continue;
            if (u.getOwnerPlayerId() == attacker.getOwnerPlayerId()) continue;
            if (!canAttackFromPosition(gameState, attacker, u, fromPos)) continue;

            int score = scoreTarget(attacker, u);
            if (score > bestScore) {
                bestScore = score;
                best = u;
            }
        }

        return best;
    }

    private static int scoreTarget(UnitEntity attacker, UnitEntity target) {
        if (target == null) return Integer.MIN_VALUE;

        if (target instanceof AvatarUnit) {
            return (attacker.getAttack() >= target.getHealth()) ? 10000 : 250;
        }

        int score = 100;

        if (attacker.getAttack() >= target.getHealth()) score += 350;
        score += target.getAttack() * 20;
        score += (target.hasKeyword("PROVOKE") ? 50 : 0);
        score += (target.isStunned() ? -25 : 0);

        return score;
    }

    private static boolean canAttackFromPosition(GameState gameState, UnitEntity attacker, UnitEntity defender, Position fromPos) {
        if (gameState == null || attacker == null || defender == null || fromPos == null) return false;
        if (attacker.isDead() || defender.isDead()) return false;
        if (attacker.getOwnerPlayerId() == defender.getOwnerPlayerId()) return false;
        if (defender.getPosition() == null) return false;
        if (!attacker.canAttack(gameState.getGlobalTurnNumber())) return false;

        int dx = Math.abs(fromPos.getTilex() - defender.getPosition().getTilex());
        int dy = Math.abs(fromPos.getTiley() - defender.getPosition().getTiley());
        if (!(dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0))) return false;

        return !isAttackBlockedByProvokeAtPosition(gameState, attacker, defender, fromPos);
    }

    private static boolean isAttackBlockedByProvokeAtPosition(GameState gameState, UnitEntity attacker, UnitEntity defender, Position attackFrom) {
        boolean hasAdjacentEnemyProvoke = false;

        for (UnitEntity other : gameState.getUnitsById().values()) {
            if (other == null || other.isDead()) continue;
            if (other.getOwnerPlayerId() == attacker.getOwnerPlayerId()) continue;
            if (!other.hasKeyword("PROVOKE")) continue;
            if (other.getPosition() == null) continue;

            int dx = Math.abs(attackFrom.getTilex() - other.getPosition().getTilex());
            int dy = Math.abs(attackFrom.getTiley() - other.getPosition().getTiley());
            boolean adjacent = dx <= 1 && dy <= 1 && !(dx == 0 && dy == 0);

            if (!adjacent) continue;

            hasAdjacentEnemyProvoke = true;
            if (defender.getId() == other.getId()) {
                return false;
            }
        }

        return hasAdjacentEnemyProvoke;
    }

    // =========================================================
    // HELPERS
    // =========================================================

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

    private static Position copyPosition(Position p) {
        if (p == null) return null;
        Position copy = new Position();
        copy.setTilex(p.getTilex());
        copy.setTiley(p.getTiley());
        return copy;
    }

    private static int manhattan(Position a, Position b) {
        return Math.abs(a.getTilex() - b.getTilex()) + Math.abs(a.getTiley() - b.getTiley());
    }

    private static String safeUpper(String s) {
        return (s == null) ? "" : s.toUpperCase();
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

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception ignored) {
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

    // =========================================================
    // SMALL INTERNAL TYPES
    // =========================================================

    private static class SpellChoice {
        final int slot;
        final CardInstance card;
        final Position targetPos;
        final int score;

        SpellChoice(int slot, CardInstance card, Position targetPos, int score) {
            this.slot = slot;
            this.card = card;
            this.targetPos = targetPos;
            this.score = score;
        }
    }

    private static class CreatureChoice {
        final int slot;
        final CardInstance card;
        final Position summonPos;
        final int score;

        CreatureChoice(int slot, CardInstance card, Position summonPos, int score) {
            this.slot = slot;
            this.card = card;
            this.summonPos = summonPos;
            this.score = score;
        }
    }
}