/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # BotanicalBreweryAludelScreen.java
 * - Client GUI for the Botanical Brewery Aludel, extending AbstractRitualScreen.
 * - Provides brewery-specific recipe loading, display items (brew outputs), and selection packets.
 * - Overrides getOutputSlotCount to return 4 and renderRecipeTooltip for brew-specific tooltips.
 * - All shared layout (bars, ellipse, grid, inventory) is handled by the base class.
 */
package corvaeoboro.botania_runic_ritualization.screen;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;
import corvaeoboro.botania_runic_ritualization.block.BotanicalBreweryAludelBlockEntity;
import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlockEntity;
import corvaeoboro.botania_runic_ritualization.client.BrewDescriptions;
import corvaeoboro.botania_runic_ritualization.network.SelectBrewPayload;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import vazkii.botania.api.brew.Brew;
import vazkii.botania.api.recipe.BotanicalBreweryRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BotanicalBreweryAludelScreen extends AbstractRitualScreen<BotanicalBreweryAludelMenu> {

	//region FIELD
	private final List<BotanicalBreweryRecipe> recipes = new ArrayList<>();
	private static final ItemStack VIAL_GHOST = resolveVialItem();
	//endregion

	//region CONSTRUCT
	public BotanicalBreweryAludelScreen(BotanicalBreweryAludelMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	private static ItemStack resolveVialItem() {
		var vialItem = BuiltInRegistries.ITEM.get(new ResourceLocation("botania", "vial"));
		return vialItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(vialItem);
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
				if (recipe instanceof BotanicalBreweryRecipe breweryRecipe) {
					recipes.add(breweryRecipe);
				}
			}
			// Sort by category (buff, utility, special, then uncategorized), then by recipe id
			recipes.sort(Comparator.comparingInt((BotanicalBreweryRecipe r) -> {
				int cat = BrewDescriptions.getCategoryForBrew(r.getBrew());
				if (cat == BrewDescriptions.CAT_BUFF) return 0;
				if (cat == BrewDescriptions.CAT_UTILITY) return 1;
				if (cat == BrewDescriptions.CAT_SPECIAL) return 2;
				return 3;
			}).thenComparing(recipe -> ((Recipe<?>) recipe).getId().toString()));
		}
	}

	//region CATEGORY
	@Override
	@org.jetbrains.annotations.Nullable
	protected String getRecipeCategory(Recipe<?> recipe) {
		if (recipe instanceof BotanicalBreweryRecipe breweryRecipe) {
			int cat = BrewDescriptions.getCategoryForBrew(breweryRecipe.getBrew());
			return switch (cat) {
				case BrewDescriptions.CAT_BUFF -> "buff";
				case BrewDescriptions.CAT_UTILITY -> "utility";
				case BrewDescriptions.CAT_SPECIAL -> "special";
				default -> null;
			};
		}
		return null;
	}

	@Override
	@org.jetbrains.annotations.Nullable
	protected String getCategoryLabel(String categoryKey) {
		return switch (categoryKey) {
			case "buff" -> "Buff Brews";
			case "utility" -> "Utility Brews";
			case "special" -> "Special Brews";
			default -> null;
		};
	}

	@Override
	protected int getCategoryColor(String categoryKey) {
		return switch (categoryKey) {
			case "buff" -> BrewDescriptions.getCategoryColor(BrewDescriptions.CAT_BUFF);
			case "utility" -> BrewDescriptions.getCategoryColor(BrewDescriptions.CAT_UTILITY);
			case "special" -> BrewDescriptions.getCategoryColor(BrewDescriptions.CAT_SPECIAL);
			default -> COL_TEXT_DIM;
		};
	}
	//endregion

	@Override
	protected int getOutputSlotCount() {
		return BotanicalBreweryAludelMenu.OUTPUT_SLOT_COUNT;
	}

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
		return 1F - (float) cooldown / BotanicalBreweryAludelBlockEntity.getRitualCooldownTicksConfig();
	}

	@Override
	protected List<PedestalOfLivingRockBlockEntity> getAdjacentPedestals() {
		return menu.blockEntity != null ? menu.blockEntity.getAdjacentPedestals() : List.of();
	}

	@Override
	protected boolean isInputEmpty() {
		return menu.blockEntity == null || menu.blockEntity.getContainerStack().isEmpty();
	}

	@Override
	protected ItemStack getInputGhostItem() {
		return VIAL_GHOST;
	}

	@Override
	protected Component getInputLabel() {
		return Component.translatable("gui.botania_runic_ritualization.label_container");
	}

	@Override
	protected int[] getGradientPalette() {
 
		return new int[] {
				0xFF8B5FB8, // muted purple
				0xFF7B5FC8, // purple-blue
				0xFF6B6FD4, // bluish purple
				0xFF7B7FD4, // light bluish purple
				0xFF8B8FE0, // muted lavender-blue
				0xFF9B9FE8  // light periwinkle
		};
	}

	@Override
	protected List<? extends Recipe<?>> getRecipes() {
		return recipes;
	}

	@Override
	protected ItemStack getRecipeDisplayItem(Recipe<?> recipe) {
		if (recipe instanceof BotanicalBreweryRecipe breweryRecipe) {
			ItemStack vialStack = VIAL_GHOST.isEmpty() ? new ItemStack(Items.GLASS_BOTTLE) : VIAL_GHOST;
			return breweryRecipe.getOutput(vialStack);
		}
		return ItemStack.EMPTY;
	}

	@Override
	protected void sendSelectPacket(String recipeId, boolean clearing) {
		ClientPlayNetworking.send(BotaniaRunicRitualization.SELECT_BREW_PACKET_ID,
				SelectBrewPayload.encode(menu.blockPos, recipeId));
	}
	//endregion

	//region TOOLTIP

	// A single line in the rich tooltip: an optional item icon + text component + color.  
	private static class RichTooltipLine {
		final ItemStack icon;
		final Component text;
		final int color;
		RichTooltipLine(ItemStack icon, Component text, int color) {
			this.icon = icon;
			this.text = text;
			this.color = color;
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
		if (!(recipe instanceof BotanicalBreweryRecipe breweryRecipe)) return;
		List<RichTooltipLine> lines = buildRichTooltipLines(breweryRecipe);
		List<IngredientPair> ingredientPairs = buildIngredientPairs(breweryRecipe);
		renderRichTooltip(g, lines, ingredientPairs, mouseX, mouseY);
	}

	/**
	 * Builds the rich tooltip header lines for a brewery recipe:
	 * 1. Brew output icon + brew name + category (color-coded)
	 * 2. Effect summary (word-wrapped)
	 * 3. Mana cost
	 * 4. "Ingredients:" label
	 */
	private List<RichTooltipLine> buildRichTooltipLines(BotanicalBreweryRecipe breweryRecipe) {
		List<RichTooltipLine> lines = new ArrayList<>();
		Brew brew = breweryRecipe.getBrew();
		ItemStack vialStack = VIAL_GHOST.isEmpty() ? new ItemStack(Items.GLASS_BOTTLE) : VIAL_GHOST;
		ItemStack outputItem = breweryRecipe.getOutput(vialStack);

		// Line 1: brew icon + name + category
		String brewName = brew.getTranslationKey().isEmpty()
				? ((Recipe<?>) breweryRecipe).getId().getPath()
				: Component.translatable(brew.getTranslationKey()).getString();
		MutableComponent nameLine = Component.literal(brewName);
		int category = BrewDescriptions.getCategoryForBrew(brew);
		if (category != BrewDescriptions.CAT_NONE) {
			String categoryLabel = BrewDescriptions.getCategoryLabel(category);
			int categoryColor = BrewDescriptions.getCategoryColor(category);
			nameLine.append(Component.literal(" (" + categoryLabel + ")")
					.withStyle(style -> style.withColor(categoryColor)));
		}
		lines.add(new RichTooltipLine(outputItem, nameLine, 0xFFFFFFFF));

		// Line 2: effect summary (word-wrapped)
		String effectSummary = BrewDescriptions.getEffectSummary(brew, outputItem);
		if (effectSummary != null && !effectSummary.isEmpty()) {
			for (String wrappedLine : wrapText(effectSummary, 140)) {
				lines.add(new RichTooltipLine(ItemStack.EMPTY,
						Component.literal(wrappedLine).withStyle(ChatFormatting.GRAY), 0xFFAAAAAA));
			}
		}

		// Line 3: mana cost
		lines.add(new RichTooltipLine(ItemStack.EMPTY,
				Component.translatable("gui.botania_runic_ritualization.cost", breweryRecipe.getManaUsage())
						.withStyle(style -> style.withColor(0x3FB3FF)), 0xFF3FB3FF));

		// Line 4: "Ingredients:" label
		lines.add(new RichTooltipLine(ItemStack.EMPTY,
				Component.translatable("gui.botania_runic_ritualization.ingredients")
						.withStyle(style -> style.withColor(0xAAAAAA)), 0xFFAAAAAA));

		return lines;
	}

	// Builds ingredient pairs for 2-per-row layout, collapsing duplicates into count-prefixed entries.
	private List<IngredientPair> buildIngredientPairs(BotanicalBreweryRecipe breweryRecipe) {
		List<ItemStack> collapsedItems = new ArrayList<>();
		List<Integer> collapsedCounts = new ArrayList<>();
		for (Ingredient ingredient : ((Recipe<?>) breweryRecipe).getIngredients()) {
			if (ingredient.isEmpty()) continue;
			ItemStack[] ingredientItems = ingredient.getItems();
			if (ingredientItems.length == 0) continue;
			ItemStack ingredientStack = ingredientItems[0];
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

		List<ItemStack> iconStacks = new ArrayList<>();
		List<Component> textComponents = new ArrayList<>();
		for (int itemIndex = 0; itemIndex < collapsedItems.size(); itemIndex++) {
			int count = collapsedCounts.get(itemIndex);
			ItemStack iconStack = collapsedItems.get(itemIndex);
			String itemName = iconStack.getHoverName().getString();
			String displayText = count > 1 ? count + "x " + itemName : itemName;
			iconStacks.add(iconStack);
			textComponents.add(Component.literal(displayText));
		}

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

	// Renders a rich tooltip with compact line spacing (12px per line).
	private void renderRichTooltip(GuiGraphics g, List<RichTooltipLine> headerLines,
			List<IngredientPair> ingredientPairs, int mouseX, int mouseY) {
		if (headerLines.isEmpty() && ingredientPairs.isEmpty()) return;

		int iconSpace = 18;
		int lineH = 12;
		int pairColWidth = 80;

		int width = 0;
		for (RichTooltipLine line : headerLines) {
			int lineWidth = (line.icon != null && !line.icon.isEmpty() ? iconSpace : 0)
					+ font.width(line.text);
			if (lineWidth > width) width = lineWidth;
		}
		int pairRowWidth = 2 * pairColWidth;
		if (pairRowWidth > width) width = pairRowWidth;

		int totalLines = headerLines.size() + ingredientPairs.size();
		int height = totalLines * lineH + 8;

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

		int curY = y + 4;
		for (RichTooltipLine line : headerLines) {
			int lx = x;
			if (line.icon != null && !line.icon.isEmpty()) {
				g.pose().pushPose();
				g.pose().scale(0.75F, 0.75F, 1.0F);
				g.renderFakeItem( line.icon, (int) (lx / 0.75F), (int) (curY / 0.75F));
				g.pose().popPose();
				lx += iconSpace;
			}
			g.drawString(font, line.text, lx, curY + 2, line.color, false);
			curY += lineH;
		}

		for (IngredientPair pair : ingredientPairs) {
			if (pair.leftIcon != null && !pair.leftIcon.isEmpty()) {
				g.pose().pushPose();
				g.pose().scale(0.75F, 0.75F, 1.0F);
				g.renderFakeItem( pair.leftIcon, (int) (x / 0.75F), (int) (curY / 0.75F));
				g.pose().popPose();
			}
			if (pair.leftText != null) {
				g.drawString(font, pair.leftText, x + iconSpace, curY + 2, 0xFFFFFFFF, false);
			}
			if (pair.rightIcon != null && !pair.rightIcon.isEmpty()) {
				int rightX = x + pairColWidth;
				g.pose().pushPose();
				g.pose().scale(0.75F, 0.75F, 1.0F);
				g.renderFakeItem( pair.rightIcon, (int) (rightX / 0.75F), (int) (curY / 0.75F));
				g.pose().popPose();
				if (pair.rightText != null) {
					g.drawString(font, pair.rightText, rightX + iconSpace, curY + 2, 0xFFFFFFFF, false);
				}
			}
			curY += lineH;
		}
		g.pose().popPose();
	}
	//endregion
}
