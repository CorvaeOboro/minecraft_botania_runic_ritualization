/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # WandLinkVisualizer.java
 * - Shared client-side utility for rendering sparkling blue particle lines from connected pedestals to a machine's center.
 * - Visible only when the player holds a Botania Wand of the Forest (main or off hand).
 * - Inspired by the mana spreader's wand-bound-target visualization. Uses SparkleParticleData.fake() (client-side only).
 * - Only pedestals holding items matching a recipe ingredient get a line, showing which pedestals are active in the ritual.
 */
package corvaeoboro.botania_runic_ritualization.client;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualizationConfig;
import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.common.item.WandOfTheForestItem;

import java.util.List;

public final class WandLinkVisualizer {

	//region CONSTANT
	// Saturated blue particle color (RGB 0.2, 0.4, 1.0).
	private static final float PARTICLE_R = 0.2F;
	private static final float PARTICLE_G = 0.4F;
	private static final float PARTICLE_B = 1.0F;
	private static final float PARTICLE_SIZE = 0.6F;
	private static final int PARTICLE_M = 4;

	// Spacing between particles along the line (in blocks).
	private static final double PARTICLE_SPACING = 0.25;
	//endregion

	private WandLinkVisualizer() {}

	//region WAND
	// Returns true if the local player is holding a Wand of the Forest in either hand.
	public static boolean isPlayerHoldingWand() {
		net.minecraft.client.player.LocalPlayer localPlayer = Minecraft.getInstance().player;
		if (localPlayer == null) return false;
		return localPlayer.getMainHandItem().getItem() instanceof WandOfTheForestItem
				|| localPlayer.getOffhandItem().getItem() instanceof WandOfTheForestItem;
	}
	//endregion

	//region LINK
	/**
	 * Draws sparkling blue particle lines from each pedestal that supplies a recipe ingredient
	 * to the machine's center. Should be called from a BlockEntityRenderer's render() method.
	 *
	 * @param level        The client level
	 * @param machinePos   The machine's block position
	 * @param pedestals    All adjacent/linked pedestals
	 * @param ingredients  The current recipe's ingredients (used to filter which pedestals get lines)
	 */
	public static void drawPedestalLinks(Level level, BlockPos machinePos,
			List<PedestalOfLivingRockBlockEntity> pedestals,
			NonNullList<Ingredient> ingredients) {
		if (!BotaniaRunicRitualizationConfig.get().enableWandLinkVisualization) return;
		if (!isPlayerHoldingWand()) return;
		if (pedestals.isEmpty() || ingredients.isEmpty()) return;

		// Throttle: only spawn particles every 3 ticks 
		long currentGameTime = level.getGameTime();
		if (currentGameTime % 3 != 0) return;

		// Determine which pedestals supply at least one recipe ingredient.
		// Greedy match: each pedestal is checked against remaining ingredients.
		java.util.List<Ingredient> remainingIngredients = new java.util.ArrayList<>();
		for (Ingredient ingredient : ingredients) {
			if (!ingredient.isEmpty()) remainingIngredients.add(ingredient);
		}

		Vec3 machineCenter = new Vec3(
				machinePos.getX() + 0.5,
				machinePos.getY() + 0.5,
				machinePos.getZ() + 0.5);

		for (PedestalOfLivingRockBlockEntity pedestal : pedestals) {
			ItemStack pedestalStack = pedestal.getStack();
			if (pedestalStack.isEmpty()) continue;

			// Check if this pedestal's item matches any remaining ingredient
			boolean pedestalMatchesIngredient = false;
			for (int ingredientIndex = 0; ingredientIndex < remainingIngredients.size(); ingredientIndex++) {
				if (remainingIngredients.get(ingredientIndex).test(pedestalStack)) {
					remainingIngredients.remove(ingredientIndex);
					pedestalMatchesIngredient = true;
					break;
				}
			}
			if (!pedestalMatchesIngredient) continue;

			// Draw a particle line from pedestal top to machine center
			BlockPos pedestalPos = pedestal.getBlockPos();
			Vec3 pedestalTop = new Vec3(
					pedestalPos.getX() + 0.5,
					pedestalPos.getY() + 1.0,
					pedestalPos.getZ() + 0.5);

			drawParticleLine(level, pedestalTop, machineCenter);
		}
	}
	//endregion

	//region PARTICLE
	// Draws a line of sparkle particles between two points in world space.
	private static void drawParticleLine(Level level, Vec3 from, Vec3 to) {
		double lineDistance = from.distanceTo(to);
		int particleCount = Math.max(2, (int) (lineDistance / PARTICLE_SPACING));
		for (int particleIndex = 0; particleIndex <= particleCount; particleIndex++) {
			float progressAlongLine = (float) particleIndex / particleCount;
			double x = from.x + (to.x - from.x) * progressAlongLine;
			double y = from.y + (to.y - from.y) * progressAlongLine;
			double z = from.z + (to.z - from.z) * progressAlongLine;
			SparkleParticleData sparkleData = SparkleParticleData.fake(
					PARTICLE_SIZE, PARTICLE_R, PARTICLE_G, PARTICLE_B, PARTICLE_M);
			level.addParticle(sparkleData, true, x, y, z, 0, 0, 0);
		}
	}
	//endregion
}
