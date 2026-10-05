/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # PedestalOfLivingRockScreen.java
 * - Minimal client GUI for the Pedestal of Living Rock.
 * - 176x166. A single floating input slot centered above the player inventory.
 * - Dark grey panel behind the inventory + hotbar 
 */
package corvaeoboro.botania_runic_ritualization.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PedestalOfLivingRockScreen extends AbstractContainerScreen<PedestalOfLivingRockMenu> {

	//region LAYOUT
	private static final int GUI_WIDTH = 176;
	private static final int GUI_HEIGHT = 166;
	// Floating input slot, horizontally centered
	private static final int INPUT_SLOT_X = 80;
	private static final int INPUT_SLOT_Y = 35;
	// Player inventory row positions (must match PedestalOfLivingRockMenu)
	private static final int INV_FIRST_ROW_Y = 84;
	private static final int HOTBAR_Y = 142;
	// Dark grey panel bounds covering the 3 inventory rows + hotbar (1px outer margin around slot borders)
	private static final int INV_PANEL_X = 6;
	private static final int INV_PANEL_Y = 82;
	private static final int INV_PANEL_W = 164;
	private static final int INV_PANEL_H = 78;
	//endregion

	//region COLOR
	// Dark grey palette (shared with the other machine GUIs)
	private static final int COL_PANEL_OUTER = 0xFF1E1E1E;
	private static final int COL_PANEL_INNER = 0xFF2A2A2A;
	// Slot palette: dark grey basalt border + darker grey inner (matches the machine GUIs)
	private static final int COL_PLAYER_SLOT_BORDER = 0xFF2A2A2E;
	private static final int COL_PLAYER_SLOT_INNER = 0xFF3A3A3E;
	//endregion

	//region CONSTRUCT
	public PedestalOfLivingRockScreen(PedestalOfLivingRockMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = GUI_WIDTH;
		this.imageHeight = GUI_HEIGHT;
		// No title or inventory label - the floating slot is self-evident
		this.titleLabelX = -100;
		this.titleLabelY = -100;
		this.inventoryLabelX = -100;
		this.inventoryLabelY = -100;
	}
	//endregion

	//region RENDER
	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		// No title or inventory label
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = leftPos, y = topPos;

		// Dark grey panel behind the player inventory + hotbar only
		g.fill(x + INV_PANEL_X, y + INV_PANEL_Y,
				x + INV_PANEL_X + INV_PANEL_W, y + INV_PANEL_Y + INV_PANEL_H, COL_PANEL_OUTER);
		g.fill(x + INV_PANEL_X + 1, y + INV_PANEL_Y + 1,
				x + INV_PANEL_X + INV_PANEL_W - 1, y + INV_PANEL_Y + INV_PANEL_H - 1, COL_PANEL_INNER);

		// Floating input slot (centered, no panel around it)
		drawSlotBg(g, x + INPUT_SLOT_X, y + INPUT_SLOT_Y);

		// Player inv slot bgs (3 rows)
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				drawSlotBg(g, x + 8 + col * 18, y + INV_FIRST_ROW_Y + row * 18);
			}
		}
		// Hotbar
		for (int col = 0; col < 9; col++) {
			drawSlotBg(g, x + 8 + col * 18, y + HOTBAR_Y);
		}
	}

	private static void drawSlotBg(GuiGraphics g, int slotX, int slotY) {
		g.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, COL_PLAYER_SLOT_BORDER);
		g.fill(slotX, slotY, slotX + 16, slotY + 16, COL_PLAYER_SLOT_INNER);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(g);
		super.render(g, mouseX, mouseY, partialTick);
		this.renderTooltip(g, mouseX, mouseY);
	}
	//endregion
}
