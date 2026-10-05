/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # PetalApothecaryEverflowingBlock.java
 * - Petal Apothecary of the Everflowing: crafts botania:petal_apothecary recipes using ingredients from adjacent pedestals.
 * - Mana powers each craft; the translucent water texture renders statically (no biome tinting, no mana-driven fluid state).
 * - Right-click opens a UI with a seed/reagent slot, mana storage, and crafting cooldown display.
 */
package corvaeoboro.botania_runic_ritualization.block;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;

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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

public class PetalApothecaryEverflowingBlock extends Block implements EntityBlock {

	//region SHAPE
	// 3-cuboid approximation of petal_apothecary_everflowing.json:
	// base+feet (y0-3), pillar (y3-11), goblet (y11-16).
	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(2, 0, 2, 14, 3, 14),     // base plinth + corner feet
			Block.box(5, 3, 5, 11, 11, 11),    // middle pillar
			Block.box(2, 11, 2, 14, 16, 14)    // goblet bowl + walls
	);

	public PetalApothecaryEverflowingBlock(Properties properties) {
		super(properties);
	}

	@SuppressWarnings("deprecation")
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@SuppressWarnings("deprecation")
	@Override
	public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return SHAPE;
	}
	//endregion

	//region BLOCKENTITY
	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PetalApothecaryEverflowingBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) return null;
		return type == BotaniaRunicRitualization.APOTHECARY_BE_TYPE
				? (lvl, pos, st, blockEntity) -> PetalApothecaryEverflowingBlockEntity.serverTick(lvl, pos, st, (PetalApothecaryEverflowingBlockEntity) blockEntity)
				: null;
	}
	//endregion

	//region INTERACT
	@SuppressWarnings("deprecation")
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (player instanceof ServerPlayer serverPlayer) {
			BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
			if (blockEntityAtPos instanceof PetalApothecaryEverflowingBlockEntity apothecary) {
				serverPlayer.openMenu(apothecary);
			}
		}
		return InteractionResult.CONSUME;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean moved) {
		BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
		if (blockEntityAtPos instanceof PetalApothecaryEverflowingBlockEntity apothecary) {
			apothecary.onNeighborChanged();
		}
	}
	//endregion

	//region SIGNAL
	@SuppressWarnings("deprecation")
	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@SuppressWarnings("deprecation")
	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		// 0 idle, 1 collecting mana / has seed, 2 ready to craft
		BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
		return blockEntityAtPos instanceof PetalApothecaryEverflowingBlockEntity apothecary ? apothecary.getSignal() : 0;
	}
	//endregion

	//region DROP
	@SuppressWarnings("deprecation")
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock())) {
			BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
			if (blockEntityAtPos instanceof PetalApothecaryEverflowingBlockEntity apothecary) {
				ItemStack seedStack = apothecary.getSeedStack();
				if (!seedStack.isEmpty()) {
					Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, seedStack);
				}
				ItemStack outputStack = apothecary.getOutputStack();
				if (!outputStack.isEmpty()) {
					Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, outputStack);
				}
			}
			super.onRemove(state, level, pos, newState, moved);
		}
	}
	//endregion
}
