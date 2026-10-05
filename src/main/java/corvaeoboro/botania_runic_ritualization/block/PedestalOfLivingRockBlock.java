/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # PedestalOfLivingRockBlock.java
 * - Pedestal block: holds up to a stack (default 64) of a single item type.
 * - Right-click opens a single-slot UI; players insert/extract via slot interaction.
 *   Shift-clicking from inventory pushes a full stack into the pedestal.
 * - Used as ingredient source for adjacent machines (the BE is queried directly for ingredient consumption).
 */
package corvaeoboro.botania_runic_ritualization.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

public class PedestalOfLivingRockBlock extends Block implements EntityBlock {
	//region SHAPE
	// 3-cuboid approximation of pedestal_of_living_rock.json:
	// base slab (y0-2), pillar+ring (y2-12), top slab (y12-14).
	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(3, 0, 3, 13, 2, 13),     // base slab
			Block.box(4, 2, 4, 12, 12, 12),    // pillar + ring
			Block.box(3, 12, 3, 13, 14, 13)    // top slab
	);

	public PedestalOfLivingRockBlock(Properties properties) {
		super(properties);
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
	//endregion

	//region BLOCKENTITY
	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PedestalOfLivingRockBlockEntity(pos, state);
	}
	//endregion

	//region INTERACT
	// Right-click opens the inventory UI regardless of held item.
	@SuppressWarnings("deprecation")
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
		if (!(blockEntityAtPos instanceof PedestalOfLivingRockBlockEntity pedestal)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.openMenu(pedestal);
		}
		return InteractionResult.CONSUME;
	}
	//endregion

	//region DROP
	@SuppressWarnings("deprecation")
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock())) {
			BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
			if (blockEntityAtPos instanceof PedestalOfLivingRockBlockEntity pedestal) {
				ItemStack heldStack = pedestal.getStack();
				if (!heldStack.isEmpty()) {
					Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, heldStack);
				}
			}
			super.onRemove(state, level, pos, newState, moved);
		}
	}
	//endregion
}
