/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # RunicAltarDaisBlockEntityRenderer.java
 * - Renders the "ritual" presentation on top of the Runic Altar Dais.
 * - Floating Living Rock at center (the reagent in the input slot).
 * - Rotating ring of ingredient previews pulled live from adjacent PedestalsOfLivingRock
 * - Mana-progress star overlay using Botania's RenderHelper.renderStar, scaled by mana fill.
 * - Wand-link visualization: sparkling blue lines from active pedestals when holding a Botania wand.
 */
package corvaeoboro.botania_runic_ritualization.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import corvaeoboro.botania_runic_ritualization.block.PedestalOfLivingRockBlockEntity;
import corvaeoboro.botania_runic_ritualization.block.RunicAltarDaisBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

import org.joml.Quaternionf;

import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.client.core.helper.RenderHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RunicAltarDaisBlockEntityRenderer implements BlockEntityRenderer<RunicAltarDaisBlockEntity> {

	//region FIELD
	private static final ItemStack LIVINGROCK_DISPLAY = makeLivingrockDisplay();
	private static final ResourceLocation CUBE_TEX = new ResourceLocation("botania", "textures/block/runic_altar_cube.png");
	private final ModelPart spinningCube;
	//endregion

	//region CONSTRUCT
	public RunicAltarDaisBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		MeshDefinition mesh = new MeshDefinition();
		mesh.getRoot().addOrReplaceChild("cube", CubeListBuilder.create().addBox(0, 0, 0, 1, 1, 1), PartPose.ZERO);
		spinningCube = LayerDefinition.create(mesh, 16, 16).bakeRoot();
	}
	//endregion

	//region RENDER
	@Override
	public void render(RunicAltarDaisBlockEntity blockEntity, float partialTick, PoseStack poseStack,
			MultiBufferSource buffers, int light, int overlay) {
		Level level = blockEntity.getLevel();
		if (level == null) return;

		float time = (level.getGameTime() % 720000L) + partialTick;
		ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

		// 0) Floating output item above the block (always visible when a recipe is selected)
		RunicAltarRecipe recipe = resolveRecipe(blockEntity);
		if (recipe != null) {
			ItemStack outputPreview = ((Recipe<?>) recipe).getResultItem(level.registryAccess());
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

		// 0b) Spinning cubes trail (always active, independent of crafting state).
		// Mirrors Botania's runic altar cube trail 
		renderSpinningCubes(poseStack, buffers, overlay, 2, 15, time);

		// VFX only shows during an active crafting process (charging, ready, or cooldown).
		// Stalled machines (missing ingredients, missing mana, no input) show no VFX.
		if (!blockEntity.isCraftingActive()) return;

		// 1) Floating Living Rock at center (only when the input slot is populated).
		ItemStack livingrockStack = blockEntity.getLivingrockStack();
		if (!livingrockStack.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(0.5F, 1.35F, 0.5F);
			poseStack.scale(0.55F, 0.55F, 0.55F);
			poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.5F));
			poseStack.translate(0, Math.sin(time / 8.0) * 0.04, 0);
			itemRenderer.renderStatic(livingrockStack, ItemDisplayContext.GROUND, light, overlay, poseStack, buffers, level, 0);
			poseStack.popPose();
		}

		// 2) Ingredient ring: one item per non-empty recipe ingredient. Pulled from adjacent
		// pedestals when available, otherwise shown as the ingredient's first matching item.
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

		// 3) Mana progress star.
		int manaToGet = blockEntity.getManaToGetPublic();
		int currentMana = blockEntity.getCurrentManaPublic();
		if (manaToGet > 0 && currentMana > 0) {
			float starScale = Math.min(1F, (float) currentMana / (float) manaToGet) / 75F;
			if (starScale > 0F) {
				int positionSeed = blockEntity.getBlockPos().getX() ^ blockEntity.getBlockPos().getY() ^ blockEntity.getBlockPos().getZ();
				poseStack.pushPose();
				poseStack.translate(0.5F, 1.35F, 0.5F);
				RenderHelper.renderStar(poseStack, buffers, 0x00E4D7, starScale, starScale, starScale, positionSeed);
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
	public boolean shouldRenderOffScreen(RunicAltarDaisBlockEntity blockEntity) {
		// Allow the star / orbiting items to keep rendering even when the block origin is offscreen.
		return true;
	}
	//endregion

	//region HELPER
	private static RunicAltarRecipe resolveRecipe(RunicAltarDaisBlockEntity blockEntity) {
		ResourceLocation recipeId = blockEntity.getSelectedRecipeId();
		if (recipeId == null) return null;
		Level level = blockEntity.getLevel();
		if (level == null) return null;
		Optional<? extends Recipe<?>> recipeByKey = level.getRecipeManager().byKey(recipeId);
		return recipeByKey.isPresent() && recipeByKey.get() instanceof RunicAltarRecipe runicAltarRecipe ? runicAltarRecipe : null;
	}

	/**
	 * Build the ring list: for each non-empty ingredient of the recipe, prefer the stack
	 * actually present on an adjacent pedestal (greedy, one pedestal consumed per slot).
	 * If none matches, fall back to the first item in the ingredient definition.
	 */
	private static List<ItemStack> buildRingStacks(RunicAltarDaisBlockEntity blockEntity, RunicAltarRecipe recipe) {
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

	private static ItemStack makeLivingrockDisplay() {
		// Resolved lazily if Botania is present at runtime; falls back to air otherwise.
		net.minecraft.world.item.Item livingrockItem = BuiltInRegistries.ITEM.get(new ResourceLocation("botania", "livingrock"));
		return livingrockItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(livingrockItem);
	}
	//endregion

	//region CUBETRAIL
	/**
	 * Renders the spinning cube trail around the runic altar dais.
	 * This is always active, independent of crafting state, mirroring Botania's runic altar.
	 * The cubes orbit in an elliptical path with rotation and bobbing, with trailing
	 * translucent copies for a motion-blur-like effect.
	 */
	private void renderSpinningCubes(PoseStack poseStack, MultiBufferSource buffers, int overlay,
			int cubeCount, int trailIterations, float time) {
		for (int currentIteration = trailIterations; currentIteration > 0; currentIteration--) {
			final float modifier = 6F;
			final float rotationModifier = 0.2F;
			final float radiusBase = 0.35F;
			final float radiusMod = 0.05F;

			double ticks = time - 1.3 * (trailIterations - currentIteration);
			float offsetPerCube = 360F / cubeCount;

			poseStack.pushPose();
			poseStack.translate(0.5F, 1.0F, 0.5F);
			for (int cubeIndex = 0; cubeIndex < cubeCount; cubeIndex++) {
				float angleOffset = offsetPerCube * cubeIndex;
				float degrees = (int) (ticks / rotationModifier % 360F + angleOffset);
				float radians = (float) Math.toRadians(degrees);
				float radiusX = (float) (radiusBase + radiusMod * Math.sin(ticks / modifier));
				float radiusZ = (float) (radiusBase + radiusMod * Math.cos(ticks / modifier));
				float x = (float) (radiusX * Math.cos(radians));
				float z = (float) (radiusZ * Math.sin(radians));
				float y = (float) Math.cos((ticks + 50 * cubeIndex) / 5F) / 10F;

				poseStack.pushPose();
				poseStack.translate(x, y, z);
				float xRotate = (float) Math.sin(ticks * rotationModifier) / 2F;
				float yRotate = (float) Math.max(0.6F, Math.sin(ticks * 0.1F) / 2F + 0.5F);
				float zRotate = (float) Math.cos(ticks * rotationModifier) / 2F;

				poseStack.mulPose(new Quaternionf().rotateAxis(radians, xRotate, yRotate, zRotate));
				float cubeAlpha = 1;
				if (currentIteration < trailIterations) {
					cubeAlpha = (float) currentIteration / (float) trailIterations * 0.4F;
				}

				VertexConsumer vertexBuffer = buffers.getBuffer(
						currentIteration < trailIterations ? RenderType.entityTranslucentCull(CUBE_TEX) : RenderType.entitySolid(CUBE_TEX));
				spinningCube.render(poseStack, vertexBuffer, 0xF000F0, overlay, 1, 1, 1, cubeAlpha);

				poseStack.popPose();
			}
			poseStack.popPose();
		}
	}
	//endregion
}
