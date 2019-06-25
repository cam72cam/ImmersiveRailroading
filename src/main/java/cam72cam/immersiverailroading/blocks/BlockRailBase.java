package cam72cam.immersiverailroading.blocks;

import cam72cam.immersiverailroading.IRItems;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.items.ItemTrackBlueprint;
import cam72cam.immersiverailroading.library.ChatText;
import cam72cam.immersiverailroading.library.SwitchState;
import cam72cam.immersiverailroading.tile.TileRail;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.tile.TileRailGag;
import cam72cam.immersiverailroading.util.SwitchUtil;
import cam72cam.mod.block.BlockEntityBase;
import cam72cam.mod.block.BlockSettings;
import cam72cam.mod.block.IBreakCancelable;
import cam72cam.mod.block.Material;
import cam72cam.mod.entity.Player;
import cam72cam.mod.item.Fuzzy;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.text.PlayerMessage;
import cam72cam.mod.util.Facing;
import cam72cam.mod.util.Hand;
import cam72cam.mod.util.TagCompound;
import cam72cam.mod.world.World;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import javax.annotation.Nonnull;

public abstract class BlockRailBase extends BlockEntityBase<TileRailBase> implements IBreakCancelable {
	public BlockRailBase(BlockSettings settings) {
		super(settings
                .withConnectable(false)
                .withMaterial(Material.METAL)
                .withHardness(1F)
        );
	}

	@Override
	public void onBreak(TileRailBase te) {
		if (te instanceof TileRail) {
			((TileRail) te).spawnDrops();
		}

		breakParentIfExists(te);
	}

	@Override
	public boolean onClick(TileRailBase te, Player player, Hand hand, Facing facing, Vec3d hit) {
		ItemStack stack = player.getHeldItem(hand);
        if (stack.item == IRItems.ITEM_SWITCH_KEY) {
            TileRail tileSwitch = te.findSwitchParent();
            if (tileSwitch != null) {
                SwitchState switchForced = te.cycleSwitchForced();
                if (te.world.isServer) {
                    player.sendMessage(switchForced.equals(SwitchState.NONE) ? ChatText.SWITCH_UNLOCKED.getMessage() : ChatText.SWITCH_LOCKED.getMessage(switchForced.toString()));
                }
            }
        }
        if (stack.is(Fuzzy.REDSTONE_DUST)) {
            String next = te.nextAugmentRedstoneMode();
            if (next != null) {
                if (te.world.isServer) {
                    player.sendMessage(PlayerMessage.direct(next));
                }
                return true;
            }
        }
        if (stack.is(Fuzzy.SNOW_LAYER)) {
            if (te.world.isServer) {
                te.handleSnowTick();
            }
            return true;
        }
        if (stack.is(Fuzzy.SNOW_BLOCK)) {
            if (te.world.isServer) {
                for (int i = 0; i < 8; i ++) {
                    te.handleSnowTick();
                }
            }
            return true;
        }
        if (stack.item.getToolClasses(stack.internal).contains("shovel")) {
            if (te.world.isServer) {
                te.cleanSnow();
                te.setSnowLayers(0);
                stack.internal.damageItem(1, player.internal);
            }
            return true;
        }
        return false;
	}

	@Override
	public ItemStack onPick(TileRailBase rail) {
		ItemStack stack = new ItemStack(IRItems.ITEM_TRACK_BLUEPRINT, 1);
		if (!rail.isLoaded()) {
			return stack;
		}

		TileRail parent = rail.getParentTile();
		if (parent == null) {
			return stack;
		}
		ItemTrackBlueprint.settings(stack, parent.info.settings);
		return stack;
	}

    public void onNeighborChange(TileRailBase te, Vec3i neighbor) {
        World world = te.world;
        if (world.isClient) {
            return;
        }

        te.blockUpdate = true;

        if (new ItemStack(world.getBlock(te.pos.up())).is(Fuzzy.SNOW_LAYER)) {
            if (te.handleSnowTick()) {
                world.setToAir(te.pos.up());
            }
        }

        TagCompound data = te.getReplaced();
        while (true) {
            if (te.getParentTile() != null && te.getParentTile().getParentTile() != null) {
                TileRail switchTile = te.getParentTile();
                if (te instanceof TileRail) {
                    switchTile = (TileRail) te;
                }
                SwitchState state = SwitchUtil.getSwitchState(switchTile);
                if (state != SwitchState.NONE) {
                    switchTile.setSwitchState(state);
                }
            }
            if (data == null) {
                break;
            }
            te = new TileRailBase();
            te.load(data);
            te.setWorld(world);
            data = te.getReplaced();
        }
    }

	public static void breakParentIfExists(TileRailBase te) {
		TileRail parent = te.getParentTile();
		if (parent != null && !te.getWillBeReplaced()) {
            parent.spawnDrops();
            //if (tryBreak(te.getWorld(), te.getPos())) {
            te.world.setToAir(parent.pos);
            //}
		}
	}
    public boolean tryBreak(World world, Vec3i pos, Player player) {
        try {
            TileRailBase rail = world.getTileEntity(pos, TileRailBase.class);
            if (rail != null) {
                if (rail.getReplaced() != null) {
                    // new object here is important
                    TileRailGag newGag = new TileRailGag();
                    newGag.load(rail.getReplaced());
                    while(true) {
                        // Only do replacement if parent still exists
                        if (newGag.getParent() != null && world.getTileEntity(newGag.getParent(), TileRailBase.class) != null) {
                            rail.world.setTileEntity(pos, newGag);
                            newGag.markDirty();
                            breakParentIfExists(rail);
                            return false;
                        }

                        TagCompound data = newGag.getReplaced();
                        if (data == null) {
                            break;
                        }

                        newGag = new TileRailGag();
                        newGag.load(data);
                    }
                }
            }
        } catch (StackOverflowError ex) {
            ImmersiveRailroading.error("Invalid recursive rail block at %s", pos);
            ImmersiveRailroading.catching(ex);
            world.setToAir(pos);
        }
        return true;
    }


    @Override
    public double getHeight(TileRailBase te) {
        //TODO collision height
        // height = te.getFullHeight() +0.1 * (te.getTrackGauge() / Gauge.STANDARD);
        return te.getFullHeight();
    }


	public static final PropertyObject RAIL_DATA = new PropertyObject("RAIL_DATA");

	@Override
    @Nonnull
    protected BlockStateContainer createBlockState()
    {
        return new ExtendedBlockState(this, new IProperty[0], new IUnlistedProperty<?>[] {RAIL_DATA});
    }

	@Override
    public IBlockState getExtendedState(IBlockState origState, IBlockAccess internal, BlockPos pos)
    {
    	IExtendedBlockState state = (IExtendedBlockState)origState;
    	TileRailBase te = World.get((net.minecraft.world.World) internal).getTileEntity(new Vec3i(pos), TileRailBase.class);
        state = state.withProperty(RAIL_DATA, te);
        return state;
    }
}
