package cam72cam.mod;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.tile.TileEntity;
import cam72cam.mod.util.Facing;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;

public class World {
    public final net.minecraft.world.World internal;
    public final boolean isClient;
    public final boolean isServer;

    public World(net.minecraft.world.World world) {
        this.internal = world;
        this.isClient = world.isRemote;
        this.isServer = !world.isRemote;
    }

    public <T extends net.minecraft.tileentity.TileEntity> T getTileEntity(Vec3i pos, Class<T> cls) {
        return getTileEntity(pos, cls, true);
    }
    public <T extends net.minecraft.tileentity.TileEntity> T getTileEntity(Vec3i pos, Class<T> cls, boolean create) {
        net.minecraft.tileentity.TileEntity ent = internal.getChunkFromBlockCoords(pos.internal).getTileEntity(pos.internal, create ? Chunk.EnumCreateEntityType.IMMEDIATE : Chunk.EnumCreateEntityType.CHECK);
        if (ent != null && ent.getClass().isInstance(cls)) {
            return (T) ent;
        }
        return null;
    }
    public void setTileEntity(Vec3i pos, TileEntity tile) {
        internal.setTileEntity(pos.internal, tile);
    }

    public void setToAir(Vec3i pos) {
        internal.setBlockToAir(pos.internal);
    }

    public Block getBlock(Vec3i pos) {
        return internal.getBlockState(pos.internal).getBlock();
    }

    public long getTime() {
        return internal.getWorldTime();
    }

    public Vec3i getPrecipitationHeight(Vec3i offset) {
        return new Vec3i(internal.getPrecipitationHeight(offset.internal));
    }

    public boolean isAir(Vec3i ph) {
        return internal.isAirBlock(ph.internal);
    }

    public void setSnowLevel(Vec3i ph, int snowDown) {
        internal.setBlockState(ph.internal, Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, snowDown));
    }

    public int getSnowLevel(Vec3i ph) {
        IBlockState state = internal.getBlockState(ph.internal);
        if (state.getBlock() == Blocks.SNOW_LAYER) {
            return state.getValue(BlockSnow.LAYERS);
        }
        return 0;
    }

    public boolean isSnowBlock(Vec3i ph) {
        return internal.getBlockState(ph.internal).getBlock() == Blocks.SNOW;
    }

    public boolean isRaining() {
        return internal.isRaining();
    }

    public boolean isBlockLoaded(Vec3i parent) {
        return internal.isBlockLoaded(parent.internal);
    }

    public void breakBlock(Vec3i pos) {
        internal.destroyBlock(pos.internal, true);
    }

    public void dropItem(ItemStack stack, Vec3i pos) {
        dropItem(stack, new Vec3d(pos));
    }
    public void dropItem(ItemStack stack, Vec3d pos) {
        internal.spawnEntity(new EntityItem(internal, pos.x, pos.y, pos.z, stack.internal));
    }

    public void setBlock(Vec3i pos, Block block) {
        internal.setBlockState(pos.internal, block.getDefaultState());
    }
    public void setBlock(Vec3i pos, ItemStack item) {
        internal.setBlockState(pos.internal, BlockUtil.itemToBlockState(item.internal));
    }

    public boolean isTopSolid(Vec3i pos) {
        return internal.getBlockState(pos.internal).isTopSolid();
    }

    public int getRedstone(Vec3i pos) {
        int power = 0;
        for (Facing facing : Facing.values()) {
            power = Math.max(power, internal.getRedstonePower(pos.offset(facing).internal, facing.internal));
        }
        return power;
    }

    public void removeEntity(Entity entity) {
        internal.removeEntity(entity);
    }
}
