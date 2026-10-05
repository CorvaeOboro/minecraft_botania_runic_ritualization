/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # RuneDescriptions.java
 * - Classifies Botania runes into tiers for rich tooltip display in the Runic Dais GUI.
 * - Tier 1: Water, Fire, Earth, Air, Mana (elemental runes, 5200 mana, no rune dependencies)
 * - Tier 2: Spring, Summer, Autumn, Winter (seasonal , 8000 mana, require Tier 1 runes)
 * - Tier 3: Lust, Gluttony, Greed, Sloth, Wrath, Envy, Pride (sin runes, 12000 mana, require Tier 2 + Tier 1 runes)
 * - Special: any other runic altar output (e.g. player head)
 * - Also provides utility to check if any ItemStack is a Botania rune (for ingredient classification).
 */
package corvaeoboro.botania_runic_ritualization.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class RuneDescriptions {

	private RuneDescriptions() {}

	//region CONSTANT
	// Tier 1: elemental runes (5200 mana, no rune dependencies)
	// Rune of Mana is included here since it requires no prerequisite runes.
	private static final Set<String> TIER_1 = Set.of(
			"botania:rune_water",
			"botania:rune_fire",
			"botania:rune_earth",
			"botania:rune_air",
			"botania:rune_mana"
	);

	// Tier 2: seasonal runes (8000 mana, require Tier 1 runes)
	private static final Set<String> TIER_2 = Set.of(
			"botania:rune_spring",
			"botania:rune_summer",
			"botania:rune_autumn",
			"botania:rune_winter"
	);

	// Tier 3: sin runes (12000 mana, require Tier 2 + Tier 1 runes)
	private static final Set<String> TIER_3 = Set.of(
			"botania:rune_lust",
			"botania:rune_gluttony",
			"botania:rune_greed",
			"botania:rune_sloth",
			"botania:rune_wrath",
			"botania:rune_envy",
			"botania:rune_pride"
	);

	// Tier constants for display logic.
	public static final int TIER_NONE = 0;
	public static final int TIER_1_VAL = 1;
	public static final int TIER_2_VAL = 2;
	public static final int TIER_3_VAL = 3;
	public static final int TIER_SPECIAL = 4;
	//endregion

	//region TIER
	/**
	 * Returns the tier (1, 2, 3, or 4 for special) for the given item registry id string.
	 * Returns 0 if the item is not a recognized rune.
	 */
	public static int getTier(String registryId) {
		if (TIER_1.contains(registryId)) return TIER_1_VAL;
		if (TIER_2.contains(registryId)) return TIER_2_VAL;
		if (TIER_3.contains(registryId)) return TIER_3_VAL;
		// Any runic altar output that's not a recognized rune is "special"
		// (e.g. player head recipe). The caller should check if it's a runic altar recipe.
		return TIER_NONE;
	}

	// Returns the tier for the given ItemStack, based on its item registry id.
	public static int getTierForItem(ItemStack stack) {
		if (stack.isEmpty()) return TIER_NONE;
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) return TIER_NONE;
		return getTier(itemId.toString());
	}

	/**
	 * Returns the tier label string for display, e.g. "Tier 1", "Tier 2", "Tier 3", "Special".
	 */
	public static String getTierLabel(int tier) {
		return switch (tier) {
			case TIER_1_VAL -> "T1";
			case TIER_2_VAL -> "T2";
			case TIER_3_VAL -> "T3";
			case TIER_SPECIAL -> "Special";
			default -> "";
		};
	}

	/**
	 * Returns the ARGB color for the given tier, used for the tier label in tooltips.
	 * Tier 1: light blue (elemental)
	 * Tier 2: green (seasonal)
	 * Tier 3: gold/orange (sin)
	 * Special: purple
	 */
	public static int getTierColor(int tier) {
		return switch (tier) {
			case TIER_1_VAL -> 0xFF7FB7FF; // light blue
			case TIER_2_VAL -> 0xFF55FF55; // green
			case TIER_3_VAL -> 0xFFFFAA00; // gold/orange
			case TIER_SPECIAL -> 0xFFBB55FF; // purple
			default -> 0xFFAAAAAA; // gray
		};
	}

	// Returns a short description of the tier's dependency, or null if none. e.g. "Requires Tier 1 runes" for Tier 2.
	@Nullable
	public static String getTierDependencyNote(int tier) {
		return switch (tier) {
			case TIER_1_VAL -> "Base elemental rune";
			case TIER_2_VAL -> "Requires Tier 1 runes";
			case TIER_3_VAL -> "Requires Tier 2 + Tier 1 runes";
			case TIER_SPECIAL -> "Special recipe";
			default -> null;
		};
	}

	/**
	 * Checks if the given ItemStack is a Botania rune item.
	 * Used to classify ingredients into "Required Runes" vs "Base Materials".
	 */
	public static boolean isRune(ItemStack stack) {
		if (stack.isEmpty()) return false;
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) return false;
		String fullRegistryId = itemId.toString();
		return TIER_1.contains(fullRegistryId) || TIER_2.contains(fullRegistryId) || TIER_3.contains(fullRegistryId);
	}
	//endregion
}
