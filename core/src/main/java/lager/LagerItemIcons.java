package lager;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.serializer.legacy.LegacyComponentSerializer;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.world.item.ItemStack;
import net.labymod.api.client.world.item.ItemStackFactory;
import net.labymod.api.client.world.item.VanillaItem;
import net.labymod.api.client.world.item.VanillaItems;
import net.labymod.api.util.I18n;

/**
 * Löst freitextliche Itemnamen zu Minecraft-ItemStacks auf.
 * Indexiert alle Vanilla-Items der aktuellen Version inkl. lokalisierter Namen
 * (DE/EN über item.minecraft.* / block.minecraft.*).
 */
public final class LagerItemIcons {

  private static final Map<String, VanillaItem> BY_PATH = new HashMap<>();
  private static final Map<String, String> LOOKUP = new HashMap<>();
  private static final Map<String, Integer> LOOKUP_PRIORITY = new HashMap<>();
  private static final Map<String, ItemStack> CACHE = new HashMap<>();

  private static volatile boolean pathIndexReady;
  private static volatile boolean translationIndexReady;

  private LagerItemIcons() {
  }

  public static ItemStack resolve(String rawName) {
    ensureIndex();
    String key = LagerEntry.normalizeKey(rawName);
    if (key.isEmpty()) {
      return placeholder();
    }

    ItemStack cached = CACHE.get(key);
    if (cached != null) {
      return cached;
    }

    ItemStack resolved = resolveUncached(key);
    CACHE.put(key, resolved);
    return resolved;
  }

  private static void ensureIndex() {
    if (!pathIndexReady) {
      synchronized (LagerItemIcons.class) {
        if (!pathIndexReady) {
          buildPathIndex();
          registerManualAliases();
          // Graham (1.12-Liste) + moderne Vanilla-Liste inkl. DE-Kurzformen
          LagerGrahamItemAliases.register(LagerItemIcons::alias);
          LagerModernItemAliases.register(LagerItemIcons::alias);
          pathIndexReady = true;
        }
      }
    }
    // Übersetzungen erst wenn Language geladen ist – ggf. mehrfach versuchen
    if (!translationIndexReady) {
      synchronized (LagerItemIcons.class) {
        if (!translationIndexReady) {
          int added = indexLocalizedNames();
          if (added >= 50) {
            translationIndexReady = true;
            CACHE.clear();
          }
        }
      }
    }
  }

  private static void buildPathIndex() {
    // Alle für die aktuelle Version verfügbaren Vanilla-Items
    List<VanillaItem> items = VanillaItems.findItems(VanillaItem::isAvailable);
    if (items.isEmpty()) {
      items = VanillaItems.findItems(item -> true);
    }

    for (VanillaItem item : items) {
      ResourceLocation id = item.identifier();
      if (id == null) {
        continue;
      }
      String namespace = id.getNamespace();
      String path = id.getPath();
      if (path == null || path.isEmpty() || "air".equals(path)) {
        continue;
      }
      // Fokus Minecraft; andere Namespaces trotzdem per ID erreichbar
      if ("minecraft".equals(namespace)) {
        BY_PATH.put(path, item);
      }

      int prio = priorityFor(path);
      putLookup(path, path, prio);
      putLookup(path.replace("_", ""), path, prio);
      putLookup(namespace + ":" + path, path, prio + 5);

      String shortName = stripKnownSuffix(path);
      if (shortName != null && !shortName.equals(path)) {
        putLookup(shortName, path, prio);
        putLookup(shortName.replace("_", ""), path, prio);
      }
    }
  }

  /**
   * Indexiert Anzeigenamen aus der aktiven Minecraft-Sprache
   * (z.B. "Goldbarren", "Diamond Sword", …).
   *
   * @return Anzahl neu indexierter Namen
   */
  private static int indexLocalizedNames() {
    int added = 0;
    ItemStackFactory factory = null;
    try {
      factory = Laby.references().itemStackFactory();
    } catch (Exception ignored) {
    }

    for (Map.Entry<String, VanillaItem> entry : BY_PATH.entrySet()) {
      String path = entry.getKey();
      int prio = 185;

      // Aktive Spiele-Sprache (DE oder EN) über I18n
      if (indexTranslationKey("item.minecraft." + path, path, prio)) {
        added++;
      }
      if (indexTranslationKey("block.minecraft." + path, path, prio)) {
        added++;
      }
      // Ältere / alternative Keys
      if (indexTranslationKey("item.minecraft." + path + ".name", path, prio - 5)) {
        added++;
      }
      if (indexTranslationKey("tile.minecraft." + path + ".name", path, prio - 5)) {
        added++;
      }
      // EN-Anzeigename aus Registry-Pfad (z.B. gold_ingot → "Gold Ingot")
      if (indexNormalizedName(pathToEnglishDisplay(path), path, 160)) {
        added++;
      }

      // Fallback: DisplayName vom ItemStack (falls i18n-Key anders lautet)
      if (factory != null) {
        try {
          ItemStack stack = factory.create(entry.getValue());
          if (isValid(stack)) {
            Component component = stack.getDisplayName();
            if (component != null) {
              String plain = LegacyComponentSerializer.legacySection().serialize(component);
              plain = stripFormatting(plain);
              if (indexNormalizedName(plain, path, 175)) {
                added++;
              }
            }
          }
        } catch (Exception ignored) {
        }
      }
    }
    return added;
  }

  private static boolean indexTranslationKey(String key, String path, int priority) {
    try {
      if (!I18n.has(key)) {
        return false;
      }
      String translation = I18n.getTranslation(key);
      if (translation == null || translation.isEmpty() || translation.equals(key)) {
        return false;
      }
      return indexNormalizedName(translation, path, priority);
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean indexNormalizedName(String name, String path, int priority) {
    String normalized = normalizeLookup(name);
    if (normalized.isEmpty() || normalized.equals(path)) {
      return false;
    }
    boolean wasNew = !LOOKUP.containsKey(normalized);
    putLookup(normalized, path, priority);
    putLookup(normalized.replace("_", ""), path, priority);
    // auch Variante mit Leerzeichen entfernt bereits in normalizeLookup
    return wasNew;
  }

  /** gold_ingot → "Gold Ingot" für EN-Freitext-Suche */
  private static String pathToEnglishDisplay(String path) {
    if (path == null || path.isEmpty()) {
      return "";
    }
    String[] parts = path.split("_");
    StringBuilder sb = new StringBuilder(path.length() + 8);
    for (String part : parts) {
      if (part.isEmpty()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        sb.append(part.substring(1));
      }
    }
    return sb.toString();
  }

  private static String normalizeLookup(String value) {
    if (value == null) {
      return "";
    }
    String s = stripFormatting(value).toLowerCase(Locale.ROOT).trim();
    // Deutsche Umlaute vor dem Strippen umschreiben, sonst gehen Treffer verloren
    s = s
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
        .replace("æ", "ae")
        .replace("ø", "oe");
    s = s.replace(' ', '_').replace('-', '_');
    // Klammern/Sonderzeichen aus Anzeigenamen entfernen
    s = s.replaceAll("[^a-z0-9_:]", "");
    return s;
  }

  private static String stripFormatting(String value) {
    if (value == null) {
      return "";
    }
    // §-Farbcodes und Ampersand-Codes
    return value.replaceAll("§.", "").replaceAll("&[0-9a-fk-or]", "");
  }

  private static int priorityFor(String path) {
    if (path.endsWith("_ingot")) {
      return 100;
    }
    if (!path.contains("_")) {
      return 90;
    }
    if (path.endsWith("_block")) {
      return 70;
    }
    if (path.endsWith("_ore")) {
      return 60;
    }
    if (path.endsWith("_nugget")) {
      return 40;
    }
    if (path.endsWith("_log") || path.endsWith("_planks")) {
      return 50;
    }
    return 30;
  }

  private static String stripKnownSuffix(String path) {
    String[] suffixes = {
        "_ingot", "_nugget", "_block", "_ore", "_log", "_planks", "_slab", "_stairs",
        "_wall", "_fence", "_door", "_trapdoor", "_button", "_pressure_plate",
        "_boat", "_chest_boat", "_sign", "_hanging_sign", "_sapling", "_leaves",
        "_spawn_egg", "_bucket", "_sword", "_pickaxe", "_axe", "_shovel", "_hoe",
        "_helmet", "_chestplate", "_leggings", "_boots", "_horse_armor"
    };
    for (String suffix : suffixes) {
      if (path.endsWith(suffix) && path.length() > suffix.length()) {
        return path.substring(0, path.length() - suffix.length());
      }
    }
    return null;
  }

  private static void putLookup(String key, String path, int priority) {
    if (key == null || key.isEmpty() || path == null || path.isEmpty()) {
      return;
    }
    Integer existing = LOOKUP_PRIORITY.get(key);
    if (existing != null && existing >= priority) {
      return;
    }
    LOOKUP.put(key, path);
    LOOKUP_PRIORITY.put(key, priority);
  }

  private static void alias(String from, String toPath) {
    String key = normalizeLookup(from);
    putLookup(key, toPath, 200);
    putLookup(key.replace("_", ""), toPath, 200);
  }

  private static void registerManualAliases() {
    // Metalle / Erze
    alias("gold", "gold_ingot");
    alias("goldbarren", "gold_ingot");
    alias("eisen", "iron_ingot");
    alias("eisenbarren", "iron_ingot");
    alias("iron", "iron_ingot");
    alias("diamant", "diamond");
    alias("dia", "diamond");
    alias("smaragd", "emerald");
    alias("kohle", "coal");
    alias("holzkohle", "charcoal");
    alias("lapis", "lapis_lazuli");
    alias("lapislazuli", "lapis_lazuli");
    alias("netherite", "netherite_ingot");
    alias("netherit", "netherite_ingot");
    alias("netheritebarren", "netherite_ingot");
    alias("kupfer", "copper_ingot");
    alias("kupferbarren", "copper_ingot");
    alias("copper", "copper_ingot");
    alias("rohgold", "raw_gold");
    alias("roheisen", "raw_iron");
    alias("rohkupfer", "raw_copper");
    alias("goldklumpen", "gold_nugget");
    alias("eisenklumpen", "iron_nugget");
    alias("quartz", "quartz");
    alias("netherquarz", "quartz");
    alias("amethyst", "amethyst_shard");
    alias("amethystscherbe", "amethyst_shard");

    // Netherit-Rohstoffe (Priorität: "Schrott" = Antiker Schrott, nicht Netheritplatten)
    alias("antikerschrott", "ancient_debris");
    alias("antiker_schrott", "ancient_debris");
    alias("antiker schrott", "ancient_debris");
    alias("schrott", "ancient_debris");
    alias("antikesgeroell", "ancient_debris");
    alias("antikes_geroell", "ancient_debris");
    alias("ancientdebris", "ancient_debris");
    alias("ancient_debris", "ancient_debris");
    alias("netheritplatten", "netherite_scrap");
    alias("netheriteplatten", "netherite_scrap");
    alias("netherit_platten", "netherite_scrap");
    alias("netherite_scrap", "netherite_scrap");
    alias("antikerrest", "netherite_scrap");
    alias("netheriteschrott", "netherite_scrap");
    alias("netheritschrott", "netherite_scrap");
    alias("netheritblock", "netherite_block");
    alias("netheriteblock", "netherite_block");
    alias("netherit_block", "netherite_block");
    alias("blockofnetherite", "netherite_block");

    // Redstone / Technik
    alias("rs", "redstone");
    alias("redstone", "redstone");
    alias("redstonestaub", "redstone");
    alias("repeater", "repeater");
    alias("verstaerker", "repeater");
    alias("comparator", "comparator");
    alias("komparator", "comparator");
    alias("kolben", "piston");
    alias("haftkolben", "sticky_piston");
    alias("trichter", "hopper");
    alias("spender", "dispenser");
    alias("werfer", "dropper");
    alias("beobachter", "observer");
    alias("zielblock", "target");
    alias("notizblock", "note_block");
    alias("tageslichtsensor", "daylight_detector");
    alias("redstonelampe", "redstone_lamp");
    alias("hebel", "lever");
    alias("knopf", "stone_button");
    alias("druckplatte", "stone_pressure_plate");

    // Mob-Drops / Farming
    alias("enderperle", "ender_pearl");
    alias("enderpearl", "ender_pearl");
    alias("blaze", "blaze_rod");
    alias("blazestange", "blaze_rod");
    alias("blazepulver", "blaze_powder");
    alias("ghasttraene", "ghast_tear");
    alias("magmacreme", "magma_cream");
    alias("schleimball", "slime_ball");
    alias("slime", "slime_ball");
    alias("gunpowder", "gunpowder");
    alias("schwarzpulver", "gunpowder");
    alias("knochen", "bone");
    alias("knochenmehl", "bone_meal");
    alias("faden", "string");
    alias("spinnenauge", "spider_eye");
    alias("fermentiertesspinnenauge", "fermented_spider_eye");
    alias("fleisch", "beef");
    alias("rohfleisch", "beef");
    alias("steak", "cooked_beef");
    alias("schweinefleisch", "porkchop");
    alias("schweinesteak", "cooked_porkchop");
    alias("huehnchen", "chicken");
    alias("gebrateneshuehnchen", "cooked_chicken");
    alias("hammelfleisch", "mutton");
    alias("gebrateneshammelfleisch", "cooked_mutton");
    alias("kaninchen", "rabbit");
    alias("gebrateneskaninchen", "cooked_rabbit");
    alias("kabeljau", "cod");
    alias("lachs", "salmon");
    alias("tropenfisch", "tropical_fish");
    alias("kugelfisch", "pufferfish");
    alias("brot", "bread");
    alias("apfel", "apple");
    alias("goldapfel", "golden_apple");
    alias("gapple", "golden_apple");
    alias("enchantedgapple", "enchanted_golden_apple");
    alias("notchapfel", "enchanted_golden_apple");
    alias("karotte", "carrot");
    alias("goldkarotte", "golden_carrot");
    alias("kartoffel", "potato");
    alias("backenkartoffel", "baked_potato");
    alias("giftigekartoffel", "poisonous_potato");
    alias("roteruebe", "beetroot");
    alias("rote_ruebe", "beetroot");
    alias("beete", "beetroot");
    alias("zucker", "sugar");
    alias("zuckerrohr", "sugar_cane");
    alias("weizen", "wheat");
    alias("weizensamen", "wheat_seeds");
    alias("melone", "melon_slice");
    alias("melonen", "melon");
    alias("kuerbis", "pumpkin");
    alias("kakaobohnen", "cocoa_beans");
    alias("netherwarze", "nether_wart");
    alias("leuchtbeeren", "glow_berries");
    alias("suessbeeren", "sweet_berries");
    alias("honigwabe", "honeycomb");
    alias("honigflasche", "honey_bottle");
    alias("eier", "egg");
    alias("ei", "egg");
    alias("milcheimer", "milk_bucket");
    alias("wassereimer", "water_bucket");
    alias("lavaeimer", "lava_bucket");
    alias("pulverschneeimer", "powder_snow_bucket");
    alias("axolotleimer", "axolotl_bucket");
    alias("schlammeimer", "tadpole_bucket");

    // Blöcke / Bau
    alias("holz", "oak_log");
    alias("eiche", "oak_log");
    alias("eichenstamm", "oak_log");
    alias("birke", "birch_log");
    alias("birkenstamm", "birch_log");
    alias("fichte", "spruce_log");
    alias("fichtenstamm", "spruce_log");
    alias("tropenholz", "jungle_log");
    alias("akazie", "acacia_log");
    alias("schwarzeiche", "dark_oak_log");
    alias("mangrove", "mangrove_log");
    alias("kirsche", "cherry_log");
    alias("bambus", "bamboo");
    alias("cobble", "cobblestone");
    alias("bruchstein", "cobblestone");
    alias("stein", "stone");
    alias("glatterstein", "smooth_stone");
    alias("andesit", "andesite");
    alias("diorit", "diorite");
    alias("granit", "granite");
    alias("tiefenschiefer", "deepslate");
    alias("schwarzstein", "blackstone");
    alias("basalt", "basalt");
    alias("obsidian", "obsidian");
    alias("weinendesobsidian", "crying_obsidian");
    alias("glas", "glass");
    alias("glasscheibe", "glass_pane");
    alias("sand", "sand");
    alias("rotersand", "red_sand");
    alias("kies", "gravel");
    alias("ton", "clay_ball");
    alias("tonblock", "clay");
    alias("ziegel", "brick");
    alias("ziegelstein", "bricks");
    alias("netherziegel", "nether_brick");
    alias("endstein", "end_stone");
    alias("purpur", "purpur_block");
    alias("prismarin", "prismarine");
    alias("seelaterne", "sea_lantern");
    alias("leuchtenstein", "glowstone");
    alias("leuchtstein", "glowstone");
    alias("seelenerde", "soul_soil");
    alias("seelensand", "soul_sand");
    alias("netherrack", "netherrack");
    alias("magma", "magma_block");
    alias("eis", "ice");
    alias("packeis", "packed_ice");
    alias("blaueis", "blue_ice");
    alias("schnee", "snow_block");
    alias("schneeball", "snowball");
    alias("beton", "white_concrete");
    alias("betonpulver", "white_concrete_powder");
    alias("terracotta", "terracotta");
    alias("glasierte_terracotta", "white_glazed_terracotta");

    // Truhen / Lager / Utility
    alias("truhe", "chest");
    alias("kiste", "chest");
    alias("endertruhe", "ender_chest");
    alias("fass", "barrel");
    alias("shulker", "shulker_box");
    alias("shulkerbox", "shulker_box");
    alias("totem", "totem_of_undying");
    alias("totemdesunsterblichen", "totem_of_undying");
    alias("elytren", "elytra");
    alias("elytra", "elytra");
    alias("netherstern", "nether_star");
    alias("zaubertisch", "enchanting_table");
    alias("verzauberungstisch", "enchanting_table");
    alias("werkbank", "crafting_table");
    alias("werkbanktisch", "crafting_table");
    alias("schmelzofen", "blast_furnace");
    alias("hochofen", "blast_furnace");
    alias("ofen", "furnace");
    alias("raeucherofen", "smoker");
    alias("amboss", "anvil");
    alias("schleifstein", "grindstone");
    alias("steinsaege", "stonecutter");
    alias("webstuhl", "loom");
    alias("kartentisch", "cartography_table");
    alias("schmiedetisch", "smithing_table");
    alias("fassbinder", "barrel");
    alias("komposter", "composter");
    alias("biene", "bee_nest");
    alias("bienenstock", "beehive");
    alias("brauerei", "brewing_stand");
    alias("braustand", "brewing_stand");
    alias("kessel", "cauldron");
    alias("leuchtfeuer", "beacon");
    alias("konduit", "conduit");
    alias("spawner", "spawner");
    alias("monsterspawner", "spawner");
    alias("jukebox", "jukebox");
    alias("plattenspieler", "jukebox");
    alias("lagerfeuer", "campfire");
    alias("seelenlagerfeuer", "soul_campfire");
    alias("fackel", "torch");
    alias("seelenfackel", "soul_torch");
    alias("laterne", "lantern");
    alias("seelenlaterne", "soul_lantern");
    alias("kerze", "candle");
    alias("buecherregal", "bookshelf");
    alias("buch", "book");
    alias("beschriebenesbuch", "written_book");
    alias("buchundfeder", "writable_book");
    alias("verzaubertesbuch", "enchanted_book");
    alias("papier", "paper");
    alias("karte", "map");
    alias("leerekarte", "map");
    alias("kompass", "compass");
    alias("uhr", "clock");
    alias("fernrohr", "spyglass");
    alias("pinsel", "brush");
    alias("feuerzeug", "flint_and_steel");
    alias("feuerkugel", "fire_charge");
    alias("schere", "shears");
    alias("angel", "fishing_rod");
    alias("bogen", "bow");
    alias("armbrust", "crossbow");
    alias("schild", "shield");
    alias("dreizack", "trident");
    alias("streitkolben", "mace");
    alias("windladung", "wind_charge");
    alias("spektralpfeil", "spectral_arrow");
    alias("pfeil", "arrow");
    alias("feuerwerksrakete", "firework_rocket");
    alias("feuerwerk", "firework_rocket");
    alias("sattel", "saddle");
    alias("minecart", "minecart");
    alias("lore", "minecart");
    alias("boot", "oak_boat");
    alias("eichenboot", "oak_boat");
    alias("truheboot", "oak_chest_boat");

    // Rüstung / Werkzeuge (Kurzformen)
    alias("diahelm", "diamond_helmet");
    alias("diabrust", "diamond_chestplate");
    alias("diahose", "diamond_leggings");
    alias("diaschuhe", "diamond_boots");
    alias("diaschwert", "diamond_sword");
    alias("diapickaxe", "diamond_pickaxe");
    alias("diapicke", "diamond_pickaxe");
    alias("diaspate", "diamond_shovel");
    alias("diaaxt", "diamond_axe");
    alias("diahacke", "diamond_hoe");
    alias("netheritehelm", "netherite_helmet");
    alias("netheritebrust", "netherite_chestplate");
    alias("netheritehose", "netherite_leggings");
    alias("netheriteschuhe", "netherite_boots");
    alias("netheriteschwert", "netherite_sword");
    alias("netheritepicke", "netherite_pickaxe");
    alias("eisenhelm", "iron_helmet");
    alias("eisenbrust", "iron_chestplate");
    alias("eisenhose", "iron_leggings");
    alias("eisenschuhe", "iron_boots");
    alias("eisenschwert", "iron_sword");
    alias("eisenpicke", "iron_pickaxe");

    // End / Nether Spezial
    alias("endkristall", "end_crystal");
    alias("endstab", "end_rod");
    alias("chorus", "chorus_fruit");
    alias("chorusfrucht", "chorus_fruit");
    alias("gepoptechorusfrucht", "popped_chorus_fruit");
    alias("shulkerschale", "shulker_shell");
    alias("phantommembran", "phantom_membrane");
    alias("drachenatem", "dragon_breath");
    alias("drachenei", "dragon_egg");
    alias("witherkopf", "wither_skeleton_skull");
    alias("skelettschaedel", "skeleton_skull");
    alias("zombieskopf", "zombie_head");
    alias("creeperkopf", "creeper_head");
    alias("spielerkopf", "player_head");
    alias("piglinkopf", "piglin_head");
    alias("herzdesmeeres", "heart_of_the_sea");
    alias("nautilus", "nautilus_shell");
    alias("prismarinkristall", "prismarine_crystals");
    alias("prismarinscherbe", "prismarine_shard");
    alias("seepickel", "sea_pickle");
    alias("kelp", "kelp");
    alias("seetang", "kelp");
    alias("getrockneterkelp", "dried_kelp");
    alias("skulk", "sculk");
    alias("sculk", "sculk");
    alias("skulksensor", "sculk_sensor");
    alias("skulkschrieer", "sculk_shrieker");
    alias("skulkkatalysator", "sculk_catalyst");
    alias("echo", "echo_shard");
    alias("echoscherbe", "echo_shard");
    alias("bergungscompass", "recovery_compass");
    alias("goathorn", "goat_horn");
    alias("ziegenhorn", "goat_horn");
    alias("platten", "music_disc_13");
    alias("scherbe", "pottery_sherd");
    alias("schmiedevorlage", "netherite_upgrade_smithing_template");
    alias("netheriteupgrade", "netherite_upgrade_smithing_template");
    alias("versuchskammer", "trial_key");
    alias("ominoeseflasche", "ominous_bottle");
    alias("ominousbottle", "ominous_bottle");
    alias("breeze", "breeze_rod");
    alias("breezerod", "breeze_rod");
    alias("schwererkern", "heavy_core");
    alias("heavycore", "heavy_core");
  }

  private static ItemStack resolveUncached(String key) {
    ItemStackFactory factory = Laby.references().itemStackFactory();

    String path = key;
    String namespace = "minecraft";
    if (path.contains(":")) {
      String[] parts = path.split(":", 2);
      namespace = parts[0];
      path = parts[1];
    }

    // Lookup-Keys: mit _ und ohne, sowie normalisierte Form (inkl. Umlaute→ae/oe/ue)
    String underscored = path.replace(' ', '_').replace('-', '_').toLowerCase(Locale.ROOT);
    String compact = underscored.replace("_", "");
    String normalized = normalizeLookup(path);
    String normalizedCompact = normalized.replace("_", "");

    String mapped = firstNonNull(
        LOOKUP.get(underscored),
        LOOKUP.get(compact),
        LOOKUP.get(normalized),
        LOOKUP.get(normalizedCompact),
        LOOKUP.get(normalizeLookup(underscored)),
        BY_PATH.containsKey(underscored) ? underscored : null,
        BY_PATH.containsKey(normalized) ? normalized : null
    );

    if (mapped != null) {
      ItemStack stack = createFromPath(factory, namespace, mapped);
      if (isValid(stack)) {
        return stack;
      }
    }

    ItemStack stack = tryCreate(factory, namespace, underscored);
    if (isValid(stack)) {
      return stack;
    }

    String[] suffixes = {
        "_ingot", "_block", "_ore", "_nugget", "_log", "_planks", "_dust",
        "_shard", "_scrap", "_seeds", "_bucket", "_spawn_egg"
    };
    for (String suffix : suffixes) {
      if (underscored.endsWith(suffix)) {
        continue;
      }
      stack = createFromPath(factory, namespace, underscored + suffix);
      if (isValid(stack)) {
        return stack;
      }
    }

    String fuzzy = fuzzyFindPath(underscored);
    if (fuzzy != null) {
      stack = createFromPath(factory, namespace, fuzzy);
      if (isValid(stack)) {
        return stack;
      }
    }

    return placeholder();
  }

  @SafeVarargs
  private static <T> T firstNonNull(T... values) {
    for (T value : values) {
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private static String fuzzyFindPath(String query) {
    String compact = query.replace("_", "");
    String best = null;
    int bestScore = Integer.MIN_VALUE;

    for (String path : BY_PATH.keySet()) {
      int score = scoreMatch(query, compact, path);
      if (score > bestScore) {
        bestScore = score;
        best = path;
      }
    }

    // Auch lokalisierte Lookup-Keys durchsuchen
    for (Map.Entry<String, String> entry : LOOKUP.entrySet()) {
      int score = scoreMatch(query, compact, entry.getKey());
      if (score > bestScore) {
        bestScore = score;
        best = entry.getValue();
      }
    }

    return bestScore >= 50 ? best : null;
  }

  private static int scoreMatch(String query, String compactQuery, String path) {
    String compactPath = path.replace("_", "");
    if (path.equals(query)) {
      return 1000 + priorityFor(path);
    }
    if (compactPath.equals(compactQuery)) {
      return 900 + priorityFor(path);
    }
    if (path.startsWith(query + "_") || path.endsWith("_" + query)) {
      return 200 + priorityFor(path) - Math.min(path.length(), 80);
    }
    if (compactPath.startsWith(compactQuery) || compactPath.endsWith(compactQuery)) {
      return 150 + priorityFor(path) - Math.min(path.length(), 80);
    }
    if (query.length() >= 4 && path.contains(query)) {
      return 80 + priorityFor(path) - Math.min(path.length(), 80);
    }
    if (compactQuery.length() >= 4 && compactPath.contains(compactQuery)) {
      return 60 + priorityFor(path) - Math.min(path.length(), 80);
    }
    return Integer.MIN_VALUE;
  }

  private static ItemStack createFromPath(ItemStackFactory factory, String namespace, String path) {
    VanillaItem vanilla = BY_PATH.get(path);
    if (vanilla == null) {
      try {
        vanilla = VanillaItems.findItem(ResourceLocation.create(namespace, path));
      } catch (Exception ignored) {
      }
    }
    if (vanilla != null) {
      try {
        ItemStack stack = factory.create(vanilla);
        if (isValid(stack)) {
          return stack;
        }
      } catch (Exception ignored) {
      }
    }
    return tryCreate(factory, namespace, path);
  }

  private static ItemStack tryCreate(ItemStackFactory factory, String namespace, String path) {
    try {
      return factory.create(namespace, path);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static boolean isValid(ItemStack stack) {
    return stack != null && !stack.isAir();
  }

  public static ItemStack placeholder() {
    ensureIndex();
    try {
      ItemStack chest = Laby.references().itemStackFactory().create(VanillaItems.CHEST);
      if (isValid(chest)) {
        return chest;
      }
    } catch (Exception ignored) {
    }
    return Laby.references().itemStackFactory().create("minecraft", "chest");
  }
}
