/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # FlowerManaCosts.java
 * - Maps Botania flower registry IDs to mana costs for the Petal Apothecary of the Everflowing.
 * - Botania petal apothecary recipes have no mana cost field, this provides the mana cost of the  craft
 *   based on the flower's tier and role (generating vs functional).
 * - Tier costs are configurable via BotaniaRunicRitualizationConfig.
 */
package corvaeoboro.botania_runic_ritualization.block;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualizationConfig;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public final class FlowerManaCosts {

	private FlowerManaCosts() {}

	//region TIER
	// Config-backed tier costs. Each tier maps to a configurable mana value.
	private static int basicGenerating() { return BotaniaRunicRitualizationConfig.get().flowerManaBasicGenerating; }
	private static int midGenerating() { return BotaniaRunicRitualizationConfig.get().flowerManaMidGenerating; }
	private static int highGenerating() { return BotaniaRunicRitualizationConfig.get().flowerManaHighGenerating; }
	private static int basicFunctional() { return BotaniaRunicRitualizationConfig.get().flowerManaBasicFunctional; }
	private static int midFunctional() { return BotaniaRunicRitualizationConfig.get().flowerManaMidFunctional; }
	private static int highFunctional() { return BotaniaRunicRitualizationConfig.get().flowerManaHighFunctional; }
	private static int defaultCost() { return BotaniaRunicRitualizationConfig.get().flowerManaDefault; }
	//endregion

	//region MAP
	// Flower registry ID -> mana cost. Built lazily from config tier values.
	private static Map<String, Integer> buildCosts() {
		return Map.ofEntries(
				Map.entry("botania:daybloom", basicGenerating()),
				Map.entry("botania:nightshade", basicGenerating()),
				Map.entry("botania:hydroangeas", basicGenerating()),
				Map.entry("botania:endoflame", basicGenerating()),
				Map.entry("botania:arcanterose", basicGenerating()),

				Map.entry("botania:thermalily", midGenerating()),
				Map.entry("botania:munchdew", midGenerating()),
				Map.entry("botania:entropinnyum", midGenerating()),
				Map.entry("botania:kekimurus", midGenerating()),
				Map.entry("botania:gourmaryllis", midGenerating()),

				Map.entry("botania:narslimmus", highGenerating()),
				Map.entry("botania:spectrolus", highGenerating()),
				Map.entry("botania:dandelifeon", highGenerating()),
				Map.entry("botania:rafflowsia", highGenerating()),
				Map.entry("botania:shulk_me_not", highGenerating()),

				Map.entry("botania:pure_daisy", basicFunctional()),
				Map.entry("botania:bellethorne", basicFunctional()),
				Map.entry("botania:dreadthorne", basicFunctional()),
				Map.entry("botania:heisei_dream", basicFunctional()),
				Map.entry("botania:tigerseye", basicFunctional()),

				Map.entry("botania:orechid", midFunctional()),
				Map.entry("botania:orechid_ignem", midFunctional()),
				Map.entry("botania:fallen_kanade", midFunctional()),
				Map.entry("botania:exoflame", midFunctional()),
				Map.entry("botania:agricarnation", midFunctional()),
				Map.entry("botania:hypovereasting", midFunctional()),
				Map.entry("botania:tangleberrie", midFunctional()),
				Map.entry("botania:jaded_amaranthus", midFunctional()),
				Map.entry("botania:clayconia", midFunctional()),

				Map.entry("botania:loonium", highFunctional()),
				Map.entry("botania:marimorphosis", highFunctional()),
				Map.entry("botania:bubbell", highFunctional()),
				Map.entry("botania:solegnolia", highFunctional()),
				Map.entry("botania:medumone", highFunctional()),
				Map.entry("botania:pollidisiac", highFunctional()),
				Map.entry("botania:rannuncarpus", highFunctional()),
				Map.entry("botania:spectranthemum", highFunctional()),
				Map.entry("botania:tigressy", highFunctional()),
				Map.entry("botania:daffomill", highFunctional()),
				Map.entry("botania:vincetea", highFunctional())
		);
	}

	private static volatile Map<String, Integer> CACHED_COSTS;

	private static Map<String, Integer> costs() {
		Map<String, Integer> cachedCostMap = CACHED_COSTS;
		if (cachedCostMap == null) {
			synchronized (FlowerManaCosts.class) {
				cachedCostMap = CACHED_COSTS;
				if (cachedCostMap == null) {
					cachedCostMap = buildCosts();
					CACHED_COSTS = cachedCostMap;
				}
			}
		}
		return cachedCostMap;
	}

	// Invalidates the cached cost map so the next call rebuilds from config. 
	public static void invalidateCache() { CACHED_COSTS = null; }
	//endregion

	//region ACCESS
	// Returns the mana cost for the given flower registry id string (e.g. "botania:endoflame").
	// Falls back to the config default for unknown flowers.
	public static int getCost(String registryId) {
		return costs().getOrDefault(registryId, defaultCost());
	}

	// Returns the mana cost for the given ItemStack's registry id.
	// Handles floating_/potted_ prefixes by falling back to the base flower id.
	public static int getCostForItem(ItemStack stack) {
		if (stack.isEmpty()) return defaultCost();
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) return defaultCost();
		String fullRegistryId = itemId.toString();
		Integer costForFlower = costs().get(fullRegistryId);
		if (costForFlower != null) return costForFlower;
		String itemPath = itemId.getPath();
		if (itemPath.startsWith("floating_")) {
			costForFlower = costs().get("botania:" + itemPath.substring("floating_".length()));
			if (costForFlower != null) return costForFlower;
		}
		if (itemPath.startsWith("potted_")) {
			costForFlower = costs().get("botania:" + itemPath.substring("potted_".length()));
			if (costForFlower != null) return costForFlower;
		}
		return defaultCost();
	}
	//endregion
}
