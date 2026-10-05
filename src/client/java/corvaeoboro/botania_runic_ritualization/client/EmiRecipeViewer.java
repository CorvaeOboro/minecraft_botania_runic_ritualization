/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes
 * - draws ingredients from adjacent Pedestals of Living Rock
 *
 * # EmiRecipeViewer.java
 * - Soft-dependency bridge to EMI for recipe/uses display from custom GUI elements.
 * - EMI normally intercepts R/U keybinds on HandledScreens via its KeyboardMixin, but only
 *   for stacks it detects in standard inventory slots. Our ritual screens draw recipe grid
 *   buttons and ritual circle ingredient icons as custom rendered elements (not slots),
 *   so EMI never sees them as hovered stacks.
 * - This helper lets AbstractRitualScreen.keyPressed call EmiApi.displayRecipes/displayUses
 *   directly when the mouse hovers a recipe target or circle ingredient.
 * - All EMI classes are compile-only (modCompileOnly API jar); at runtime, FabricLoader
 *   guards every call so the mod works without EMI installed.
 */
package corvaeoboro.botania_runic_ritualization.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;

public final class EmiRecipeViewer {

	private static final boolean EMI_LOADED = FabricLoader.getInstance().isModLoaded("emi");

	private EmiRecipeViewer() {}

	//region CHECK
	// Returns true if EMI is installed at runtime. 
	public static boolean isEmiLoaded() {
		return EMI_LOADED;
	}
	//endregion

	//region DISPLAY
	/**
	 * Opens the EMI recipe display for the given output stack (equivalent to pressing R
	 * on the item in an inventory). No-op if EMI is not installed.
	 */
	public static void displayRecipes(ItemStack outputStack) {
		if (!EMI_LOADED || outputStack.isEmpty()) return;
		EmiApi.displayRecipes(EmiStack.of(outputStack));
	}

	/**
	 * Opens the EMI uses display for the given stack (equivalent to pressing U on the
	 * item in an inventory). No-op if EMI is not installed.
	 */
	public static void displayUses(ItemStack ingredientStack) {
		if (!EMI_LOADED || ingredientStack.isEmpty()) return;
		EmiApi.displayUses(EmiStack.of(ingredientStack));
	}
	//endregion
}
