/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # BotaniaRunicRitualizationConfig.java
 * - Loads/saves `config/botania_runic_ritualization.json5`.
 * - Writes a JSON5-style config with `//` comments and section spacing for readability.
 * - Uses Gson lenient parsing so `//` comments are accepted.
 * - Holds the set of runtime tunables (mana capacities, costs, cooldowns, pedestal radius, VFX, debug).
 *
 * # CONFIG SECTIONS:
 * - `schemaVersion`: internal schema version.
 *
 * ## Runic Altar Dais
 * - `runic_altar_mana_capacity`: max mana the runic dais can hold.
 * - `runic_altar_mana_multiplier`: multiplier applied to native botania runic_altar recipe mana costs.
 * - `runic_altar_cooldown_ticks`: cooldown ticks after a successful craft.
 * - `runic_altar_craft_duration_ticks`: processing phase duration in ticks before a craft completes.
 *
 * ## Petal Apothecary Everflowing
 * - `petal_apothecary_mana_capacity`: max mana the petal apothecary can hold.
 * - `petal_apothecary_cooldown_ticks`: cooldown ticks after a successful craft.
 *
 * ## Botanical Brewery Aludel
 * - `botanical_brewery_mana_capacity`: max mana the aludel can hold.
 * - `botanical_brewery_mana_multiplier`: multiplier applied to native botania brewery recipe mana costs.
 * - `botanical_brewery_cooldown_ticks`: cooldown ticks after a successful craft.
 *
 * ## Flower Mana Costs (Petal Apothecary)
 * - `flower_mana_basic_generating`: mana cost for basic generating flowers (daybloom, endoflame, etc.).
 * - `flower_mana_mid_generating`: mana cost for mid-tier generating flowers (thermalily, entropinnyum, etc.).
 * - `flower_mana_high_generating`: mana cost for high-end generating flowers (spectrolus, dandelifeon, etc.).
 * - `flower_mana_basic_functional`: mana cost for basic functional flowers (pure_daisy, bellethorne, etc.).
 * - `flower_mana_mid_functional`: mana cost for mid-tier functional flowers (orechid, exoflame, etc.).
 * - `flower_mana_high_functional`: mana cost for high-end functional flowers (rannuncarpus, marimorphosis, etc.).
 * - `flower_mana_default`: default mana cost for unrecognized flowers.
 *
 * ## Pedestal Finder
 * - `pedestal_search_radius`: horizontal radius for finding pedestals around a machine.
 *
 * ## Mana Pool Linker
 * - `mana_pool_linker_enabled`: if true, machines interact with adjacent Botania mana pools.
 * - `mana_pool_linker_transfer_per_check`: amount of mana transferred per check cycle.
 * - `mana_pool_linker_base_check_interval`: base interval in ticks between pool status checks.
 *
 * ## Visual Effects
 * - `enable_vfx`: master toggle for crafting particles and effects.
 * - `enable_wand_link_visualization`: sparkling blue lines from active pedestals when holding a Botania wand.
 *
 * # NOTES:
 * - Read by all three machine block entities for mana capacity, multiplier, and cooldown values.
 * - Read by FlowerManaCosts for per-tier flower mana costs.
 * - Read by PedestalFinder for the search radius.
 * - Read by ManaPoolLinker for transfer amount and check interval.
 * - Read by PedestalOfLivingRockBlockEntity for debug logging.
 * - Read by screen classes for cooldown/duration progress display.
 */
package corvaeoboro.botania_runic_ritualization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Locale;

public final class BotaniaRunicRitualizationConfig {
	//region CONSTANT
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME_JSON5 = "botania_runic_ritualization.json5";

	private static volatile Data INSTANCE;
	private static final Object LOCK = new Object();
	//endregion

	//region ACCESS
	public static Data get() {
		ensureLoaded();
		return INSTANCE;
	}

	public static void reload() {
		synchronized (LOCK) {
			INSTANCE = null;
		}
		ensureLoaded();
	}
	//endregion

	//region DATA
	//   config . All runtime tunables live here as public fields with defaults.
	// @SerializedName maps each field to its snake_case JSON5 key, with camelCase alternates for compatibility.
	public static final class Data {
		public int schemaVersion = 1;

		// Runic Altar Dais
		@SerializedName(value = "runic_altar_mana_capacity", alternate = {"runicAltarManaCapacity"})
		public int runicAltarManaCapacity = 50000;
		@SerializedName(value = "runic_altar_mana_multiplier", alternate = {"runicAltarManaMultiplier"})
		public float runicAltarManaMultiplier = 1.0F;
		@SerializedName(value = "runic_altar_cooldown_ticks", alternate = {"runicAltarCooldownTicks"})
		public int runicAltarCooldownTicks = 100;
		@SerializedName(value = "runic_altar_craft_duration_ticks", alternate = {"runicAltarCraftDurationTicks"})
		public int runicAltarCraftDurationTicks = 400;

		// Petal Apothecary Everflowing
		@SerializedName(value = "petal_apothecary_mana_capacity", alternate = {"petalApothecaryManaCapacity"})
		public int petalApothecaryManaCapacity = 50000;
		@SerializedName(value = "petal_apothecary_cooldown_ticks", alternate = {"petalApothecaryCooldownTicks"})
		public int petalApothecaryCooldownTicks = 200;

		// Botanical Brewery Aludel
		@SerializedName(value = "botanical_brewery_mana_capacity", alternate = {"botanicalBreweryManaCapacity"})
		public int botanicalBreweryManaCapacity = 50000;
		@SerializedName(value = "botanical_brewery_mana_multiplier", alternate = {"botanicalBreweryManaMultiplier"})
		public float botanicalBreweryManaMultiplier = 1.0F;
		@SerializedName(value = "botanical_brewery_cooldown_ticks", alternate = {"botanicalBreweryCooldownTicks"})
		public int botanicalBreweryCooldownTicks = 200;

		// Flower Mana Costs
		@SerializedName(value = "flower_mana_basic_generating", alternate = {"flowerManaBasicGenerating"})
		public int flowerManaBasicGenerating = 2500;
		@SerializedName(value = "flower_mana_mid_generating", alternate = {"flowerManaMidGenerating"})
		public int flowerManaMidGenerating = 5000;
		@SerializedName(value = "flower_mana_high_generating", alternate = {"flowerManaHighGenerating"})
		public int flowerManaHighGenerating = 8000;
		@SerializedName(value = "flower_mana_basic_functional", alternate = {"flowerManaBasicFunctional"})
		public int flowerManaBasicFunctional = 5000;
		@SerializedName(value = "flower_mana_mid_functional", alternate = {"flowerManaMidFunctional"})
		public int flowerManaMidFunctional = 10000;
		@SerializedName(value = "flower_mana_high_functional", alternate = {"flowerManaHighFunctional"})
		public int flowerManaHighFunctional = 15000;
		@SerializedName(value = "flower_mana_default", alternate = {"flowerManaDefault"})
		public int flowerManaDefault = 5000;

		// Pedestal Finder
		@SerializedName(value = "pedestal_search_radius", alternate = {"pedestalSearchRadius"})
		public int pedestalSearchRadius = 2;

		// Mana Pool Linker
		@SerializedName(value = "mana_pool_linker_enabled", alternate = {"manaPoolLinkerEnabled"})
		public boolean manaPoolLinkerEnabled = true;
		@SerializedName(value = "mana_pool_linker_transfer_per_check", alternate = {"manaPoolLinkerTransferPerCheck"})
		public int manaPoolLinkerTransferPerCheck = 2000;
		@SerializedName(value = "mana_pool_linker_base_check_interval", alternate = {"manaPoolLinkerBaseCheckInterval"})
		public int manaPoolLinkerBaseCheckInterval = 100;

		// Visual Effects
		@SerializedName(value = "enable_vfx", alternate = {"enableVfx"})
		public boolean enableVfx = true;
		@SerializedName(value = "enable_wand_link_visualization", alternate = {"enableWandLinkVisualization"})
		public boolean enableWandLinkVisualization = true;
	}
	//endregion

	//region LOAD
	// Reads the JSON5 config file with lenient Gson (accepts // comments).
	// Falls back to defaults on parse failure. Clamps all values to safe ranges.
	private static void ensureLoaded() {
		if (INSTANCE != null) {
			return;
		}
		synchronized (LOCK) {
			if (INSTANCE != null) {
				return;
			}

			Path configDir = FabricLoader.getInstance().getConfigDir();
			Path path = configDir.resolve(FILE_NAME_JSON5);
			Data data = new Data();
			boolean shouldWrite = false;
			final boolean allowWriteJson5 = !Files.exists(path);

			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					JsonReader jsonReader = new JsonReader(reader);
					jsonReader.setLenient(true);
					Data parsed = GSON.fromJson(jsonReader, Data.class);
					if (parsed != null) {
						data = parsed;
					}
				} catch (Exception e) {
					data = new Data();
					shouldWrite = true;
					System.err.println("[BotaniaRunicRitualizationConfig] Failed to parse config: " + path);
					e.printStackTrace();
				}
			} else {
				shouldWrite = true;
			}

			// Clamp / validate values
			data.runicAltarManaCapacity = clampInt(data.runicAltarManaCapacity, 1000, 1000000, 50000);
			data.runicAltarManaMultiplier = clampFloat(data.runicAltarManaMultiplier, 0.1F, 10.0F, 1.0F);
			data.runicAltarCooldownTicks = clampInt(data.runicAltarCooldownTicks, 0, 6000, 100);
			data.runicAltarCraftDurationTicks = clampInt(data.runicAltarCraftDurationTicks, 1, 6000, 400);

			data.petalApothecaryManaCapacity = clampInt(data.petalApothecaryManaCapacity, 1000, 1000000, 50000);
			data.petalApothecaryCooldownTicks = clampInt(data.petalApothecaryCooldownTicks, 0, 6000, 200);

			data.botanicalBreweryManaCapacity = clampInt(data.botanicalBreweryManaCapacity, 1000, 1000000, 50000);
			data.botanicalBreweryManaMultiplier = clampFloat(data.botanicalBreweryManaMultiplier, 0.1F, 10.0F, 1.0F);
			data.botanicalBreweryCooldownTicks = clampInt(data.botanicalBreweryCooldownTicks, 0, 6000, 200);

			data.flowerManaBasicGenerating = clampInt(data.flowerManaBasicGenerating, 0, 100000, 2500);
			data.flowerManaMidGenerating = clampInt(data.flowerManaMidGenerating, 0, 100000, 5000);
			data.flowerManaHighGenerating = clampInt(data.flowerManaHighGenerating, 0, 100000, 8000);
			data.flowerManaBasicFunctional = clampInt(data.flowerManaBasicFunctional, 0, 100000, 5000);
			data.flowerManaMidFunctional = clampInt(data.flowerManaMidFunctional, 0, 100000, 10000);
			data.flowerManaHighFunctional = clampInt(data.flowerManaHighFunctional, 0, 100000, 15000);
			data.flowerManaDefault = clampInt(data.flowerManaDefault, 0, 100000, 5000);

			data.pedestalSearchRadius = clampInt(data.pedestalSearchRadius, 1, 16, 2);

			data.manaPoolLinkerTransferPerCheck = clampInt(data.manaPoolLinkerTransferPerCheck, 100, 100000, 2000);
			data.manaPoolLinkerBaseCheckInterval = clampInt(data.manaPoolLinkerBaseCheckInterval, 10, 1200, 100);

			if (data.schemaVersion < 1) {
				data.schemaVersion = 1;
				shouldWrite = true;
			}

			INSTANCE = data;
			if (shouldWrite && allowWriteJson5) {
				writeConfig(path, data);
			}
		}
	}
	//endregion

	//region WRITE
	private static void writeConfig(Path path, Data data) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				writer.write(toJson5String(data));
			}
		} catch (IOException ignored) {
		}
	}
	//endregion

	//region CLAMP
	private static int clampInt(int value, int min, int max, int defaultValue) {
		if (value < min || value > max) return defaultValue;
		return value;
	}

	private static float clampFloat(float value, float min, float max, float defaultValue) {
		if (value < min || value > max) return defaultValue;
		return value;
	}
	//endregion

	//region JSON5
	// Builds the human-readable JSON5 string with // comments and section headers.
	private static String toJson5String(Data data) {
		StringBuilder sb = new StringBuilder(4096);

		sb.append("// Botania Runic Ritualization config \n");
		sb.append("{\n");

		appendKeyValue(sb, "schemaVersion", data.schemaVersion, true);

		appendSectionHeader(sb, "Runic Altar Dais");
		appendKeyValueWithComment(sb, "runic_altar_mana_capacity", data.runicAltarManaCapacity,
				"Max mana the runic dais can hold in its persistent pool.");
		appendKeyValueWithComment(sb, "runic_altar_mana_multiplier", data.runicAltarManaMultiplier,
				"Multiplier applied to native botania runic_altar recipe mana costs. 1.0 = native cost.");
		appendKeyValueWithComment(sb, "runic_altar_cooldown_ticks", data.runicAltarCooldownTicks,
				"Cooldown in ticks after a successful craft. 100 = 5 seconds.");
		appendKeyValueWithComment(sb, "runic_altar_craft_duration_ticks", data.runicAltarCraftDurationTicks,
				"Processing phase duration in ticks before a craft completes. 400 = 20 seconds.");
		sb.append("\n");

		appendSectionHeader(sb, "Petal Apothecary Everflowing");
		appendKeyValueWithComment(sb, "petal_apothecary_mana_capacity", data.petalApothecaryManaCapacity,
				"Max mana the petal apothecary can hold in its persistent pool.");
		appendKeyValueWithComment(sb, "petal_apothecary_cooldown_ticks", data.petalApothecaryCooldownTicks,
				"Cooldown in ticks after a successful craft. 200 = 10 seconds.");
		sb.append("\n");

		appendSectionHeader(sb, "Botanical Brewery Aludel");
		appendKeyValueWithComment(sb, "botanical_brewery_mana_capacity", data.botanicalBreweryManaCapacity,
				"Max mana the aludel can hold in its persistent pool.");
		appendKeyValueWithComment(sb, "botanical_brewery_mana_multiplier", data.botanicalBreweryManaMultiplier,
				"Multiplier applied to native botania brewery recipe mana costs. 1.0 = native cost.");
		appendKeyValueWithComment(sb, "botanical_brewery_cooldown_ticks", data.botanicalBreweryCooldownTicks,
				"Cooldown in ticks after a successful craft. 200 = 10 seconds.");
		sb.append("\n");

		appendSectionHeader(sb, "Flower Mana Costs (Petal Apothecary)");
		sb.append("  // Botania petal apothecary recipes have no native mana cost field.\n");
		sb.append("  // These values assign mana costs based on the flower's tier and role.\n");
		appendKeyValueWithComment(sb, "flower_mana_basic_generating", data.flowerManaBasicGenerating,
				"Basic generating flowers (daybloom, nightshade, hydroangeas, endoflame, arcanterose).");
		appendKeyValueWithComment(sb, "flower_mana_mid_generating", data.flowerManaMidGenerating,
				"Mid-tier generating flowers (thermalily, munchdew, entropinnyum, kekimurus, gourmaryllis).");
		appendKeyValueWithComment(sb, "flower_mana_high_generating", data.flowerManaHighGenerating,
				"High-end generating flowers (narslimmus, spectrolus, dandelifeon, rafflowsia, shulk_me_not).");
		appendKeyValueWithComment(sb, "flower_mana_basic_functional", data.flowerManaBasicFunctional,
				"Basic functional flowers (pure_daisy, bellethorne, dreadthorne, heisei_dream, tigerseye).");
		appendKeyValueWithComment(sb, "flower_mana_mid_functional", data.flowerManaMidFunctional,
				"Mid-tier functional flowers (orechid, exoflame, agricarnation, clayconia, etc.).");
		appendKeyValueWithComment(sb, "flower_mana_high_functional", data.flowerManaHighFunctional,
				"High-end functional flowers (loonium, marimorphosis, rannuncarpus, etc.).");
		appendKeyValueWithComment(sb, "flower_mana_default", data.flowerManaDefault,
				"Default mana cost for unrecognized flowers.");
		sb.append("\n");

		appendSectionHeader(sb, "Pedestal Finder");
		appendKeyValueWithComment(sb, "pedestal_search_radius", data.pedestalSearchRadius,
				"Horizontal radius for finding pedestals around a machine (same Y level). 2 = 5x5 area.");
		sb.append("\n");

		appendSectionHeader(sb, "Mana Pool Linker");
		appendKeyValueWithComment(sb, "mana_pool_linker_enabled", data.manaPoolLinkerEnabled,
				"If true, machines pull from / distribute to adjacent Botania mana pools.");
		appendKeyValueWithComment(sb, "mana_pool_linker_transfer_per_check", data.manaPoolLinkerTransferPerCheck,
				"Amount of mana transferred per check cycle. Higher = faster pool draining/filling.");
		appendKeyValueWithComment(sb, "mana_pool_linker_base_check_interval", data.manaPoolLinkerBaseCheckInterval,
				"Base interval in ticks between pool status checks. 100 = 5 seconds.");
		sb.append("\n");

		appendSectionHeader(sb, "Visual Effects");
		appendKeyValueWithComment(sb, "enable_vfx", data.enableVfx,
				"Master toggle for crafting particles and effects.");
		appendKeyValueWithComment(sb, "enable_wand_link_visualization", data.enableWandLinkVisualization,
				"Sparkling blue lines from active pedestals when holding a Botania wand.");

		sb.append("}\n");
		return sb.toString();
	}
	//endregion

	//region FORMAT
	// JSON5 formatting helpers for the generated config file.
	private static void appendSectionHeader(StringBuilder sb, String name) {
		sb.append("  // ").append(name).append("    ------------------------------\n");
	}

	private static void appendKeyValueWithComment(StringBuilder sb, String key, boolean value, String comment) {
		sb.append("  // ").append(comment).append("\n");
		appendKeyValue(sb, key, value, true);
	}

	private static void appendKeyValueWithComment(StringBuilder sb, String key, int value, String comment) {
		sb.append("  // ").append(comment).append("\n");
		appendKeyValue(sb, key, value, true);
	}

	private static void appendKeyValueWithComment(StringBuilder sb, String key, float value, String comment) {
		sb.append("  // ").append(comment).append("\n");
		appendKeyValue(sb, key, value, true);
	}

	private static void appendKeyValue(StringBuilder sb, String key, boolean value, boolean trailingComma) {
		sb.append("  ").append(quote(key)).append(": ").append(value);
		sb.append(trailingComma ? ",\n" : "\n");
	}

	private static void appendKeyValue(StringBuilder sb, String key, int value, boolean trailingComma) {
		sb.append("  ").append(quote(key)).append(": ").append(value);
		sb.append(trailingComma ? ",\n" : "\n");
	}

	private static void appendKeyValue(StringBuilder sb, String key, float value, boolean trailingComma) {
		sb.append("  ").append(quote(key)).append(": ").append(String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", ""));
		sb.append(trailingComma ? ",\n" : "\n");
	}

	private static String quote(String s) {
		if (s == null) return "\"\"";
		return "\"" + s + "\"";
	}
	//endregion

	private BotaniaRunicRitualizationConfig() {}
}
