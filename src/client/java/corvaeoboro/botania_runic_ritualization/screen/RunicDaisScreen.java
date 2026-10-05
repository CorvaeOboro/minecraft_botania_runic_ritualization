/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # RunicDaisScreen.java
 * - Client GUI for the Runic Altar Dais, extending AbstractRitualScreen.
 * - Provides runic-altar-specific recipe loading, display items, and selection packets.
 * - All shared layout (bars, ellipse, grid, inventory) is handled by the base class.
 */
package corvaeoboro.botania_runic_ritualization.screen;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;
import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlockEntity;
import corvaeoboro.botania_runic_ritualization.block.RunicAltarDaisBlockEntity;
import corvaeoboro.botania_runic_ritualization.client.FlowerDescriptions;
import corvaeoboro.botania_runic_ritualization.client.RuneDescriptions;
import corvaeoboro.botania_runic_ritualization.network.SelectRunePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import vazkii.botania.api.recipe.RunicAltarRecipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RunicDaisScreen extends AbstractRitualScreen<RunicDaisMenu> {

	//region FIELD
	private final List<RunicAltarRecipe> recipes = new ArrayList<>();
	private static final Item LIVINGROCK_ITEM = resolveLivingrockItem();
	//endregion

	//region CONSTRUCT
	public RunicDaisScreen(RunicDaisMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
	}

	private static Item resolveLivingrockItem() {
		Item livingrockItem = BuiltInRegistries.ITEM.get(new ResourceLocation("botania", "livingrock"));
		return livingrockItem == Items.AIR ? Items.AIR : livingrockItem;
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
				if (recipe instanceof RunicAltarRecipe runicAltarRecipe) {
					recipes.add(runicAltarRecipe);
				}
			}
			// Sort by tier (T1 elemental, T2 seasonal, T3 sins, special), then by recipe id
			recipes.sort(Comparator.comparingInt((RunicAltarRecipe r) -> {
				ItemStack out = ((Recipe<?>) r).getResultItem(minecraft.level.registryAccess());
				int tier = RuneDescriptions.getTierForItem(out);
				if (tier == RuneDescriptions.TIER_NONE) tier = RuneDescriptions.TIER_SPECIAL;
				return tier;
			}).thenComparing(recipe -> ((Recipe<?>) recipe).getId().toString()));
		}
	}

	//region CATEGORY
	@Override
	@org.jetbrains.annotations.Nullable
	protected String getRecipeCategory(Recipe<?> recipe) {
		ItemStack out = recipe.getResultItem(Minecraft.getInstance().level.registryAccess());
		int tier = RuneDescriptions.getTierForItem(out);
		return switch (tier) {
			case RuneDescriptions.TIER_1_VAL -> "t1_elemental";
			case RuneDescriptions.TIER_2_VAL -> "t2_seasonal";
			case RuneDescriptions.TIER_3_VAL -> "t3_sins";
			case RuneDescriptions.TIER_SPECIAL -> "special";
			default -> "other";
		};
	}

	@Override
	@org.jetbrains.annotations.Nullable
	protected String getCategoryLabel(String categoryKey) {
		return switch (categoryKey) {
			case "t1_elemental" -> "Tier 1 - Elemental";
			case "t2_seasonal" -> "Tier 2 - Seasonal";
			case "t3_sins" -> "Tier 3 - Sins";
			case "special" -> "Special";
			default -> null;
		};
	}

	@Override
	protected int getCategoryColor(String categoryKey) {
		return switch (categoryKey) {
			case "t1_elemental" -> RuneDescriptions.getTierColor(RuneDescriptions.TIER_1_VAL);
			case "t2_seasonal" -> RuneDescriptions.getTierColor(RuneDescriptions.TIER_2_VAL);
			case "t3_sins" -> RuneDescriptions.getTierColor(RuneDescriptions.TIER_3_VAL);
			case "special" -> RuneDescriptions.getTierColor(RuneDescriptions.TIER_SPECIAL);
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
		// During processing phase: progress counts up toward completion
		int craftProgress = menu.blockEntity.getCraftProgress();
		if (craftProgress > 0) {
			return (float) craftProgress / RunicAltarDaisBlockEntity.getCraftDurationTicksConfig();
		}
		// During post-craft cooldown: show cooldown progress
		int cooldown = menu.blockEntity.getCooldown();
		if (cooldown <= 0) return 0F;
		return 1F - (float) cooldown / RunicAltarDaisBlockEntity.getRitualCooldownTicksConfig();
	}

	@Override
	protected List<PedestalOfLivingRockBlockEntity> getAdjacentPedestals() {
		return menu.blockEntity != null ? menu.blockEntity.getAdjacentPedestals() : List.of();
	}

	@Override
	protected boolean isInputEmpty() {
		return menu.blockEntity == null || menu.blockEntity.getLivingrockStack().isEmpty();
	}

	@Override
	protected ItemStack getInputGhostItem() {
		return new ItemStack(LIVINGROCK_ITEM);
	}

	@Override
	protected Component getInputLabel() {
		return Component.translatable("gui.botania_runic_ritualization.label_livingrock");
	}

	@Override
	protected int[] getGradientPalette() {
		// Blue theme: deep blue -> teal -> lighter blue, cycling
		return new int[] {
				0xFF3B6FD4, // deep blue
				0xFF4A8FD4, // mid blue
				0xFF5FB3D4, // blue-teal
				0xFF3FD4C0, // teal
				0xFF5FD4E8, // light teal
				0xFF7FD4FF  // light blue
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
		ClientPlayNetworking.send(BotaniaRunicRitualization.SELECT_RUNE_PACKET_ID,
				SelectRunePayload.encode(menu.blockPos, recipeId));
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
		final int leftColor;
		final ItemStack rightIcon;
		final Component rightText;
		final int rightColor;
		IngredientPair(ItemStack leftIcon, Component leftText, int leftColor,
				ItemStack rightIcon, Component rightText, int rightColor) {
			this.leftIcon = leftIcon;
			this.leftText = leftText;
			this.leftColor = leftColor;
			this.rightIcon = rightIcon;
			this.rightText = rightText;
			this.rightColor = rightColor;
		}
	}

	// A group of rune ingredient pairs all belonging to the same tier.
	private static class RuneTierGroup {
		final int tier;
		final List<IngredientPair> pairs;
		RuneTierGroup(int tier, List<IngredientPair> pairs) {
			this.tier = tier;
			this.pairs = pairs;
		}
	}

	@Override
	protected void renderRecipeTooltip(GuiGraphics g, Recipe<?> recipe, int mouseX, int mouseY) {
		List<RichTooltipLine> lines = buildRichTooltipLines(recipe);
		List<IngredientPair> basePairs = buildIngredientPairs(recipe, false);
		List<RuneTierGroup> runeGroups = buildRuneTierGroups(recipe);
		renderRichTooltip(g, lines, basePairs, runeGroups, mouseX, mouseY);
	}

	/**
	 * Builds the rich tooltip header lines for a runic altar recipe:
	 * 1. Rune icon + rune name + tier classification (color-coded, short label e.g. "T1")
	 * 2. Mana
	 * 3. Description lines from descriptions.json (gray, word-wrapped)
	 */
	private List<RichTooltipLine> buildRichTooltipLines(Recipe<?> recipe) {
		List<RichTooltipLine> lines = new ArrayList<>();
		ItemStack out = getRecipeDisplayItem(recipe);

		// Classify the rune tier
		int tier = RuneDescriptions.getTierForItem(out);
		if (tier == RuneDescriptions.TIER_NONE) tier = RuneDescriptions.TIER_SPECIAL;

		// Line 1: rune icon + name + tier label
		MutableComponent nameLine = out.getHoverName().copy();
		String tierLabel = RuneDescriptions.getTierLabel(tier);
		int tierColor = RuneDescriptions.getTierColor(tier);
		nameLine.append(Component.literal(" (" + tierLabel + ")")
				.withStyle(s -> s.withColor(tierColor)));
		lines.add(new RichTooltipLine(out, nameLine, 0xFFFFFFFF));

		// Line 2: mana
		if (recipe instanceof RunicAltarRecipe rar) {
			lines.add(new RichTooltipLine(ItemStack.EMPTY,
					Component.translatable("gui.botania_runic_ritualization.cost", rar.getManaUsage())
							.withStyle(s -> s.withColor(0x3FB3FF)), 0xFF3FB3FF));
		}

		// Description lines (gray, no icon) - word-wrapped for compact display
		for (String descriptionLine : FlowerDescriptions.getForItem(out)) {
			for (String wrappedLine : wrapText(descriptionLine, 140)) {
				lines.add(new RichTooltipLine(ItemStack.EMPTY,
						Component.literal(wrappedLine).withStyle(ChatFormatting.GRAY), 0xFFAAAAAA));
			}
		}

		return lines;
	}

	/**
	 * Builds ingredient pairs for 2-per-row layout, collapsing duplicates into count-prefixed entries.
	 * If runesOnly is true, only rune ingredients are included; otherwise only non-rune ingredients.
	 * Rune ingredients get a tier-colored text, base materials get white text.
	 */
	private List<IngredientPair> buildIngredientPairs(Recipe<?> recipe, boolean runesOnly) {
		// Collect and collapse ingredients by item identity
		List<ItemStack> collapsed = new ArrayList<>();
		List<Integer> counts = new ArrayList<>();
		for (Ingredient ing : recipe.getIngredients()) {
			if (ing.isEmpty()) continue;
			ItemStack[] items = ing.getItems();
			if (items.length == 0) continue;
			ItemStack item = items[0];
			boolean isRune = RuneDescriptions.isRune(item);
			if (runesOnly != isRune) continue;

			int foundIdx = -1;
			for (int j = 0; j < collapsed.size(); j++) {
				if (ItemStack.isSameItemSameTags(collapsed.get(j), item)) {
					foundIdx = j;
					break;
				}
			}
			if (foundIdx >= 0) {
				counts.set(foundIdx, counts.get(foundIdx) + 1);
			} else {
				collapsed.add(item);
				counts.add(1);
			}
		}

		// Build text components with count prefix, colored by tier for runes
		List<ItemStack> icons = new ArrayList<>();
		List<Component> texts = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		for (int i = 0; i < collapsed.size(); i++) {
			int count = counts.get(i);
			ItemStack icon = collapsed.get(i);
			String name = icon.getHoverName().getString();
			String text = count > 1 ? count + "x " + name : name;
			icons.add(icon);
			texts.add(Component.literal(text));
			if (runesOnly) {
				int tier = RuneDescriptions.getTierForItem(icon);
				colors.add(RuneDescriptions.getTierColor(tier));
			} else {
				colors.add(0xFFFFFFFF);
			}
		}

		// Pair them 2 per row
		List<IngredientPair> pairs = new ArrayList<>();
		for (int i = 0; i < icons.size(); i += 2) {
			ItemStack leftIcon = icons.get(i);
			Component leftText = texts.get(i);
			int leftColor = colors.get(i);
			ItemStack rightIcon = (i + 1 < icons.size()) ? icons.get(i + 1) : ItemStack.EMPTY;
			Component rightText = (i + 1 < icons.size()) ? texts.get(i + 1) : null;
			int rightColor = (i + 1 < icons.size()) ? colors.get(i + 1) : 0xFFFFFFFF;
			pairs.add(new IngredientPair(leftIcon, leftText, leftColor, rightIcon, rightText, rightColor));
		}
		return pairs;
	}

	/**
	 * Builds rune ingredient pairs grouped by tier (T1, T2, T3), in ascending tier order.
	 * Each group contains collapsed ingredient pairs for runes of that tier only.
	 * Empty tiers are skipped. Used to render runes in clustered subsections with
	 * blank line separators between tiers.
	 */
	private List<RuneTierGroup> buildRuneTierGroups(Recipe<?> recipe) {
		// Collect rune ingredients per tier
		List<List<ItemStack>> perTierIcons = new ArrayList<>();
		List<List<Integer>> perTierCounts = new ArrayList<>();
		for (int t = 0; t <= RuneDescriptions.TIER_3_VAL; t++) {
			perTierIcons.add(new ArrayList<>());
			perTierCounts.add(new ArrayList<>());
		}

		for (Ingredient ing : recipe.getIngredients()) {
			if (ing.isEmpty()) continue;
			ItemStack[] items = ing.getItems();
			if (items.length == 0) continue;
			ItemStack item = items[0];
			if (!RuneDescriptions.isRune(item)) continue;
			int tier = RuneDescriptions.getTierForItem(item);
			if (tier < RuneDescriptions.TIER_1_VAL || tier > RuneDescriptions.TIER_3_VAL) continue;

			List<ItemStack> icons = perTierIcons.get(tier);
			List<Integer> counts = perTierCounts.get(tier);
			int foundIdx = -1;
			for (int j = 0; j < icons.size(); j++) {
				if (ItemStack.isSameItemSameTags(icons.get(j), item)) {
					foundIdx = j;
					break;
				}
			}
			if (foundIdx >= 0) {
				counts.set(foundIdx, counts.get(foundIdx) + 1);
			} else {
				icons.add(item);
				counts.add(1);
			}
		}

		// Build tier groups in ascending order (T1, T2, T3), skipping empty tiers
		List<RuneTierGroup> groups = new ArrayList<>();
		for (int t = RuneDescriptions.TIER_1_VAL; t <= RuneDescriptions.TIER_3_VAL; t++) {
			List<ItemStack> icons = perTierIcons.get(t);
			if (icons.isEmpty()) continue;
			List<Integer> counts = perTierCounts.get(t);
			int tierColor = RuneDescriptions.getTierColor(t);

			List<ItemStack> pairIcons = new ArrayList<>();
			List<Component> pairTexts = new ArrayList<>();
			for (int i = 0; i < icons.size(); i++) {
				int count = counts.get(i);
				ItemStack icon = icons.get(i);
				String name = icon.getHoverName().getString();
				String text = count > 1 ? count + "x " + name : name;
				pairIcons.add(icon);
				pairTexts.add(Component.literal(text));
			}

			List<IngredientPair> pairs = new ArrayList<>();
			for (int i = 0; i < pairIcons.size(); i += 2) {
				ItemStack leftIcon = pairIcons.get(i);
				Component leftText = pairTexts.get(i);
				ItemStack rightIcon = (i + 1 < pairIcons.size()) ? pairIcons.get(i + 1) : ItemStack.EMPTY;
				Component rightText = (i + 1 < pairIcons.size()) ? pairTexts.get(i + 1) : null;
				pairs.add(new IngredientPair(leftIcon, leftText, tierColor, rightIcon, rightText, tierColor));
			}
			groups.add(new RuneTierGroup(t, pairs));
		}
		return groups;
	}

	/**
	 * Renders a rich tooltip with compact line spacing (12px per line).
	 * Header lines are full-width with icons. Base material pairs are laid out 2 per row.
	 * Rune pairs are grouped by tier (T1, T2, T3) with a blank line between each tier group.
	 */
	private void renderRichTooltip(GuiGraphics g, List<RichTooltipLine> headerLines,
			List<IngredientPair> basePairs, List<RuneTierGroup> runeGroups,
			int mouseX, int mouseY) {
		if (headerLines.isEmpty() && basePairs.isEmpty() && runeGroups.isEmpty()) return;

		int iconSpace = 18;
		int lineH = 12;
		int pairColWidth = 80;
		int blankLineH = 6; // half-height blank line between tier groups

		// Compute tooltip width
		int width = 0;
		for (RichTooltipLine line : headerLines) {
			int lineWidth = (line.icon != null && !line.icon.isEmpty() ? iconSpace : 0)
					+ font.width(line.text);
			if (lineWidth > width) width = lineWidth;
		}
		int pairRowWidth = 2 * pairColWidth;
		if (pairRowWidth > width) width = pairRowWidth;

		// Compute tooltip height: header + base pairs + blank lines + rune tier groups
		int totalLines = headerLines.size() + basePairs.size();
		for (RuneTierGroup group : runeGroups) {
			totalLines += group.pairs.size();
		}
		// Add blank lines: one before each rune tier group (if there are rune groups)
		int blankLines = runeGroups.isEmpty() ? 0 : runeGroups.size();
		// If there are base pairs AND rune groups, the first blank line separates base from runes
		// If no base pairs, the first blank line separates header from first rune group
		int height = totalLines * lineH + blankLines * blankLineH + 8;

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

		// Draw base material pairs
		for (IngredientPair pair : basePairs) {
			curY = drawIngredientPair(g, pair, x, curY, iconSpace, pairColWidth, lineH);
		}

		// Draw rune tier groups with blank line separators
		for (RuneTierGroup group : runeGroups) {
			curY += blankLineH; // blank line before each tier group
			for (IngredientPair pair : group.pairs) {
				curY = drawIngredientPair(g, pair, x, curY, iconSpace, pairColWidth, lineH);
			}
		}
		g.pose().popPose();
	}

	// Draws a single ingredient pair row and returns the updated Y position.
	private int drawIngredientPair(GuiGraphics g, IngredientPair pair, int x, int curY,
			int iconSpace, int pairColWidth, int lineH) {
		// Left entry
		if (pair.leftIcon != null && !pair.leftIcon.isEmpty()) {
			g.pose().pushPose();
			g.pose().scale(0.75F, 0.75F, 1.0F);
			g.renderFakeItem( pair.leftIcon, (int) (x / 0.75F), (int) (curY / 0.75F));
			g.pose().popPose();
		}
		if (pair.leftText != null) {
			g.drawString(font, pair.leftText, x + iconSpace, curY + 2, pair.leftColor, false);
		}
		// Right entry
		if (pair.rightIcon != null && !pair.rightIcon.isEmpty()) {
			int rightX = x + pairColWidth;
			g.pose().pushPose();
			g.pose().scale(0.75F, 0.75F, 1.0F);
			g.renderFakeItem( pair.rightIcon, (int) (rightX / 0.75F), (int) (curY / 0.75F));
			g.pose().popPose();
			if (pair.rightText != null) {
				g.drawString(font, pair.rightText, rightX + iconSpace, curY + 2, pair.rightColor, false);
			}
		}
		return curY + lineH;
	}
	//endregion
}
