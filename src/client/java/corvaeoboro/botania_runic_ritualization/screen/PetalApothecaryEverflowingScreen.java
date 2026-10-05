/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # PetalApothecaryEverflowingScreen.java
 * - Client GUI for the Petal Apothecary of the Everflowing, extending AbstractRitualScreen.
 * - Provides petal-apothecary-specific recipe loading, display items, and selection packets.
 * - Overrides renderRecipeTooltip with a rich tooltip showing flower icon, name, color-coded category,
 *   description lines, reagent, and ingredient icons.
 * - All shared layout (bars, ellipse, grid, inventory) is handled by the base class.
 */
package corvaeoboro.botania_runic_ritualization.screen;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;
import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlockEntity;
import corvaeoboro.botania_runic_ritualization.block.PetalApothecaryEverflowingBlockEntity;
import corvaeoboro.botania_runic_ritualization.client.FlowerDescriptions;
import corvaeoboro.botania_runic_ritualization.network.SelectPetalPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import vazkii.botania.api.recipe.PetalApothecaryRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PetalApothecaryEverflowingScreen extends AbstractRitualScreen<PetalApothecaryEverflowingMenu> {

	//region FIELD
	private final List<PetalApothecaryRecipe> recipes = new ArrayList<>();
	//endregion

	//region CONSTRUCT
	public PetalApothecaryEverflowingScreen(PetalApothecaryEverflowingMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}
	//endregion

	//region RECIPE
	@Override
	protected void loadRecipes() {
		recipes.clear();
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level != null) {
			RecipeManager recipeManager = minecraft.level.getRecipeManager();
			for (Recipe<?> recipe : recipeManager.getRecipes()) {
				if (recipe instanceof PetalApothecaryRecipe petalRecipe) {
					recipes.add(petalRecipe);
				}
			}
			// Sort by category (pure_daisy first, then generating, then functional, then uncategorized),
			// then by recipe id within each category
			recipes.sort(Comparator.comparingInt((PetalApothecaryRecipe r) -> {
				ItemStack out = ((Recipe<?>) r).getResultItem(minecraft.level.registryAccess());
				ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(out.getItem());
				if (itemId != null && "botania:pure_daisy".equals(itemId.toString())) return 0;
				String category = FlowerDescriptions.getCategoryForItem(out);
				if ("generating".equals(category)) return 1;
				if ("functional".equals(category)) return 2;
				return 3;
			}).thenComparing(recipe -> ((Recipe<?>) recipe).getId().toString()));
		}
	}

	//region CATEGORY
	@Override
	@org.jetbrains.annotations.Nullable
	protected String getRecipeCategory(Recipe<?> recipe) {
		ItemStack out = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
		ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(out.getItem());
		if (itemId != null && "botania:pure_daisy".equals(itemId.toString())) return "pure_daisy";
		return FlowerDescriptions.getCategoryForItem(out);
	}

	@Override
	@org.jetbrains.annotations.Nullable
	protected String getCategoryLabel(String categoryKey) {
		return switch (categoryKey) {
			case "generating" -> "Generating Flowers";
			case "functional" -> "Functional Flowers";
			default -> null;
		};
	}

	@Override
	protected int getCategoryColor(String categoryKey) {
		return switch (categoryKey) {
			case "generating" -> 0xFF55FF55;
			case "functional" -> 0xFF7FB7FF;
			default -> COL_TEXT_DIM;
		};
	}
	//endregion

	@Override
	protected ResourceLocation getSelectedRecipeId() {
		return menu.blockEntity != null ? menu.blockEntity.getSelectedRecipeId() : null;
	}

	@Override
	protected int getCurrentMana() {
		return menu.blockEntity != null ? menu.blockEntity.getCurrentManaPublic() : 0;
	}

	@Override
	protected int getManaCapacity() {
		return menu.blockEntity != null ? menu.blockEntity.getManaCapacity() : 0;
	}

	@Override
	protected int getManaToGet() {
		return menu.blockEntity != null ? menu.blockEntity.getManaToGetPublic() : 0;
	}

	@Override
	protected int getCooldown() {
		return menu.blockEntity != null ? menu.blockEntity.getCooldown() : 0;
	}

	@Override
	protected boolean isCraftingActive() {
		return menu.blockEntity != null && menu.blockEntity.isCraftingActive();
	}

	@Override
	protected float getCraftingProgress() {
		if (menu.blockEntity == null) return 0F;
		int cooldown = menu.blockEntity.getCooldown();
		if (cooldown <= 0) return 0F;
		return 1F - (float) cooldown / PetalApothecaryEverflowingBlockEntity.getRitualCooldownTicksConfig();
	}

	@Override
	protected List<PedestalOfLivingRockBlockEntity> getAdjacentPedestals() {
		return menu.blockEntity != null ? menu.blockEntity.getAdjacentPedestals() : List.of();
	}

	@Override
	protected boolean isInputEmpty() {
		return menu.blockEntity == null || menu.blockEntity.getSeedStack().isEmpty();
	}

	@Override
	protected ItemStack getInputGhostItem() {
		if (menu.blockEntity == null) return ItemStack.EMPTY;
		ResourceLocation selectedRecipeId = menu.blockEntity.getSelectedRecipeId();
		if (selectedRecipeId == null) return ItemStack.EMPTY;
		for (PetalApothecaryRecipe petalRecipe : recipes) {
			if (((Recipe<?>) petalRecipe).getId().equals(selectedRecipeId)) {
				ItemStack[] reagentItems = petalRecipe.getReagent().getItems();
				if (reagentItems.length > 0) return reagentItems[0];
				break;
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	protected Component getInputLabel() {
		return Component.translatable("gui.botania_runic_ritualization.label_seed");
	}

	@Override
	protected int[] getGradientPalette() {
		// Green theme: deep green -> verdant saturated green -> teal, cycling
		return new int[] {
				0xFF2EAA44, // deep green
				0xFF3FCB55, // verdant green
				0xFF4FE065, // saturated green
				0xFF5FE880, // bright green
				0xFF3FD4A0, // green-teal
				0xFF3FD4C8  // teal
		};
	}

	@Override
	protected List<? extends Recipe<?>> getRecipes() {
		return recipes;
	}

	@Override
	protected ItemStack getRecipeDisplayItem(Recipe<?> recipe) {
		return recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
	}

	@Override
	protected void sendSelectPacket(String recipeId, boolean clearing) {
		ClientPlayNetworking.send(BotaniaRunicRitualization.SELECT_PETAL_PACKET_ID,
				SelectPetalPayload.encode(menu.blockPos, recipeId));
	}
	//endregion

	//region TOOLTIP

	// A single line in the rich tooltip: an optional prefix text, an optional item icon, + text component + color.
	private static class RichTooltipLine {
		final ItemStack icon;
		final Component text;
		final int color;
		final Component prefixText;
		RichTooltipLine(ItemStack icon, Component text, int color) {
			this(icon, text, color, null);
		}
		RichTooltipLine(ItemStack icon, Component text, int color, Component prefixText) {
			this.icon = icon;
			this.text = text;
			this.color = color;
			this.prefixText = prefixText;
		}
	}

	// A pair of ingredient entries for 2-per-row layout. Either entry may be null.
	private static class IngredientPair {
		final ItemStack leftIcon;
		final Component leftText;
		final ItemStack rightIcon;
		final Component rightText;
		IngredientPair(ItemStack leftIcon, Component leftText, ItemStack rightIcon, Component rightText) {
			this.leftIcon = leftIcon;
			this.leftText = leftText;
			this.rightIcon = rightIcon;
			this.rightText = rightText;
		}
	}

	@Override
	protected void renderRecipeTooltip(GuiGraphics g, Recipe<?> recipe, int mouseX, int mouseY) {
		List<RichTooltipLine> lines = buildRichTooltipLines(recipe);
		List<IngredientPair> ingredientPairs = buildIngredientPairs(recipe);
		renderRichTooltip(g, lines, ingredientPairs, mouseX, mouseY);
	}

	/**
	 * Builds the rich tooltip header lines for a petal apothecary recipe:
	 * 1. Flower icon + flower name + color-coded category (generating/functional)
	 * 2. Description lines from FlowerDescriptions (gray)
	 * 3. "Ingredients:  [reagent icon] reagent name" - merges the reagent into the ingredients
	 *    label so it reads as the first ingredient (e.g. "Ingredients:  Wheat Seeds").
	 *    Remaining ingredients are handled via buildIngredientPairs for 2-per-row layout.
	 */
	private List<RichTooltipLine> buildRichTooltipLines(Recipe<?> recipe) {
		List<RichTooltipLine> lines = new ArrayList<>();
		ItemStack resultItem = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());

		// Line 1: flower icon + name + category
		MutableComponent nameLine = resultItem.getHoverName().copy();
		String category = FlowerDescriptions.getCategoryForItem(resultItem);
		if (category != null) {
			int categoryColor;
			String categoryLabel;
			if ("generating".equals(category)) {
				categoryColor = 0x55FF55;
				categoryLabel = "Generating";
			} else {
				categoryColor = 0x7FB7FF;
				categoryLabel = "Functional";
			}
			nameLine.append(Component.literal(" (" + categoryLabel + ")")
					.withStyle(style -> style.withColor(categoryColor)));
		}
		lines.add(new RichTooltipLine(resultItem, nameLine, 0xFFFFFFFF));

		// Description lines (gray, no icon) - word-wrapped for compact display
		for (String descriptionLine : FlowerDescriptions.getForItem(resultItem)) {
			for (String wrappedLine : wrapText(descriptionLine, 140)) {
				lines.add(new RichTooltipLine(ItemStack.EMPTY,
						Component.literal(wrappedLine).withStyle(ChatFormatting.GRAY), 0xFFAAAAAA));
			}
		}

		// Merged "Ingredients:" label + reagent on one line:
		// "Ingredients:  [reagent icon] reagent name" - reagent reads as the first ingredient.
		// If no reagent exists, just show the plain "Ingredients:" label.
		Component ingredientsLabel = Component.translatable("gui.botania_runic_ritualization.ingredients")
				.withStyle(s -> s.withColor(0xAAAAAA));
		if (recipe instanceof PetalApothecaryRecipe petalRecipe) {
			ItemStack[] reagentItems = petalRecipe.getReagent().getItems();
			if (reagentItems.length > 0) {
				ItemStack reagentIcon = reagentItems[0];
				Component prefixWithSpaces = Component.literal("")
						.append(ingredientsLabel)
						.append(Component.literal("  "));
				lines.add(new RichTooltipLine(reagentIcon, reagentIcon.getHoverName(), 0xFFCCCCCC, prefixWithSpaces));
			} else {
				lines.add(new RichTooltipLine(ItemStack.EMPTY, ingredientsLabel, 0xFFAAAAAA));
			}
		} else {
			lines.add(new RichTooltipLine(ItemStack.EMPTY, ingredientsLabel, 0xFFAAAAAA));
		}

		return lines;
	}

	// Builds ingredient pairs for 2-per-row layout, collapsing duplicates into count-prefixed entries.
	// e.g. two "Mystical Red Petal" ingredients become a single entry "2x Red Petal" (the "Mystical " prefix is stripped).
	private List<IngredientPair> buildIngredientPairs(Recipe<?> recipe) {
		// Collect and collapse ingredients by item registry id
		List<ItemStack> collapsedItems = new ArrayList<>();
		List<Integer> collapsedCounts = new ArrayList<>();
		for (Ingredient ingredient : recipe.getIngredients()) {
			if (ingredient.isEmpty()) continue;
			ItemStack[] ingredientItems = ingredient.getItems();
			if (ingredientItems.length == 0) continue;
			ItemStack ingredientStack = ingredientItems[0];
			// Check if this item is already in the collapsed list
			int foundIndex = -1;
			for (int existingIndex = 0; existingIndex < collapsedItems.size(); existingIndex++) {
				if (ItemStack.isSameItemSameTags(collapsedItems.get(existingIndex), ingredientStack)) {
					foundIndex = existingIndex;
					break;
				}
			}
			if (foundIndex >= 0) {
				collapsedCounts.set(foundIndex, collapsedCounts.get(foundIndex) + 1);
			} else {
				collapsedItems.add(ingredientStack);
				collapsedCounts.add(1);
			}
		}

		// Build text components with count prefix
		// Strip "Mystical " prefix from petal names for compact display (e.g. "Mystical Magenta Petal" -> "Magenta Petal")
		List<ItemStack> iconStacks = new ArrayList<>();
		List<Component> textComponents = new ArrayList<>();
		for (int itemIndex = 0; itemIndex < collapsedItems.size(); itemIndex++) {
			int count = collapsedCounts.get(itemIndex);
			ItemStack iconStack = collapsedItems.get(itemIndex);
			String itemName = iconStack.getHoverName().getString();
			if (itemName.startsWith("Mystical ")) {
				itemName = itemName.substring("Mystical ".length());
			}
			String displayText = count > 1 ? count + "x " + itemName : itemName;
			iconStacks.add(iconStack);
			textComponents.add(Component.literal(displayText));
		}

		// Pair them 2 per row
		List<IngredientPair> ingredientPairs = new ArrayList<>();
		for (int pairIndex = 0; pairIndex < iconStacks.size(); pairIndex += 2) {
			ItemStack leftIcon = iconStacks.get(pairIndex);
			Component leftText = textComponents.get(pairIndex);
			ItemStack rightIcon = (pairIndex + 1 < iconStacks.size()) ? iconStacks.get(pairIndex + 1) : ItemStack.EMPTY;
			Component rightText = (pairIndex + 1 < iconStacks.size()) ? textComponents.get(pairIndex + 1) : null;
			ingredientPairs.add(new IngredientPair(leftIcon, leftText, rightIcon, rightText));
		}
		return ingredientPairs;
	}

	/**
	 * Renders a rich tooltip with compact line spacing (12px per line).
	 * Header lines are full-width with icons. Ingredient pairs are laid out 2 per row.
	 */
	private void renderRichTooltip(GuiGraphics g, List<RichTooltipLine> headerLines,
			List<IngredientPair> ingredientPairs, int mouseX, int mouseY) {
		if (headerLines.isEmpty() && ingredientPairs.isEmpty()) return;

		int iconSpace = 18; // 16px icon + 2px gap
		int lineH = 12;     // compact vertical spacing
		int pairColWidth = 80; // per-column width for ingredient pairs (icon + text)

		// Compute tooltip width
		int width = 0;
		for (RichTooltipLine line : headerLines) {
			int lineWidth = (line.prefixText != null ? font.width(line.prefixText) : 0)
					+ (line.icon != null && !line.icon.isEmpty() ? iconSpace : 0)
					+ font.width(line.text);
			if (lineWidth > width) width = lineWidth;
		}
		// Check ingredient pair rows width
		int pairRowWidth = 2 * pairColWidth;
		if (pairRowWidth > width) width = pairRowWidth;

		// Compute tooltip height
		int totalLines = headerLines.size() + ingredientPairs.size();
		int height = totalLines * lineH + 8;

		// Position the tooltip
		int x = mouseX + 12;
		int y = mouseY - 12;
		if (x + width + 8 > this.width) {
			x = mouseX - width - 16;
		}
		if (x < 4) x = 4;
		if (y < 4) y = mouseY + 16;
		if (y + height > this.height - 4) {
			y = this.height - height - 4;
		}

		// Draw background
		int x0 = x - 4;
		int y0 = y - 4;
		int x1 = x + width + 4;
		int y1 = y + height;
		int bg = 0xF0100010;
		int borderLight = 0xFF5000FF;
		int borderDark = 0xFF28007F;

		// Vanilla tooltip pattern: translate to Z=400 so both background AND content
		// are above GUI items at Z=150. drawManaged flushes pending items before drawing
		// the background, then we continue rendering content at the same elevated Z.
		g.pose().pushPose();
		g.pose().translate(0, 0, 400);
		g.drawManaged(() -> {
			g.fill(x0, y0, x1, y1, bg);
			g.fill(x0, y0, x1, y0 + 1, borderLight);
			g.fill(x0, y0, x0 + 1, y1, borderLight);
			g.fill(x0, y1 - 1, x1, y1, borderDark);
			g.fill(x1 - 1, y0, x1, y1, borderDark);
		});

		// Draw header lines
		int curY = y + 4;
		for (RichTooltipLine line : headerLines) {
			int lx = x;
			// Optional prefix text rendered before the icon (e.g. "Ingredients:  " before reagent icon)
			if (line.prefixText != null) {
				g.drawString(font, line.prefixText, lx, curY + 2, line.color, false);
				lx += font.width(line.prefixText);
			}
			if (line.icon != null && !line.icon.isEmpty()) {
				// Render icon at 12px size (slightly smaller for compact layout)
				g.pose().pushPose();
				g.pose().scale(0.75F, 0.75F, 1.0F);
				g.renderFakeItem( line.icon, (int) (lx / 0.75F), (int) (curY / 0.75F));
				g.pose().popPose();
				lx += iconSpace;
			}
			g.drawString(font, line.text, lx, curY + 2, line.color, false);
			curY += lineH;
		}

		// Draw ingredient pairs (2 per row) - slightly smaller font for compact listing
		float ingredientScale = 0.85F;
		for (IngredientPair pair : ingredientPairs) {
			g.pose().pushPose();
			g.pose().scale(ingredientScale, ingredientScale, 1.0F);
			int scaledX = (int) (x / ingredientScale);
			int scaledY = (int) (curY / ingredientScale);
			int scaledIconSpace = (int) (iconSpace / ingredientScale);
			int scaledPairColWidth = (int) (pairColWidth / ingredientScale);
			// Left entry
			if (pair.leftIcon != null && !pair.leftIcon.isEmpty()) {
				g.pose().pushPose();
				g.pose().scale(0.75F, 0.75F, 1.0F);
				g.renderFakeItem( pair.leftIcon, (int) (scaledX / 0.75F), (int) (scaledY / 0.75F));
				g.pose().popPose();
			}
			if (pair.leftText != null) {
				g.drawString(font, pair.leftText, scaledX + scaledIconSpace, scaledY + 2, 0xFFFFFFFF, false);
			}
			// Right entry
			if (pair.rightIcon != null && !pair.rightIcon.isEmpty()) {
				int scaledRightX = scaledX + scaledPairColWidth;
				g.pose().pushPose();
				g.pose().scale(0.75F, 0.75F, 1.0F);
				g.renderFakeItem( pair.rightIcon, (int) (scaledRightX / 0.75F), (int) (scaledY / 0.75F));
				g.pose().popPose();
				if (pair.rightText != null) {
					g.drawString(font, pair.rightText, scaledRightX + scaledIconSpace, scaledY + 2, 0xFFFFFFFF, false);
				}
			}
			g.pose().popPose();
			curY += lineH;
		}
		g.pose().popPose();
	}
	//endregion
}
