/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # BotanicalBreweryAludelBlockEntityRenderer.java
 * - Renders the ritual presentation on top of the Botanical Brewery Aludel.
 * - Floating brew container at center (the item in the input slot).
 * - Rotating ring of ingredient previews from adjacent/linked pedestals.
 * - Mana-progress star overlay scaled by mana fill percentage (green tint for brewery).
 * - Wand-link visualization: sparkling blue lines from active pedestals when holding a Botania wand.
 */
package corvaeoboro.botania_runic_ritualization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import corvaeoboro.botania_runic_ritualization.block.BotanicalBreweryAludelBlockEntity;
import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

import vazkii.botania.api.recipe.BotanicalBreweryRecipe;
import vazkii.botania.client.core.helper.RenderHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BotanicalBreweryAludelBlockEntityRenderer implements BlockEntityRenderer<BotanicalBreweryAludelBlockEntity> {

	public BotanicalBreweryAludelBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

	//region RENDER
	@Override
	public void render(BotanicalBreweryAludelBlockEntity blockEntity, float partialTick, PoseStack poseStack,
			MultiBufferSource buffers, int light, int overlay) {
		Level level = blockEntity.getLevel();
		if (level == null) return;

		float time = (level.getGameTime() % 720000L) + partialTick;
		ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

		// 0) Floating output item above the block (always visible when a recipe is selected)
		BotanicalBreweryRecipe recipe = resolveRecipe(blockEntity);
		if (recipe != null) {
			ItemStack vialForLookup = blockEntity.getContainerStack().isEmpty()
					? new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE)
					: blockEntity.getContainerStack();
			ItemStack outputPreview = recipe.getOutput(vialForLookup);
			if (!outputPreview.isEmpty()) {
				poseStack.pushPose();
				poseStack.translate(0.5F, 1.15F, 0.5F);
				poseStack.scale(0.85F, 0.85F, 0.85F);
				poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.0F));
				poseStack.translate(0, Math.sin(time / 10.0) * 0.03, 0);
				itemRenderer.renderStatic(outputPreview, ItemDisplayContext.GROUND, light, overlay, poseStack, buffers, level, 0);
				poseStack.popPose();
			}
		}

		// VFX only shows during an active crafting process (charging, ready, or cooldown).
		// Stalled machines (missing ingredients, missing mana, no container) show no VFX.
		if (!blockEntity.isCraftingActive()) return;

		// 1) Floating container at center
		ItemStack containerStack = blockEntity.getContainerStack();
		if (!containerStack.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(0.5F, 1.35F, 0.5F);
			poseStack.scale(0.55F, 0.55F, 0.55F);
			poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.5F));
			poseStack.translate(0, Math.sin(time / 8.0) * 0.04, 0);
			itemRenderer.renderStatic(containerStack, ItemDisplayContext.GROUND, light, overlay, poseStack, buffers, level, 0);
			poseStack.popPose();
		}

		// 2) Ingredient ring
		if (recipe != null) {
			List<ItemStack> ringStacks = buildRingStacks(blockEntity, recipe);
			int ringCount = ringStacks.size();
			if (ringCount > 0) {
				float anglePerStack = 360F / ringCount;
				for (int ringIndex = 0; ringIndex < ringCount; ringIndex++) {
					ItemStack ringStack = ringStacks.get(ringIndex);
					if (ringStack.isEmpty()) continue;
					poseStack.pushPose();
					poseStack.translate(0.5F, 1.35F, 0.5F);
					poseStack.mulPose(Axis.YP.rotationDegrees(anglePerStack * ringIndex + time));
					poseStack.translate(1.0F, Math.sin((time + ringIndex * 10) / 5D) * 0.075, 0F);
					poseStack.mulPose(Axis.YP.rotationDegrees(90F));
					poseStack.scale(0.5F, 0.5F, 0.5F);
					itemRenderer.renderStatic(ringStack, ItemDisplayContext.GROUND, light, overlay, poseStack, buffers, level, 0);
					poseStack.popPose();
				}
			}
		}

		// 3) Mana progress star (green tint for brewery)
		int manaToGet = blockEntity.getManaToGetPublic();
		int currentMana = blockEntity.getCurrentManaPublic();
		if (manaToGet > 0 && currentMana > 0) {
			float starScale = Math.min(1F, (float) currentMana / (float) manaToGet) / 75F;
			if (starScale > 0F) {
				int positionSeed = blockEntity.getBlockPos().getX() ^ blockEntity.getBlockPos().getY() ^ blockEntity.getBlockPos().getZ();
				poseStack.pushPose();
				poseStack.translate(0.5F, 1.35F, 0.5F);
				RenderHelper.renderStar(poseStack, buffers, 0x33FF44, starScale, starScale, starScale, positionSeed);
				poseStack.popPose();
			}
		}

		// 4) Wand-link visualization: sparkling blue lines from active pedestals to this machine.
		if (recipe != null && manaToGet > 0) {
			WandLinkVisualizer.drawPedestalLinks(level, blockEntity.getBlockPos(),
					blockEntity.getAdjacentPedestals(), ((Recipe<?>) recipe).getIngredients());
		}
	}

	@Override
	public boolean shouldRenderOffScreen(BotanicalBreweryAludelBlockEntity blockEntity) {
		return true;
	}
	//endregion

	//region HELPER
	private static BotanicalBreweryRecipe resolveRecipe(BotanicalBreweryAludelBlockEntity blockEntity) {
		ResourceLocation recipeId = blockEntity.getSelectedRecipeId();
		if (recipeId == null) return null;
		Level level = blockEntity.getLevel();
		if (level == null) return null;
		Optional<? extends Recipe<?>> recipeByKey = level.getRecipeManager().byKey(recipeId);
		return recipeByKey.isPresent() && recipeByKey.get() instanceof BotanicalBreweryRecipe breweryRecipe ? breweryRecipe : null;
	}

	private static List<ItemStack> buildRingStacks(BotanicalBreweryAludelBlockEntity blockEntity, BotanicalBreweryRecipe recipe) {
		List<ItemStack> ringStacks = new ArrayList<>();
		List<PedestalOfLivingRockBlockEntity> availablePedestals = new ArrayList<>(blockEntity.getAdjacentPedestals());
		NonNullList<Ingredient> ingredients = ((Recipe<?>) recipe).getIngredients();
		for (Ingredient ingredient : ingredients) {
			if (ingredient.isEmpty()) continue;
			ItemStack chosenStack = ItemStack.EMPTY;
			for (int pedestalIndex = 0; pedestalIndex < availablePedestals.size(); pedestalIndex++) {
				PedestalOfLivingRockBlockEntity pedestal = availablePedestals.get(pedestalIndex);
				ItemStack pedestalStack = pedestal.getStack();
				if (!pedestalStack.isEmpty() && ingredient.test(pedestalStack)) {
					chosenStack = pedestalStack;
					availablePedestals.remove(pedestalIndex);
					break;
				}
			}
			if (chosenStack.isEmpty()) {
				ItemStack[] matchingStacks = ingredient.getItems();
				if (matchingStacks.length > 0) {
					chosenStack = matchingStacks[(int) ((System.currentTimeMillis() / 1000) % matchingStacks.length)];
				}
			}
			if (!chosenStack.isEmpty()) ringStacks.add(chosenStack);
		}
		return ringStacks;
	}
	//endregion
}
