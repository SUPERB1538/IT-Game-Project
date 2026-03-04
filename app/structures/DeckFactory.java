package structures;

import java.util.ArrayList;
import java.util.List;

import structures.basic.Card;
import utils.BasicObjectBuilders;

public final class DeckFactory {

    private static int NEXT_CARD_ID = 1;

    private static int nextCardId() {
        return NEXT_CARD_ID++;
    }

    // Human deck (Abyssian Swarm)
    public static Deck buildHumanDeck(GameState state) {
        List<CardInstance> list = new ArrayList<>();

        // Units (Abyssian)
        addTwo(list, "BAD_OMEN", "conf/gameconfs/cards/1_1_c_u_bad_omen.json");
        addTwo(list, "GLOOM_CHASER", "conf/gameconfs/cards/1_3_c_u_gloom_chaser.json");
        addTwo(list, "SHADOW_WATCHER", "conf/gameconfs/cards/1_4_c_u_shadow_watcher.json");
        addTwo(list, "NIGHTSORROW_ASSASSIN", "conf/gameconfs/cards/1_6_c_u_nightsorrow_assassin.json");
        addTwo(list, "ROCK_PULVERISER", "conf/gameconfs/cards/1_7_c_u_rock_pulveriser.json");
        addTwo(list, "BLOODMOON_PRIESTESS", "conf/gameconfs/cards/1_9_c_u_bloodmoon_priestess.json");
        addTwo(list, "SHADOWDANCER", "conf/gameconfs/cards/1_a1_c_u_shadowdancer.json");

        // Spells
        addTwo(list, "HORN_OF_THE_FORSAKEN", "conf/gameconfs/cards/1_2_c_s_hornoftheforsaken.json");
        addTwo(list, "WRAITHLING_SWARM", "conf/gameconfs/cards/1_5_c_s_wraithling_swarm.json");
        addTwo(list, "DARK_TERMINUS", "conf/gameconfs/cards/1_8_c_s_dark_terminus.json");

        return new Deck(list);
    }

    // AI deck (Lyonar Generalist) - 2025-26
    public static Deck buildAIDeck(GameState state) {
        List<CardInstance> list = new ArrayList<>();

        // Units (Lyonar)
        addTwo(list, "SKYROCK_GOLEM", "conf/gameconfs/cards/2_1_c_u_skyrock_golem.json");
        addTwo(list, "SWAMP_ENTANGLER", "conf/gameconfs/cards/2_2_c_u_swamp_entangler.json");
        addTwo(list, "SILVERGUARD_KNIGHT", "conf/gameconfs/cards/2_3_c_u_silverguard_knight.json");
        addTwo(list, "SABERSPINE_TIGER", "conf/gameconfs/cards/2_4_c_u_saberspine_tiger.json");
        addTwo(list, "YOUNG_FLAMEWING", "conf/gameconfs/cards/2_6_c_u_young_flamewing.json");
        addTwo(list, "SILVERGUARD_SQUIRE", "conf/gameconfs/cards/2_7_c_u_silverguard_squire.json");
        addTwo(list, "IRONCLIFF_GUARDIAN", "conf/gameconfs/cards/2_8_c_u_ironcliff_guardian.json");

        // Spells
        addTwo(list, "BEAM_SHOCK", "conf/gameconfs/cards/2_5_c_s_beamshock.json");
        addTwo(list, "SUNDROP_ELIXIR", "conf/gameconfs/cards/2_9_c_s_sundrop_elixir.json");
        addTwo(list, "TRUE_STRIKE", "conf/gameconfs/cards/2_a1_c_s_truestrike.json");

        return new Deck(list);
    }


    private static void addTwo(List<CardInstance> list, String key, String configPath) {
        CardInstance c1 = buildCard(key, configPath);
        CardInstance c2 = buildCard(key, configPath);

        if (c1 != null) list.add(c1);
        if (c2 != null) list.add(c2);
    }

    private static CardInstance buildCard(String key, String configPath) {
        try {
            Card visual = BasicObjectBuilders.loadCard(configPath, nextCardId(), Card.class);

            if (visual == null) {
                System.err.println("[DeckFactory] loadCard returned null: " + configPath);
                return null;
            }

            int mana = visual.getManacost();

            return new CardInstance(key, mana, configPath, visual);

        } catch (Exception e) {
            System.err.println("[DeckFactory] Failed to load card config: " + configPath);
            e.printStackTrace();
            return null;
        }
    }
}