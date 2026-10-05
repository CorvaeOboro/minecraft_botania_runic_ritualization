/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # BrewDescriptions.java
 * - Classifies Botania brews into categories for rich tooltip display in the Botanical Brewery Aludel GUI.
 * - Categories:
 *   * Buff: positive potion effects (speed, strength, haste, healing, etc.)
 *   * Utility: non-combat effects (allure, soul cross, feather feet, emptiness, clear)
 *   * Special: complex/multi-effect brews (overload, bloodthirst)
 * - Also provides the brew's color for tooltip display.
 */
package corvaeoboro.botania_runic_ritualization.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import vazkii.botania.api.BotaniaAPI;
import vazkii.botania.api.brew.Brew;

import java.util.List;
import java.util.Set;

public final class BrewDescriptions {

	private BrewDescriptions() {}

	//region CONSTANT
	// Buff brews: positive potion effects (combat/movement)
	private static final Set<String> BUFF_BREWS = Set.of(
			"botania:speed",
			"botania:strength",
			"botania:haste",
			"botania:healing",
			"botania:jump_boost",
			"botania:regeneration",
			"botania:weak_regeneration",
			"botania:resistance",
			"botania:fire_resistance",
			"botania:water_breathing",
			"botania:invisibility",
			"botania:night_vision",
			"botania:absorption"
	);

	// Utility brews: non-combat/special effects
	private static final Set<String> UTILITY_BREWS = Set.of(
			"botania:allure",
			"botania:soul_cross",
			"botania:feather_feet",
			"botania:emptiness",
			"botania:clear"
	);

	// Special brews: complex/multi-effect
	private static final Set<String> SPECIAL_BREWS = Set.of(
			"botania:overload",
			"botania:bloodthirst"
	);

	// Category constants
	public static final int CAT_NONE = 0;
	public static final int CAT_BUFF = 1;
	public static final int CAT_UTILITY = 2;
	public static final int CAT_SPECIAL = 3;
	//endregion

	//region CATEGORY
	// Returns the category for the given brew registry id string.
	public static int getCategory(String brewId) {
		if (BUFF_BREWS.contains(brewId)) return CAT_BUFF;
		if (UTILITY_BREWS.contains(brewId)) return CAT_UTILITY;
		if (SPECIAL_BREWS.contains(brewId)) return CAT_SPECIAL;
		return CAT_NONE;
	}

	// Returns the category for the given Brew, based on its registry id.
	public static int getCategoryForBrew(Brew brew) {
		ResourceLocation brewId = BotaniaAPI.instance().getBrewRegistry().getKey(brew);
		if (brewId == null) return CAT_NONE;
		return getCategory(brewId.toString());
	}

	// Returns the category label string for display.
	public static String getCategoryLabel(int category) {
		return switch (category) {
			case CAT_BUFF -> "Buff";
			case CAT_UTILITY -> "Utility";
			case CAT_SPECIAL -> "Special";
			default -> "";
		};
	}

	/**
	 * Returns the ARGB color for the given category, used for the label in tooltips.
	 * Buff: pink/red (potion-like)
	 * Utility: cyan
	 * Special: dark purple
	 */
	public static int getCategoryColor(int category) {
		return switch (category) {
			case CAT_BUFF -> 0xFFFF66CC; // pink
			case CAT_UTILITY -> 0xFF66CCFF; // cyan
			case CAT_SPECIAL -> 0xFFBB55FF; // purple
			default -> 0xFFAAAAAA; // gray
		};
	}
	//endregion

	//region DETAIL
	// Returns the brew's color for display, or a default if unavailable.
	public static int getBrewColor(Brew brew, ItemStack stack) {
		try {
			return brew.getColor(stack);
		} catch (Exception exception) {
			return 0xFFFFFFFF;
		}
	}

	// Returns a short description of the brew's potion effects for display. e.g. "Speed II (1:30), Haste I (1:30)"
	@Nullable
	public static String getEffectSummary(Brew brew, ItemStack stack) {
		try {
			List<MobEffectInstance> effects = brew.getPotionEffects(stack);
			if (effects == null || effects.isEmpty()) return null;
			StringBuilder summaryBuilder = new StringBuilder();
			for (int effectIndex = 0; effectIndex < effects.size(); effectIndex++) {
				if (effectIndex > 0) summaryBuilder.append(", ");
				MobEffectInstance effectInstance = effects.get(effectIndex);
				String effectName = effectInstance.getEffect().getDisplayName().getString();
				int amplifier = effectInstance.getAmplifier();
				int duration = effectInstance.getDuration();
				summaryBuilder.append(effectName);
				if (amplifier > 0) {
					summaryBuilder.append(" ").append(amplifier + 1);
				}
				if (duration > 0 && duration < 9600) {
					summaryBuilder.append(" (").append(formatDuration(duration)).append(")");
				}
			}
			return summaryBuilder.toString();
		} catch (Exception exception) {
			return null;
		}
	}

	// Formats a tick duration as M:SS or seconds.
	private static String formatDuration(int ticks) {
		int seconds = ticks / 20;
		if (seconds >= 60) {
			int minutes = seconds / 60;
			int remainingSeconds = seconds % 60;
			return minutes + ":" + (remainingSeconds < 10 ? "0" + remainingSeconds : remainingSeconds);
		}
		return seconds + "s";
	}
	//endregion
}
