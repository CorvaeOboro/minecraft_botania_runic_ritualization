/*
 * # Botania Runic Ritualization
 * - a Botania addon with batch crafting for runic altar, petal apothecary, and botanical brewery recipes 
 * - draws ingredients from adjacent Pedestals of Living Rock 
 *
 * # BotanicalBreweryAludelBlock.java
 * - Block opens the Botanical Brewery Aludel menu on right-click.
 * - Provides a server-side ticker on its block entity.
 * - Drops container and output contents on break.
 */
package corvaeoboro.botania_runic_ritualization.block;

import corvaeoboro.botania_runic_ritualization.BotaniaRunicRitualization;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
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

public class BotanicalBreweryAludelBlock extends Block implements EntityBlock {
	//region SHAPE
	// 3-cuboid approximation of botanical_brewery_aludel.json:
	// base holder (y0-3), flasks+column (y3-8), center neck (y8-15).
	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(0, 0, 0, 16, 3, 16),     // base holder
			Block.box(1, 3, 1, 15, 8, 15),     // corner flasks + center column
			Block.box(4, 8, 4, 12, 15, 12)     // center flask neck
	);

	public BotanicalBreweryAludelBlock(Properties properties) {
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
		return new BotanicalBreweryAludelBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return type == BotaniaRunicRitualization.ALUDEL_BE_TYPE
				? (lvl, pos, st, blockEntity) -> BotanicalBreweryAludelBlockEntity.serverTick(lvl, pos, st, (BotanicalBreweryAludelBlockEntity) blockEntity)
				: null;
	}
	//endregion

	//region INTERACT
	@SuppressWarnings("deprecation")
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
			if (blockEntityAtPos instanceof BotanicalBreweryAludelBlockEntity aludel) {
				serverPlayer.openMenu(aludel);
			}
		}
		return InteractionResult.CONSUME;
	}

	@SuppressWarnings("deprecation")
	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean moved) {
		BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
		if (blockEntityAtPos instanceof BotanicalBreweryAludelBlockEntity aludel) {
			aludel.onNeighborChanged();
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
		BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
		return blockEntityAtPos instanceof BotanicalBreweryAludelBlockEntity aludel ? aludel.getSignal() : 0;
	}
	//endregion

	//region DROP
	@SuppressWarnings("deprecation")
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.is(newState.getBlock())) {
			BlockEntity blockEntityAtPos = level.getBlockEntity(pos);
			if (blockEntityAtPos instanceof BotanicalBreweryAludelBlockEntity aludel) {
				ItemStack containerStack = aludel.getContainerStack();
				if (!containerStack.isEmpty()) {
					Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, containerStack);
				}
				ItemStack outputStack = aludel.getOutputStack();
				if (!outputStack.isEmpty()) {
					Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, outputStack);
				}
			}
			super.onRemove(state, level, pos, newState, moved);
		}
	}
	//endregion
}
