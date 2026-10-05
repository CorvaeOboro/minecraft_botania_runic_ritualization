/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # FlowerDescriptions.java
 * - Bundled tooltip descriptions for Botania items, loaded from assets/botania_runic_ritualization/descriptions.json.
 * - Used by machine GUI screens to append description lines when hovering over recipe outputs (e.g. flowers in the apothecary).
 * - Descriptions mirror the Botania_Descriptions companion mod so the dais GUIs are self-contained.
 */
package corvaeoboro.botania_runic_ritualization.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlowerDescriptions {

	//region CONSTANT
	private static final String BUNDLED_RESOURCE = "/assets/botania_runic_ritualization/descriptions.json";
	private static final Map<String, List<String>> DESCRIPTIONS = new LinkedHashMap<>();
	private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
	private static boolean loaded = false;
	//endregion

	private FlowerDescriptions() {}

	//region LOAD
	private static void ensureLoaded() {
		if (loaded) return;
		loaded = true;
		try (InputStream inputStream = FlowerDescriptions.class.getResourceAsStream(BUNDLED_RESOURCE)) {
			if (inputStream == null) return;
			String jsonContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			JsonElement root = JsonParser.parseString(jsonContent);
			if (!root.isJsonObject()) return;
			JsonObject rootObject = root.getAsJsonObject();
			String currentCategory = null;
			for (var mapEntry : rootObject.entrySet()) {
				String entryKey = mapEntry.getKey();
				// Track sections by comment keys to classify flowers
				if (entryKey.equals("_comment_sep_gen_flowers")) {
					currentCategory = "generating";
					continue;
				}
				if (entryKey.equals("_comment_sep_func_flowers")) {
					currentCategory = "functional";
					continue;
				}
				if (entryKey.equals("_comment_sep_mana_infra")) {
					currentCategory = null;
					continue;
				}
				if (entryKey.startsWith("_")) continue; // skip other comment keys
				JsonElement entryValue = mapEntry.getValue();
				List<String> descriptionLines = new ArrayList<>();
				if (entryValue.isJsonArray()) {
					for (JsonElement line : entryValue.getAsJsonArray()) {
						descriptionLines.add(line.getAsString());
					}
				} else if (entryValue.isJsonPrimitive()) {
					descriptionLines.add(entryValue.getAsString());
				}
				if (!descriptionLines.isEmpty()) {
					DESCRIPTIONS.put(entryKey, Collections.unmodifiableList(descriptionLines));
				}
				if (currentCategory != null) {
					CATEGORIES.put(entryKey, currentCategory);
				}
			}
		} catch (Exception exception) {
			// Silently fail - descriptions are cosmetic
		}
	}
	//endregion

	//region ACCESS
	/**
	 * Returns the description lines for the given item registry id (e.g. "botania:endoflame"),
	 * or an empty list if no description is bundled.
	 */
	public static List<String> get(String registryId) {
		ensureLoaded();
		return DESCRIPTIONS.getOrDefault(registryId, Collections.emptyList());
	}

	/**
	 * Returns the description lines for the given item stack's registry id.
	 * Handles floating_/potted_ prefixes by falling back to the base flower id.
	 */
	public static List<String> getForItem(net.minecraft.world.item.ItemStack stack) {
		ensureLoaded();
		if (stack.isEmpty()) return Collections.emptyList();
		ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) return Collections.emptyList();
		String fullRegistryId = itemId.toString();
		List<String> descriptionLines = DESCRIPTIONS.get(fullRegistryId);
		if (descriptionLines != null && !descriptionLines.isEmpty()) return descriptionLines;
		// Try base flower id for floating_/potted_ variants
		String itemPath = itemId.getPath();
		if (itemPath.startsWith("floating_")) {
			descriptionLines = DESCRIPTIONS.get("botania:" + itemPath.substring("floating_".length()));
			if (descriptionLines != null && !descriptionLines.isEmpty()) return descriptionLines;
		}
		if (itemPath.startsWith("potted_")) {
			descriptionLines = DESCRIPTIONS.get("botania:" + itemPath.substring("potted_".length()));
			if (descriptionLines != null && !descriptionLines.isEmpty()) return descriptionLines;
		}
		return Collections.emptyList();
	}

	/**
	 * Appends description lines (gray, non-italic) to the given tooltip list for the given item stack.
	 * No-op if no description is bundled for the item.
	 */
	public static void appendDescriptionLines(List<Component> tooltip, net.minecraft.world.item.ItemStack stack) {
		for (String descriptionLine : getForItem(stack)) {
			tooltip.add(Component.literal(descriptionLine).withStyle(ChatFormatting.GRAY));
		}
	}

	// Returns the flower category for the given registry id: "generating", "functional", or null.
	@org.jetbrains.annotations.Nullable
	public static String getCategory(String registryId) {
		ensureLoaded();
		return CATEGORIES.get(registryId);
	}

	/**
	 * Returns the flower category for the given item stack's registry id.
	 * Handles floating_/potted_ prefixes by falling back to the base flower id.
	 */
	@org.jetbrains.annotations.Nullable
	public static String getCategoryForItem(net.minecraft.world.item.ItemStack stack) {
		ensureLoaded();
		if (stack.isEmpty()) return null;
		ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) return null;
		String fullRegistryId = itemId.toString();
		String category = CATEGORIES.get(fullRegistryId);
		if (category != null) return category;
		// Try base flower id for floating_/potted_ variants
		String itemPath = itemId.getPath();
		if (itemPath.startsWith("floating_")) {
			category = CATEGORIES.get("botania:" + itemPath.substring("floating_".length()));
			if (category != null) return category;
		}
		if (itemPath.startsWith("potted_")) {
			category = CATEGORIES.get("botania:" + itemPath.substring("potted_".length()));
			if (category != null) return category;
		}
		return null;
	}
	//endregion
}
