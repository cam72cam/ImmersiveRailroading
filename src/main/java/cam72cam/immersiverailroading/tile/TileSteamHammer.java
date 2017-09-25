package cam72cam.immersiverailroading.tile;

import blusunrize.immersiveengineering.common.IEContent;
import blusunrize.immersiveengineering.common.blocks.BlockTypes_MetalsAll;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public class TileSteamHammer extends TileEntity implements ITickable {
	private int craftProgress = 0;
	private ItemStack chosenItem = new ItemStack(Items.AIR);
	private ItemStackHandler itemStackHandler = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
        	TileSteamHammer.this.markDirty();
        }
    };
    
    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("items")) {
            itemStackHandler.deserializeNBT((NBTTagCompound) compound.getTag("items"));
        }
        this.craftProgress = compound.getInteger("craftProgress");
        this.chosenItem = new ItemStack(compound.getCompoundTag("chosenItem"));
        System.out.println("SYNC" + this.chosenItem);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setTag("items", itemStackHandler.serializeNBT());
        compound.setInteger("craftProgress", craftProgress);
        compound.setTag("chosenItem", chosenItem.serializeNBT());
        return compound;
    }
    
    @Override
	public SPacketUpdateTileEntity getUpdatePacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		this.writeToNBT(nbt);
		
		return new SPacketUpdateTileEntity(this.getPos(), 1, nbt);
	}

	@Override
	public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
		this.readFromNBT(pkt.getNbtCompound());
		super.onDataPacket(net, pkt);
		world.markBlockRangeForRenderUpdate(getPos(), getPos());
	}
	
	@Override
	public NBTTagCompound getUpdateTag() {
		NBTTagCompound tag = super.getUpdateTag();
		this.writeToNBT(tag);
		return tag;
	}
	
	@Override 
	public void handleUpdateTag(NBTTagCompound tag) {
		this.readFromNBT(tag);
		super.handleUpdateTag(tag);
		world.markBlockRangeForRenderUpdate(getPos(), getPos());
	}

	@Override
	public void update() {
		if (world.isRemote) {
			return;
		}
		
		
		ItemStack input = this.itemStackHandler.getStackInSlot(0);
		ItemStack output = this.itemStackHandler.getStackInSlot(1);
		ItemStack steel = new ItemStack(IEContent.blockStorage,1, BlockTypes_MetalsAll.STEEL.getMeta());
		if (craftProgress == 0) {
			if (this.chosenItem.getItem() != Items.AIR && !input.isEmpty() && output.isEmpty() && input.isItemEqual(steel)) {
				craftProgress = 100;
				input.setCount(input.getCount() - 1);
				this.itemStackHandler.setStackInSlot(0, input);
			}
		}
		
		if (craftProgress == 1) {
			this.chosenItem.setCount(1);
			
			if (!output.isEmpty()) {
				this.world.spawnEntity(new EntityItem(world, this.pos.getX(), this.pos.getY()+1, this.pos.getZ(), output));
			}
			
			this.itemStackHandler.setStackInSlot(1, this.chosenItem.copy());
		}
		
		if (craftProgress != 0) {
			this.craftProgress -= 1;
			this.markDirty();
		}
	}
	
	@Override
	public void markDirty() {
		super.markDirty();
		
		world.markBlockRangeForRenderUpdate(getPos(), getPos());
		world.notifyBlockUpdate(getPos(), world.getBlockState(getPos()), world.getBlockState(getPos()), 3);
	}
    
    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemStackHandler);
        }
        return super.getCapability(capability, facing);
    }
    
    
	@Override
	@SideOnly(Side.CLIENT)
	public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
		return INFINITE_EXTENT_AABB;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared()
	{
		return Double.MAX_VALUE;
	}

	public float getCraftProgress() {
		return craftProgress;
	}
	
	public ItemStack getChoosenItem() {
		return chosenItem;
	}

	public void setChoosenItem(ItemStack selected) {
		System.out.println("CHOOSEN: " + selected);
		selected.setCount(1);
		this.chosenItem = selected.copy();
		this.markDirty();
	}
}
