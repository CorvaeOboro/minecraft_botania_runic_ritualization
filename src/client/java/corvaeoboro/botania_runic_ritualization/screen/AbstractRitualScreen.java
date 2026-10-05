/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # AbstractRitualScreen.java
 * - Base class for all three ritual machine screens (Runic Altar Dais, Petal Apothecary Everflowing, Botanical Brewery Aludel).
 * - Provides the shared 176x222 layout:
 *   * Vertical mana bar on the right side (blue, fills bottom-up)
 *   * Vertical ritual progress bar on the right side (green when crafting, very dark when idle)
 *   * Light blue dotted threshold line on the mana bar at the recipe cost level
 *   * Title (centered, white)
 *   * Output slot(s) (centered, above the ellipse)
 *   * Input slot (centered, center of the ritual ellipse)
 *   * Ritual ellipse: squashed perspective ring of ingredient icons with phase offset
 *   * Status text (centered)
 *   * Scrollable recipe grid (9 columns, 2 rows)
 *   * Player inventory + hotbar
 * - Subclasses provide recipe-type-specific logic via abstract methods.
 * - Item count text in the ellipse is drawn outside the slot, lower-right, in small font.
 * - Hovering over an ingredient in the ellipse shows its name + description.
 */
package corvaeoboro.botania_runic_ritualization.screen;

import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlockEntity;
import corvaeoboro.botania_runic_ritualization.client.EmiRecipeViewer;
import corvaeoboro.botania_runic_ritualization.client.FlowerDescriptions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractRitualScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

	//region LAYOUT
	// GUI dimensions (shared by all three machines)
	protected static final int GUI_WIDTH = 176;
	protected static final int GUI_HEIGHT = 200;
	protected static final int CENTER_X = GUI_WIDTH / 2; // 88

	// Right vertical bars (mana + ritual progress)
	protected static final int BAR_Y = 15;
	protected static final int BAR_H = 44;
	protected static final int MANA_BAR_X = 158;
	protected static final int MANA_BAR_W = 6;
	protected static final int PROGRESS_BAR_X = 166;
	protected static final int PROGRESS_BAR_W = 4;

	// Center column layout (top to bottom)
	protected static final int TITLE_Y = 4;

	// Output slot(s) - centered
	protected static final int OUTPUT_Y = 15;

	// Input slot - centered, center of ritual ellipse
	protected static final int INPUT_SLOT_X = CENTER_X - 8; // 80
	protected static final int INPUT_SLOT_Y = 37;
	// Center of the ritual ellipse (lowered ~10px from input slot center for perspective)
	protected static final int CIRCLE_CX = CENTER_X; // 88
	protected static final int CIRCLE_CY = INPUT_SLOT_Y + 18; // 55
	// Squashed ellipse radii (wider than tall for perspective look)
	// X radius widened to spread ingredient slots horizontally; Y kept short for perspective
	protected static final int CIRCLE_RADIUS_X = 48;
	protected static final int CIRCLE_RADIUS_Y = 11;
	protected static final int CIRCLE_ITEM_SIZE = 16;
	// Minimum gap between circle item slots (used by the nudge pass)
	protected static final int CIRCLE_MIN_GAP = 2;

	// Status text
	protected static final int STATUS_Y = 63;

	// Recipe grid (scrollable, compact, 2 rows, 9 columns)
	protected static final int GRID_COLS = 9;
	protected static final int GRID_VISIBLE_ROWS = 2;
	protected static final int GRID_X = 4;
	protected static final int GRID_Y = 75;
	protected static final int BUTTON_SIZE = 16;
	protected static final int BUTTON_GAP = 1;
	protected static final int BUTTON_STRIDE = BUTTON_SIZE + BUTTON_GAP; // 17
	protected static final int GRID_W = GRID_COLS * BUTTON_STRIDE - BUTTON_GAP; // 152
	protected static final int GRID_H = GRID_VISIBLE_ROWS * BUTTON_STRIDE - BUTTON_GAP; // 33

	// Scrollbar
	protected static final int SCROLL_X = 168;
	protected static final int SCROLL_W = 4;
	protected static final int SCROLL_TRACK_Y = GRID_Y;
	protected static final int SCROLL_TRACK_H = GRID_H;

	// Player inventory positions (label hidden, rows moved up)
	protected static final int INV_LABEL_Y = 108;
	protected static final int INV_FIRST_ROW_Y = 120;
	protected static final int HOTBAR_Y = 178;

	//endregion

	//region COLOR
	// Dark grey palette
	protected static final int COL_PANEL_OUTER = 0xFF1E1E1E;
	protected static final int COL_PANEL_INNER = 0xFF2A2A2A;
	// Bevel highlight (top/left edges) and shadow (bottom/right edges) for vanilla-style depth
	protected static final int COL_PANEL_BEVEL = 0xFF3A3A3A;
	protected static final int COL_PANEL_SHADOW = 0xFF0A0A0A;
	protected static final int COL_SLOT_BORDER = 0xFF000000;
	protected static final int COL_SLOT_INNER = 0xFF8B8B8B;
	protected static final int COL_TEXT = 0xFFFFFFFF;
	protected static final int COL_TEXT_DIM = 0xFFAAAAAA;
	protected static final int COL_TEXT_GREEN = 0xFF55FF55;
	protected static final int COL_TEXT_RED = 0xFFFF5555;
	protected static final int COL_TEXT_YELLOW = 0xFFFFFF55;
	protected static final int COL_TEXT_BLUE = 0xFF7FB7FF;
	protected static final int COL_MANA_BG = 0xFF001833;
	protected static final int COL_MANA_FILL = 0xFF3FB3FF;
	// Ritual progress bar: green when crafting, very dark when idle
	protected static final int COL_PROGRESS_BG = 0xFF0A1A0A;
	protected static final int COL_PROGRESS_FILL = 0xFF22CC44;
	protected static final int COL_PROGRESS_IDLE = 0xFF0A0F0A;
	protected static final int COL_BUTTON_BG = 0xFF3A3A3A;
	protected static final int COL_BUTTON_HOVER = 0xFF555555;
	protected static final int COL_BUTTON_SELECTED = 0xFF8B8B8B;
	protected static final int COL_BUTTON_INNER = 0xFF1F1F1F;
	protected static final int COL_SCROLL_TRACK = 0xFF1A1A1A;
	protected static final int COL_SCROLL_THUMB = 0xFF666666;
	protected static final int COL_CIRCLE_RING = 0x4422AA22;
	protected static final int COL_SUPPLIED_BORDER = 0xFF3FB3FF;
	protected static final int COL_MISSING_BORDER = 0xFFFF5555;
	// Mana threshold line: light blue, drawn as 1px dotted
	protected static final int COL_MANA_THRESHOLD = 0xFF7FB7FF;

	// Green input slot palette (used when the input slot has an item placed in it)
	protected static final int COL_GREEN_SLOT_INNER = 0xFF1A3A1A;
	protected static final int COL_GREEN_SLOT_BORDER = 0xFF003300;
	protected static final int COL_GREEN_GHOST_OVERLAY = 0x4022AA22;

	// Red input slot palette (used when the input slot is empty - signals "needs input")
	protected static final int COL_RED_SLOT_INNER = 0xFF3A1A1A;
	protected static final int COL_RED_SLOT_BORDER = 0xFF330000;
	protected static final int COL_RED_GHOST_OVERLAY = 0x40AA2222;

	// Blue output slot palette (always tinted blue to distinguish from input/player slots)
	protected static final int COL_BLUE_SLOT_INNER = 0xFF1A2A3A;
	protected static final int COL_BLUE_SLOT_BORDER = 0xFF002255;

	// Player inventory/hotbar slot palette: dark grey basalt border + darker grey inner
	protected static final int COL_PLAYER_SLOT_BORDER = 0xFF2A2A2E;
	protected static final int COL_PLAYER_SLOT_INNER = 0xFF3A3A3E;

	// Output ghost overlay: heavy dark grey to desaturate and darken the preview icon
	// so it reads as a target/preview rather than a real item sitting in the slot.
	protected static final int COL_OUTPUT_GHOST_OVERLAY = 0xB0303030;

	//endregion

	//region STATE
	// Hover/scroll state for the recipe grid and ritual circle.
	protected int scrollOffset = 0;
	protected int hoveredRecipeIndex = -1;
	protected ItemStack hoveredCircleItem = ItemStack.EMPTY;

	/**
	 * A single row in the grouped recipe grid. Either a category header (recipe == null)
	 * or a recipe row (categoryKey == null). Recipe rows store the recipe and its index
	 * in the flat getRecipes() list so selection/tooltips can look it up.
	 */
	protected static class GridRow {
		final String categoryKey;
		final String categoryLabel;
		final int categoryColor;
		@Nullable final Recipe<?> recipe;
		final int recipeIndex;
		private GridRow(String categoryKey, String categoryLabel, int categoryColor,
				@Nullable Recipe<?> recipe, int recipeIndex) {
			this.categoryKey = categoryKey;
			this.categoryLabel = categoryLabel;
			this.categoryColor = categoryColor;
			this.recipe = recipe;
			this.recipeIndex = recipeIndex;
		}
		static GridRow header(String categoryKey, String categoryLabel, int categoryColor) {
			return new GridRow(categoryKey, categoryLabel, categoryColor, null, -1);
		}
		static GridRow recipe(Recipe<?> recipe, int recipeIndex) {
			return new GridRow(null, null, 0, recipe, recipeIndex);
		}
		boolean isHeader() { return recipe == null; }
	}

	// Cached grouped rows: category headers interspersed with recipe rows. Rebuilt on loadRecipes.
	protected List<GridRow> groupedRows = new ArrayList<>();

	//endregion

	//region CONSTRUCT
	protected AbstractRitualScreen(M menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = GUI_WIDTH;
		this.imageHeight = GUI_HEIGHT;
		this.titleLabelX = -100;
		this.titleLabelY = -100;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = INV_LABEL_Y;
	}

	//endregion

	//region ABSTRACT
	// Subclass hooks for block entity state, recipe access, and input/output display.
	protected abstract ResourceLocation getSelectedRecipeId();
	protected abstract int getCurrentMana();
	protected abstract int getManaCapacity();
	protected abstract int getManaToGet();
	protected abstract int getCooldown();
	protected abstract List<PedestalOfLivingRockBlockEntity> getAdjacentPedestals();
	protected abstract boolean isInputEmpty();
	// True when the machine is actively crafting (cooldown ticking). Used for progress bar.
	protected abstract boolean isCraftingActive();
	// Returns the ritual crafting progress as a 0-1 fraction (0 = just started, 1 = done).
	protected abstract float getCraftingProgress();

	// Abstract methods: recipe access
	protected abstract List<? extends Recipe<?>> getRecipes();
	protected abstract ItemStack getRecipeDisplayItem(Recipe<?> recipe);
	protected abstract void sendSelectPacket(String recipeId, boolean clearing);

	// Abstract methods: recipe categorization for grouped grid display
	// Returns a category key string for the given recipe, or null for "uncategorized".
	@Nullable
	protected abstract String getRecipeCategory(Recipe<?> recipe);
	// Returns the display label for a category key, or null if the category should not show a header.
	@Nullable
	protected abstract String getCategoryLabel(String categoryKey);
	// Returns the ARGB color for a category header label.
	protected abstract int getCategoryColor(String categoryKey);

	// Abstract methods: input/output
	protected abstract ItemStack getInputGhostItem();
	protected abstract Component getInputLabel();

	// Abstract methods: title gradient palette
	// Returns the array of ARGB colors used for the per-letter gradient title.
	protected abstract int[] getGradientPalette();
	//endregion

	//region OVERRIDE
	// Overridable methods with default implementations.
	protected int getOutputSlotCount() { return 1; }

	protected boolean isRecipeSelected(Recipe<?> recipe) {
		ResourceLocation sel = getSelectedRecipeId();
		return sel != null && sel.equals(recipe.getId());
	}

	//endregion

	//region INIT
	@Override
	protected void init() {
		super.init();
		loadRecipes();
		buildGroupedRows();
		clampScroll();
	}

	protected abstract void loadRecipes();

	/**
	 * Builds the grouped row list: iterates recipes in their current order (already sorted
	 * by the subclass), inserting a category header row whenever the category changes.
	 * Recipes with a null category are placed in an implicit "uncategorized" group with
	 * no header. The grouped list is used by drawRecipeGrid, maxScroll, mouseClicked,
	 * and the scrollbar to keep scroll/click indices consistent.
	 */
	protected void buildGroupedRows() {
		groupedRows.clear();
		String previousCategoryKey = null;
		List<? extends Recipe<?>> recipes = getRecipes();
		for (int recipeIndex = 0; recipeIndex < recipes.size(); recipeIndex++) {
			Recipe<?> recipe = recipes.get(recipeIndex);
			String categoryKey = getRecipeCategory(recipe);
			if (categoryKey != null && !categoryKey.equals(previousCategoryKey)) {
				// Insert a blank spacing row between category groups (no text label)
				groupedRows.add(GridRow.header(categoryKey, null, 0));
			}
			groupedRows.add(GridRow.recipe(recipe, recipeIndex));
			previousCategoryKey = categoryKey;
		}
	}

	//endregion

	//region RENDER
	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		// Inventory label is hidden - the inventory slots are self-evident
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = leftPos, y = topPos;

		// Panel background: rounded corners with bevel highlight + shadow
		drawRoundedPanel(g, x, y, imageWidth, imageHeight);

		// Title (centered, per-letter gradient)
		drawGradientTitle(g, x, y);

		// Left vertical bars (mana + progress)
		drawVerticalBars(g, x, y);

		// Output slot(s)
		drawOutputSlots(g, x, y);

		// Output ghost: show the selected recipe's result darkened/grey in the output slot
		drawOutputGhost(g, x, y);

		// Input slot (green-tinted, centered) with ghost item
		drawGreenInputSlot(g, x + INPUT_SLOT_X, y + INPUT_SLOT_Y, getInputGhostItem());

		// Ritual ellipse: ingredient icons arranged in a ring around the input
		drawRitualCircle(g, x, y, mouseX, mouseY);

		// Status text
		drawStatus(g, x, y);

		// Recipe grid (scrollable, clipped)
		drawRecipeGrid(g, x, y, mouseX, mouseY);

		// Scrollbar
		drawScrollbar(g, x + SCROLL_X, y + SCROLL_TRACK_Y, SCROLL_TRACK_H);

		// Player inventory slot backgrounds
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				drawSlotBg(g, x + 8 + col * 18, y + INV_FIRST_ROW_Y + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			drawSlotBg(g, x + 8 + col * 18, y + HOTBAR_Y);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(g);
		super.render(g, mouseX, mouseY, partialTick);
		// Standard slot tooltip first (vanilla renderComponentTooltip uses drawManaged + Z=400 internally)
		this.renderTooltip(g, mouseX, mouseY);
		// Flush all pending GUI content before custom tooltips.
		g.flush();
		// Custom tooltips drawn last, on top of everything (highest z-priority).
		// Subclass renderRichTooltip methods follow the vanilla tooltip pattern:
		//   1. drawManaged() for background fills (batched+flushed at Z=0)
		//   2. pose.translate(0,0,400) to elevate content above GUI items (which are at Z=150)
		//   3. renderFakeItem for icons (at Z=400+150=550) and drawString for text (at Z=400)
		// This ensures the tooltip background and content always render above underlying GUI items.
		if (hoveredRecipeIndex >= 0 && hoveredRecipeIndex < getRecipes().size()) {
			renderRecipeTooltip(g, getRecipes().get(hoveredRecipeIndex), mouseX, mouseY);
		} else if (isMouseOverManaBar(mouseX, mouseY)) {
			int mana = getCurrentMana();
			int cap = getManaCapacity();
			List<Component> lines = new ArrayList<>();
			lines.add(Component.translatable("gui.botania_runic_ritualization.mana", mana, cap));
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
		} else if (isInputEmpty() && isMouseOverInputSlot(mouseX, mouseY)) {
			ItemStack ghostItem = getInputGhostItem();
			List<Component> lines = new ArrayList<>();
			if (!ghostItem.isEmpty()) {
				lines.add(Component.translatable("gui.botania_runic_ritualization.missing_input",
						ghostItem.getHoverName()).withStyle(net.minecraft.ChatFormatting.RED));
			} else {
				lines.add(getInputLabel().copy().withStyle(net.minecraft.ChatFormatting.RED));
			}
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
		} else if (!hoveredCircleItem.isEmpty()) {
			List<Component> lines = new ArrayList<>();
			lines.add(hoveredCircleItem.getHoverName());
			// Word-wrap description lines for compact display
			for (String desc : FlowerDescriptions.getForItem(hoveredCircleItem)) {
				for (String wrapped : wrapText(desc, 140)) {
					lines.add(Component.literal(wrapped).withStyle(net.minecraft.ChatFormatting.GRAY));
				}
			}
			g.renderComponentTooltip(font, lines, mouseX, mouseY);
		}
	}

	// Checks if the mouse is hovering over the mana bar (including a few px padding for easy hovering).
	protected boolean isMouseOverManaBar(int mouseX, int mouseY) {
		int barScreenX = leftPos + MANA_BAR_X;
		int barScreenY = topPos + BAR_Y;
		return mouseX >= barScreenX - 2 && mouseX < barScreenX + MANA_BAR_W + 2
				&& mouseY >= barScreenY - 2 && mouseY < barScreenY + BAR_H + 2;
	}

	// Checks if the mouse is hovering over the input slot (the red/green box in the ritual ellipse).
	protected boolean isMouseOverInputSlot(int mouseX, int mouseY) {
		int slotScreenX = leftPos + INPUT_SLOT_X;
		int slotScreenY = topPos + INPUT_SLOT_Y;
		return mouseX >= slotScreenX - 1 && mouseX < slotScreenX + 17
				&& mouseY >= slotScreenY - 1 && mouseY < slotScreenY + 17;
	}

	/**
	 * Word-wraps a string into multiple lines that fit within the given max pixel width.
	 * Uses greedy word packing: words are added to the current line until adding the next
	 * word would exceed maxWidth, then a new line starts. Long words that exceed maxWidth
	 * on their own are placed on their own line.
	 */
	protected List<String> wrapText(String text, int maxWidth) {
		List<String> result = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			result.add("");
			return result;
		}
		String[] words = text.split(" ");
		StringBuilder current = new StringBuilder();
		for (String word : words) {
			String candidate = current.length() == 0 ? word : current + " " + word;
			if (font.width(candidate) <= maxWidth || current.length() == 0) {
				current = new StringBuilder(candidate);
			} else {
				result.add(current.toString());
				current = new StringBuilder(word);
			}
		}
		if (current.length() > 0) {
			result.add(current.toString());
		}
		return result;
	}

	// Default recipe tooltip: shows output name + ingredients. Subclasses can override for richer tooltips.
	protected void renderRecipeTooltip(GuiGraphics g, Recipe<?> recipe, int mouseX, int mouseY) {
		List<Component> lines = new ArrayList<>();
		lines.add(getRecipeDisplayItem(recipe).getHoverName());
		lines.add(Component.translatable("gui.botania_runic_ritualization.ingredients")
				.withStyle(s -> s.withColor(0xAAAAAA)));
		for (Ingredient ing : recipe.getIngredients()) {
			if (ing.isEmpty()) continue;
			ItemStack[] m = ing.getItems();
			if (m.length == 0) continue;
			lines.add(Component.literal(" - ").append(m[0].getHoverName()));
		}
		g.renderComponentTooltip(font, lines, mouseX, mouseY);
	}

	//endregion

	//region HELPER
	// Drawing helpers for bars, slots, ghost items, ritual circle, and recipe grid.

	/**
 * Draws the GUI panel with rounded corners and a vanilla-style bevel:
 * a 1px highlight on the top/left edges and a 1px shadow on the bottom/right edges,
 * framing the dark grey inner area. Corners are chamfered by skipping the corner
 * pixels so the panel reads as a rounded rectangle rather than a flat box.
 */
protected void drawRoundedPanel(GuiGraphics g, int x, int y, int w, int h) {
	int x2 = x + w, y2 = y + h;
	// Outer fill (slightly darker than inner) for the border ring
	g.fill(x, y, x2, y2, COL_PANEL_OUTER);
	// Inner fill, inset by 1px on all sides except the corners which stay outer
	g.fill(x + 1, y + 1, x2 - 1, y2 - 1, COL_PANEL_INNER);
	// Clear the 4 corner pixels so the rectangle reads as rounded/chamfered
	g.fill(x, y, x + 1, y + 1, 0x00000000);
	g.fill(x2 - 1, y, x2, y + 1, 0x00000000);
	g.fill(x, y2 - 1, x + 1, y2, 0x00000000);
	g.fill(x2 - 1, y2 - 1, x2, y2, 0x00000000);
	// Bevel highlight: top edge (excluding corners) and left edge (excluding corners)
	g.fill(x + 1, y, x2 - 1, y + 1, COL_PANEL_BEVEL);
	g.fill(x, y + 1, x + 1, y2 - 1, COL_PANEL_BEVEL);
	// Bevel shadow: bottom edge (excluding corners) and right edge (excluding corners)
	g.fill(x + 1, y2 - 1, x2 - 1, y2, COL_PANEL_SHADOW);
	g.fill(x2 - 1, y + 1, x2, y2 - 1, COL_PANEL_SHADOW);
}

protected void drawVerticalBars(GuiGraphics g, int gx, int gy) {
		int mana = getCurrentMana();
		int manaCap = getManaCapacity();
		float manaFrac = manaCap > 0 ? Math.min(1F, (float) mana / manaCap) : 0F;
		drawVerticalBar(g, gx + MANA_BAR_X, gy + BAR_Y, MANA_BAR_W, BAR_H,
				COL_MANA_BG, COL_MANA_FILL, manaFrac);

		// Light blue dotted 1px threshold line on the mana bar at the recipe cost level
		int recipeCost = getManaToGet();
		if (recipeCost > 0 && manaCap > 0) {
			float thresholdFrac = Math.min(1F, (float) recipeCost / manaCap);
			int thresholdY = gy + BAR_Y + BAR_H - (int) (BAR_H * thresholdFrac);
			int barX = gx + MANA_BAR_X;
			// Dotted line: draw every other pixel across the bar width
			for (int dx = 0; dx < MANA_BAR_W; dx += 2) {
				g.fill(barX + dx, thresholdY, barX + dx + 1, thresholdY + 1, COL_MANA_THRESHOLD);
			}
		}

		// Ritual progress bar: green when crafting, very dark when idle
		boolean crafting = isCraftingActive();
		if (crafting) {
			float progressFrac = getCraftingProgress();
			drawVerticalBar(g, gx + PROGRESS_BAR_X, gy + BAR_Y, PROGRESS_BAR_W, BAR_H,
					COL_PROGRESS_BG, COL_PROGRESS_FILL, progressFrac);
		} else {
			// Very dark vertical bar when not crafting
			drawVerticalBar(g, gx + PROGRESS_BAR_X, gy + BAR_Y, PROGRESS_BAR_W, BAR_H,
					COL_PROGRESS_IDLE, COL_PROGRESS_IDLE, 0F);
		}
	}

	protected void drawVerticalBar(GuiGraphics g, int x, int y, int w, int h,
			int bgColor, int fillColor, float frac) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, COL_SLOT_BORDER);
		g.fill(x, y, x + w, y + h, bgColor);
		int fillH = (int) (h * frac);
		g.fill(x, y + h - fillH, x + w, y + h, fillColor);
	}

	protected void drawOutputSlots(GuiGraphics g, int gx, int gy) {
		int count = getOutputSlotCount();
		if (count == 1) {
			drawOutputSlotBg(g, gx + CENTER_X - 8, gy + OUTPUT_Y);
		} else {
			int startX = (GUI_WIDTH - count * 18) / 2;
			for (int outputSlotIndex = 0; outputSlotIndex < count; outputSlotIndex++) {
				drawOutputSlotBg(g, gx + startX + outputSlotIndex * 18, gy + OUTPUT_Y);
			}
		}
	}

	// Draws a blue-tinted output slot background (always blue to distinguish output slots).
	protected static void drawOutputSlotBg(GuiGraphics g, int x, int y) {
		g.fill(x - 1, y - 1, x + 17, y + 17, COL_BLUE_SLOT_BORDER);
		g.fill(x, y, x + 16, y + 16, COL_BLUE_SLOT_INNER);
	}

	/**
	 * Draws the selected recipe's output item as a desaturated, darkened ghost in the output slot(s),
	 * to visualize the expected/target output. The heavy grey overlay mutes the item's colors so it
	 * reads as a preview rather than a real item sitting in the slot ready to be taken.
	 */
	protected void drawOutputGhost(GuiGraphics g, int gx, int gy) {
		ResourceLocation sel = getSelectedRecipeId();
		if (sel == null) return;
		Recipe<?> selected = null;
		for (Recipe<?> recipe : getRecipes()) {
			if (recipe.getId().equals(sel)) { selected = recipe; break; }
		}
		if (selected == null) return;
		ItemStack ghostItem = getRecipeDisplayItem(selected);
		if (ghostItem.isEmpty()) return;

		int count = getOutputSlotCount();
		int slotX, slotY = gy + OUTPUT_Y;
		if (count == 1) {
			slotX = gx + CENTER_X - 8;
			g.renderFakeItem( ghostItem, slotX, slotY);
			// Heavy dark grey overlay above the item (Z > 150) so it desaturates/darkens the icon
			g.pose().pushPose();
			g.pose().translate(0, 0, 200);
			g.fill(slotX, slotY, slotX + 16, slotY + 16, COL_OUTPUT_GHOST_OVERLAY);
			g.pose().popPose();
		} else {
			int startX = (GUI_WIDTH - count * 18) / 2;
			// Show ghost in the first output slot only
			slotX = gx + startX;
			g.renderFakeItem( ghostItem, slotX, slotY);
			g.pose().pushPose();
			g.pose().translate(0, 0, 200);
			g.fill(slotX, slotY, slotX + 16, slotY + 16, COL_OUTPUT_GHOST_OVERLAY);
			g.pose().popPose();
		}
	}

	/**
	 * Draws the input slot with a color that signals its state:
	 * - Red tint when empty (signals "needs input")
	 * - Green tint when an item is placed in it (signals "ready")
	 * When empty and a ghost item is available, the ghost is rendered with a matching tint overlay.
	 */
	protected void drawGreenInputSlot(GuiGraphics g, int x, int y, ItemStack ghostItem) {
		boolean slotEmpty = isInputEmpty();
		int borderColor = slotEmpty ? COL_RED_SLOT_BORDER : COL_GREEN_SLOT_BORDER;
		int innerColor = slotEmpty ? COL_RED_SLOT_INNER : COL_GREEN_SLOT_INNER;

		g.fill(x - 1, y - 1, x + 17, y + 17, borderColor);
		g.fill(x, y, x + 16, y + 16, innerColor);
		if (slotEmpty && !ghostItem.isEmpty()) {
			// Render the ghost item darkened/greyed out to show what's needed
			g.renderFakeItem( ghostItem, x, y);
			g.pose().pushPose();
			g.pose().translate(0, 0, 200);
			g.fill(x, y, x + 16, y + 16, COL_OUTPUT_GHOST_OVERLAY);
			g.pose().popPose();
		}
	}

	/**
	 * Draws the ritual ellipse: recipe ingredients in a squashed perspective ring around the
	 * input slot. Phase offset rotates items so none sits at top/bottom. Each ingredient icon
	 * is 14x14. Supplied ingredients get a blue border. Count text is drawn outside the slot,
	 * to the lower-right, in small font. A nudge pass resolves overlapping slots by pushing
	 * them apart along the line connecting their centers.
	 */
	protected void drawRitualCircle(GuiGraphics g, int gx, int gy, int mouseX, int mouseY) {
		hoveredCircleItem = ItemStack.EMPTY;
		ResourceLocation selectedRecipeId = getSelectedRecipeId();
		if (selectedRecipeId == null) return;

		Recipe<?> selected = null;
		for (Recipe<?> recipe : getRecipes()) {
			if (recipe.getId().equals(selectedRecipeId)) { selected = recipe; break; }
		}
		if (selected == null) return;

		NonNullList<Ingredient> ingredients = selected.getIngredients();
		List<PedestalOfLivingRockBlockEntity> pedestals = getAdjacentPedestals();

		List<Ingredient> activeIngredients = new ArrayList<>();
		for (Ingredient ingredient : ingredients) {
			if (!ingredient.isEmpty()) activeIngredients.add(ingredient);
		}
		if (activeIngredients.isEmpty()) return;

		int ingredientCount = activeIngredients.size();
		int circleCenterX = gx + CIRCLE_CX;
		int circleCenterY = gy + CIRCLE_CY;
		int halfItemSize = CIRCLE_ITEM_SIZE / 2; // 7

		drawEllipseRing(g, circleCenterX, circleCenterY, CIRCLE_RADIUS_X, CIRCLE_RADIUS_Y);

		// Position ingredients evenly around the circle, treating the top-center (angle -PI/2)
		// as already occupied by the reagent/input slot. With N ingredients, we use N+1 positions:
		// position 0 = reagent at top-center, positions 1..N = ingredient slots.
		int totalPositions = ingredientCount + 1;

		// Step 1: Compute initial slot center positions on the ellipse
		float[] slotPosX = new float[ingredientCount];
		float[] slotPosY = new float[ingredientCount];
		for (int slotIndex = 0; slotIndex < ingredientCount; slotIndex++) {
			double angle = -Math.PI / 2 + (2 * Math.PI * (slotIndex + 1) / totalPositions);
			slotPosX[slotIndex] = circleCenterX + CIRCLE_RADIUS_X * (float) Math.cos(angle);
			slotPosY[slotIndex] = circleCenterY + CIRCLE_RADIUS_Y * (float) Math.sin(angle);
		}

		// Step 2: Nudge pass - push apart overlapping slots (a few iterations)
		float minDistance = CIRCLE_ITEM_SIZE + CIRCLE_MIN_GAP;
		for (int nudgeIteration = 0; nudgeIteration < 6; nudgeIteration++) {
			boolean anyOverlap = false;
			for (int slotA = 0; slotA < ingredientCount; slotA++) {
				for (int slotB = slotA + 1; slotB < ingredientCount; slotB++) {
					float dx = slotPosX[slotB] - slotPosX[slotA];
					float dy = slotPosY[slotB] - slotPosY[slotA];
					float distSq = dx * dx + dy * dy;
					if (distSq < minDistance * minDistance && distSq > 0.01F) {
						float dist = (float) Math.sqrt(distSq);
						float overlap = (minDistance - dist) * 0.5F;
						float nx = dx / dist;
						float ny = dy / dist;
						slotPosX[slotA] -= nx * overlap;
						slotPosY[slotA] -= ny * overlap;
						slotPosX[slotB] += nx * overlap;
						slotPosY[slotB] += ny * overlap;
						anyOverlap = true;
					}
				}
			}
			if (!anyOverlap) break;
		}

		// Step 3: Greedy pedestal supply matching
		Map<PedestalOfLivingRockBlockEntity, Integer> remainingStacksPerPedestal = new HashMap<>();
		for (PedestalOfLivingRockBlockEntity pedestal : pedestals) {
			remainingStacksPerPedestal.put(pedestal, pedestal.getStack().getCount());
		}

		// Step 4: Render each slot at its nudged position
		for (int slotIndex = 0; slotIndex < ingredientCount; slotIndex++) {
			int slotX = Math.round(slotPosX[slotIndex]) - halfItemSize;
			int slotY = Math.round(slotPosY[slotIndex]) - halfItemSize;

			Ingredient ingredient = activeIngredients.get(slotIndex);
			ItemStack[] ingredientItems = ingredient.getItems();
			if (ingredientItems.length == 0) continue;
			ItemStack displayStack = ingredientItems[0];

			int supplyCount = 0;
			for (PedestalOfLivingRockBlockEntity pedestal : pedestals) {
				int remainingCount = remainingStacksPerPedestal.getOrDefault(pedestal, 0);
				if (remainingCount > 0 && ingredient.test(pedestal.getStack())) {
					remainingStacksPerPedestal.put(pedestal, remainingCount - 1);
					supplyCount = pedestal.getStack().getCount();
					break;
				}
			}

			// Slot background
			g.fill(slotX - 1, slotY - 1, slotX + CIRCLE_ITEM_SIZE + 1, slotY + CIRCLE_ITEM_SIZE + 1, COL_SLOT_BORDER);
			g.fill(slotX, slotY, slotX + CIRCLE_ITEM_SIZE, slotY + CIRCLE_ITEM_SIZE,
					supplyCount > 0 ? COL_GREEN_SLOT_INNER : COL_PANEL_INNER);

			// Item icon
			g.renderFakeItem( displayStack, slotX, slotY);
			if (supplyCount == 0) {
				g.pose().pushPose();
				g.pose().translate(0, 0, 200);
				g.fill(slotX, slotY, slotX + CIRCLE_ITEM_SIZE, slotY + CIRCLE_ITEM_SIZE, 0x80000000);
				g.pose().popPose();
			}

			// Blue border if supplied, red border if missing
			if (supplyCount > 0) {
				g.fill(slotX - 1, slotY - 1, slotX + CIRCLE_ITEM_SIZE + 1, slotY, COL_SUPPLIED_BORDER);
				g.fill(slotX - 1, slotY + CIRCLE_ITEM_SIZE, slotX + CIRCLE_ITEM_SIZE + 1, slotY + CIRCLE_ITEM_SIZE + 1, COL_SUPPLIED_BORDER);
				g.fill(slotX - 1, slotY - 1, slotX, slotY + CIRCLE_ITEM_SIZE + 1, COL_SUPPLIED_BORDER);
				g.fill(slotX + CIRCLE_ITEM_SIZE, slotY - 1, slotX + CIRCLE_ITEM_SIZE + 1, slotY + CIRCLE_ITEM_SIZE + 1, COL_SUPPLIED_BORDER);
			} else {
				g.fill(slotX - 1, slotY - 1, slotX + CIRCLE_ITEM_SIZE + 1, slotY, COL_MISSING_BORDER);
				g.fill(slotX - 1, slotY + CIRCLE_ITEM_SIZE, slotX + CIRCLE_ITEM_SIZE + 1, slotY + CIRCLE_ITEM_SIZE + 1, COL_MISSING_BORDER);
				g.fill(slotX - 1, slotY - 1, slotX, slotY + CIRCLE_ITEM_SIZE + 1, COL_MISSING_BORDER);
				g.fill(slotX + CIRCLE_ITEM_SIZE, slotY - 1, slotX + CIRCLE_ITEM_SIZE + 1, slotY + CIRCLE_ITEM_SIZE + 1, COL_MISSING_BORDER);
			}

			// Count text: outside the slot, lower-right, small font
			if (supplyCount > 0) {
				String countStr = String.valueOf(supplyCount);
				g.pose().pushPose();
				g.pose().scale(0.5F, 0.5F, 1.0F);
				int countX = (int) ((slotX + CIRCLE_ITEM_SIZE) / 0.5F);
				int countY = (int) ((slotY + CIRCLE_ITEM_SIZE - 4) / 0.5F);
				g.drawString(font, countStr, countX, countY, 0xFFFFFF, true);
				g.pose().popPose();
			}

			// Hover tracking
			if (mouseX >= slotX && mouseX < slotX + CIRCLE_ITEM_SIZE
					&& mouseY >= slotY && mouseY < slotY + CIRCLE_ITEM_SIZE) {
				hoveredCircleItem = displayStack;
			}
		}
	}

	protected void drawEllipseRing(GuiGraphics g, int cx, int cy, int rx, int ry) {
		int segments = 48;
		int prevX = cx + rx;
		int prevY = cy;
		for (int i = 1; i <= segments; i++) {
			double angle = 2 * Math.PI * i / segments;
			int curX = cx + (int) (rx * Math.cos(angle));
			int curY = cy + (int) (ry * Math.sin(angle));
			g.fill(prevX, prevY, prevX + 1, prevY + 1, COL_CIRCLE_RING);
			g.fill(curX, curY, curX + 1, curY + 1, COL_CIRCLE_RING);
			prevX = curX;
			prevY = curY;
		}
	}

	/**
	 * Status text is no longer drawn on the GUI - the mana bar, progress bar, and circle
	 * borders provide better visual indicators. Status info is only shown when the F3 debug
	 * overlay is active.
	 */
	protected void drawStatus(GuiGraphics g, int gx, int gy) {
		// Only show status text when F3 debug screen is active
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.options.renderDebug) {
			return;
		}
		int cooldown = getCooldown();
		int mana = getCurrentMana();
		int recipeCost = getManaToGet();
		ResourceLocation sel = getSelectedRecipeId();
		String statusStr;
		if (sel == null) {
			statusStr = "Idle - select a recipe";
		} else if (cooldown > 0) {
			statusStr = "Crafting... " + String.format("%.1f", cooldown / 20F) + "s";
		} else if (isInputEmpty()) {
			statusStr = "Needs: " + getInputLabel().getString();
		} else if (recipeCost > 0 && mana < recipeCost) {
			statusStr = "Charging: " + mana + " / " + recipeCost + " mana";
		} else {
			List<ItemStack> missing = computeMissingIngredientStacks();
			if (!missing.isEmpty()) {
				statusStr = "Missing ingredients (" + missing.size() + ")";
			} else {
				statusStr = "Ready to craft";
			}
		}
		int textWidth = font.width(statusStr);
		int drawX = gx + (imageWidth - textWidth) / 2;
		g.drawString(font, statusStr, drawX, gy + STATUS_Y, COL_TEXT_DIM, false);
	}

	/**
	 * Returns the missing ingredient item stacks (deduplicated by item identity+tags),
	 * with the stack count set to the number of missing instances of that item.
	 */
	protected List<ItemStack> computeMissingIngredientStacks() {
		List<ItemStack> rawMissing = new ArrayList<>();
		ResourceLocation sel = getSelectedRecipeId();
		if (sel == null) return rawMissing;

		Recipe<?> selected = null;
		for (Recipe<?> r : getRecipes()) {
			if (r.getId().equals(sel)) { selected = r; break; }
		}
		if (selected == null) return rawMissing;

		NonNullList<Ingredient> ingredients = selected.getIngredients();
		List<PedestalOfLivingRockBlockEntity> pedestals = getAdjacentPedestals();

		Map<PedestalOfLivingRockBlockEntity, Integer> remaining = new HashMap<>();
		for (PedestalOfLivingRockBlockEntity p : pedestals) {
			remaining.put(p, p.getStack().getCount());
		}

		for (Ingredient ing : ingredients) {
			if (ing.isEmpty()) continue;
			boolean satisfied = false;
			for (PedestalOfLivingRockBlockEntity p : pedestals) {
				int rem = remaining.getOrDefault(p, 0);
				if (rem <= 0) continue;
				if (ing.test(p.getStack())) {
					remaining.put(p, rem - 1);
					satisfied = true;
					break;
				}
			}
			if (!satisfied) {
				ItemStack[] items = ing.getItems();
				if (items.length > 0) {
					rawMissing.add(items[0].copy());
				}
			}
		}

		// Deduplicate: group by item identity + tags, accumulate counts
		List<ItemStack> grouped = new ArrayList<>();
		for (ItemStack stack : rawMissing) {
			boolean found = false;
			for (ItemStack existing : grouped) {
				if (ItemStack.isSameItemSameTags(existing, stack)) {
					existing.grow(1);
					found = true;
					break;
				}
			}
			if (!found) {
				ItemStack copy = stack.copy();
				copy.setCount(1);
				grouped.add(copy);
			}
		}
		return grouped;
	}

	protected void drawRecipeGrid(GuiGraphics g, int gx, int gy, int mouseX, int mouseY) {
		int clipLeft = gx + GRID_X;
		int clipTop = gy + GRID_Y;
		int clipRight = gx + GRID_X + GRID_W;
		int clipBottom = gy + GRID_Y + GRID_H;
		g.enableScissor(clipLeft, clipTop, clipRight, clipBottom);

		hoveredRecipeIndex = -1;
		int totalRows = (groupedRows.size() + GRID_COLS - 1) / GRID_COLS;
		for (int row = 0; row < totalRows; row++) {
			for (int col = 0; col < GRID_COLS; col++) {
				int gridIndex = row * GRID_COLS + col;
				if (gridIndex >= groupedRows.size()) break;
				GridRow gridRow = groupedRows.get(gridIndex);
				int bx = gx + GRID_X + col * BUTTON_STRIDE;
				int by = gy + GRID_Y + (row - scrollOffset) * BUTTON_STRIDE;
				if (by + BUTTON_SIZE < clipTop) continue;
				if (by > clipBottom) break;

				if (gridRow.isHeader()) {
					// Category spacing row: blank, no text or bar - just visual separation
					continue;
				}

				// Recipe button
				Recipe<?> recipe = gridRow.recipe;
				boolean hover = mouseX >= bx && mouseX < bx + BUTTON_SIZE
						&& mouseY >= by && mouseY < by + BUTTON_SIZE;
				boolean selected = isRecipeSelected(recipe);
				int bg = selected ? COL_BUTTON_SELECTED : (hover ? COL_BUTTON_HOVER : COL_BUTTON_BG);
				g.fill(bx, by, bx + BUTTON_SIZE, by + BUTTON_SIZE, bg);
				g.fill(bx + 1, by + 1, bx + BUTTON_SIZE - 1, by + BUTTON_SIZE - 1, COL_BUTTON_INNER);
				ItemStack out = getRecipeDisplayItem(recipe);
				if (!out.isEmpty()) {
					g.renderFakeItem( out, bx + 1, by + 1);
				}
				if (hover && mouseY >= clipTop && mouseY < clipBottom) {
					hoveredRecipeIndex = gridRow.recipeIndex;
				}
			}
		}
		g.disableScissor();
	}

	protected void drawScrollbar(GuiGraphics g, int sx, int sy, int sh) {
		g.fill(sx, sy, sx + SCROLL_W, sy + sh, COL_SCROLL_TRACK);
		int max = maxScroll();
		if (max <= 0) return;
		int totalRows = (groupedRows.size() + GRID_COLS - 1) / GRID_COLS;
		float thumbFrac = (float) GRID_VISIBLE_ROWS / Math.max(1, totalRows);
		int thumbH = Math.max(8, (int) (sh * thumbFrac));
		int thumbY = sy + (int) ((sh - thumbH) * ((float) scrollOffset / max));
		g.fill(sx, thumbY, sx + SCROLL_W, thumbY + thumbH, COL_SCROLL_THUMB);
	}

	protected static void drawSlotBg(GuiGraphics g, int x, int y) {
		g.fill(x - 1, y - 1, x + 17, y + 17, COL_PLAYER_SLOT_BORDER);
		g.fill(x, y, x + 16, y + 16, COL_PLAYER_SLOT_INNER);
	}

	/**
	 * Draws the GUI title centered at TITLE_Y, applying a per-letter color gradient.
	 * Each letter cycles through the palette returned by getGradientPalette().
	 * Spaces do not advance the color index.
	 */
	protected void drawGradientTitle(GuiGraphics g, int gx, int gy) {
		String text = this.title.getString();
		int[] palette = getGradientPalette();
		if (palette == null || palette.length == 0) {
			int titleWidth = font.width(this.title);
			g.drawString(font, this.title, gx + (imageWidth - titleWidth) / 2, gy + TITLE_Y, COL_TEXT, false);
			return;
		}

		int totalWidth = font.width(this.title);
		int startX = gx + (imageWidth - totalWidth) / 2;
		int y = gy + TITLE_Y;

		int colorIdx = 0;
		int x = startX;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == ' ') {
				x += font.width(" ");
				continue;
			}
			String ch = String.valueOf(c);
			int charWidth = font.width(ch);
			int color = palette[colorIdx % palette.length];
			g.drawString(font, ch, x, y, color, false);
			x += charWidth;
			colorIdx++;
		}
	}

	//endregion

	//region MISSING
	// Shared greedy pedestal-matching logic for missing ingredient computation.
	protected List<Component> computeMissingIngredients() {
		List<Component> missing = new ArrayList<>();
		ResourceLocation sel = getSelectedRecipeId();
		if (sel == null) return missing;

		Recipe<?> selected = null;
		for (Recipe<?> r : getRecipes()) {
			if (r.getId().equals(sel)) { selected = r; break; }
		}
		if (selected == null) return missing;

		NonNullList<Ingredient> ingredients = selected.getIngredients();
		List<PedestalOfLivingRockBlockEntity> pedestals = getAdjacentPedestals();

		Map<PedestalOfLivingRockBlockEntity, Integer> remaining = new HashMap<>();
		for (PedestalOfLivingRockBlockEntity p : pedestals) {
			remaining.put(p, p.getStack().getCount());
		}

		for (Ingredient ing : ingredients) {
			if (ing.isEmpty()) continue;
			boolean satisfied = false;
			for (PedestalOfLivingRockBlockEntity p : pedestals) {
				int rem = remaining.getOrDefault(p, 0);
				if (rem <= 0) continue;
				if (ing.test(p.getStack())) {
					remaining.put(p, rem - 1);
					satisfied = true;
					break;
				}
			}
			if (!satisfied) {
				ItemStack[] items = ing.getItems();
				if (items.length > 0) {
					missing.add(items[0].getHoverName());
				}
			}
		}
		return missing;
	}

	//endregion

	//region SCROLL
	protected int maxScroll() {
		int totalRows = (groupedRows.size() + GRID_COLS - 1) / GRID_COLS;
		return Math.max(0, totalRows - GRID_VISIBLE_ROWS);
	}

	protected void clampScroll() {
		int max = maxScroll();
		if (scrollOffset > max) scrollOffset = max;
		if (scrollOffset < 0) scrollOffset = 0;
	}

	//endregion

	//region INPUT
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int x = leftPos, y = topPos;
			int clipTop = y + GRID_Y;
			int clipBottom = y + GRID_Y + GRID_H;
			if (mouseY >= clipTop && mouseY < clipBottom) {
				List<? extends Recipe<?>> recipes = getRecipes();
				int totalRows = (groupedRows.size() + GRID_COLS - 1) / GRID_COLS;
				for (int row = 0; row < totalRows; row++) {
					for (int col = 0; col < GRID_COLS; col++) {
						int gridIndex = row * GRID_COLS + col;
						if (gridIndex >= groupedRows.size()) break;
						GridRow gridRow = groupedRows.get(gridIndex);
						if (gridRow.isHeader()) continue;
						int bx = x + GRID_X + col * BUTTON_STRIDE;
						int by = y + GRID_Y + (row - scrollOffset) * BUTTON_STRIDE;
						if (by + BUTTON_SIZE < clipTop || by > clipBottom) continue;
						if (mouseX >= bx && mouseX < bx + BUTTON_SIZE
								&& mouseY >= by && mouseY < by + BUTTON_SIZE) {
							Recipe<?> r = recipes.get(gridRow.recipeIndex);
							boolean clearing = isRecipeSelected(r);
							sendSelectPacket(clearing ? "" : r.getId().toString(), clearing);
							return true;
						}
					}
				}
			}
			int sx = x + SCROLL_X;
			int sy = y + SCROLL_TRACK_Y;
			if (mouseX >= sx && mouseX < sx + SCROLL_W
					&& mouseY >= sy && mouseY < sy + SCROLL_TRACK_H) {
				float clickFrac = (float) (mouseY - sy) / SCROLL_TRACK_H;
				int max = maxScroll();
				scrollOffset = Math.max(0, Math.min(max, Math.round(clickFrac * max)));
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (delta > 0) {
			scrollOffset = Math.max(0, scrollOffset - 1);
		} else if (delta < 0) {
			scrollOffset = Math.min(maxScroll(), scrollOffset + 1);
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	/**
	 * Intercepts EMI's R (view recipes) and U (view uses) hotkeys when the mouse hovers
	 * over a custom recipe grid button or a ritual circle ingredient icon. EMI's own
	 * KeyboardMixin only detects stacks in standard inventory slots, so it misses our
	 * custom-rendered elements. Here we bridge that gap by calling EmiApi directly.
	 * Falls through to super if EMI is not installed or no target is hovered.
	 */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (EmiRecipeViewer.isEmiLoaded()) {
			// Recipe grid: hoveredRecipeIndex is set during drawRecipeGrid
			if (hoveredRecipeIndex >= 0 && hoveredRecipeIndex < getRecipes().size()) {
				Recipe<?> hoveredRecipe = getRecipes().get(hoveredRecipeIndex);
				ItemStack outputStack = getRecipeDisplayItem(hoveredRecipe);
				if (!outputStack.isEmpty()) {
					if (keyCode == GLFW.GLFW_KEY_R) {
						EmiRecipeViewer.displayRecipes(outputStack);
						return true;
					} else if (keyCode == GLFW.GLFW_KEY_U) {
						EmiRecipeViewer.displayUses(outputStack);
						return true;
					}
				}
			}
			// Ritual circle: hoveredCircleItem is set during drawRitualCircle
			if (!hoveredCircleItem.isEmpty()) {
				if (keyCode == GLFW.GLFW_KEY_R) {
					EmiRecipeViewer.displayRecipes(hoveredCircleItem);
					return true;
				} else if (keyCode == GLFW.GLFW_KEY_U) {
					EmiRecipeViewer.displayUses(hoveredCircleItem);
					return true;
				}
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (button == 0) {
			int x = leftPos, y = topPos;
			int sx = x + SCROLL_X;
			int sy = y + SCROLL_TRACK_Y;
			if (mouseX >= sx - 2 && mouseX < sx + SCROLL_W + 2
					&& mouseY >= sy && mouseY < sy + SCROLL_TRACK_H) {
				float clickFrac = (float) (mouseY - sy) / SCROLL_TRACK_H;
				int max = maxScroll();
				scrollOffset = Math.max(0, Math.min(max, Math.round(clickFrac * max)));
				return true;
			}
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}
	//endregion
}
