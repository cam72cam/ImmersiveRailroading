package cam72cam.immersiverailroading.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.Config.ConfigDamage;
import cam72cam.immersiverailroading.Config.ConfigDebug;
import cam72cam.immersiverailroading.library.Augment;
import cam72cam.immersiverailroading.library.StockDeathType;
import cam72cam.immersiverailroading.physics.MovementSimulator;
import cam72cam.immersiverailroading.physics.PhysicsAccummulator;
import cam72cam.immersiverailroading.physics.TickPos;
import cam72cam.immersiverailroading.proxy.CommonProxy;
import cam72cam.immersiverailroading.sound.ISound;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.immersiverailroading.util.BufferUtil;
import cam72cam.immersiverailroading.util.RedstoneUtil;
import cam72cam.immersiverailroading.util.Speed;
import cam72cam.immersiverailroading.util.VecUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk.EnumCreateEntityType;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class EntityMoveableRollingStock extends EntityRidableRollingStock {

	private Float frontYaw;
	private Float rearYaw;
	public float distanceTraveled = 0;
	public float renderDistanceTraveled = 0;
	public double tickPosID = 0;
	private Speed currentSpeed;
	public List<TickPos> positions = new ArrayList<TickPos>();
	private AxisAlignedBB boundingBox;
	private double[][] heightMapCache;
	private double tickSkew = 1;
	/**
	 * Please validate the BlockPos before putting into destructionQueue!
	 */
	private Map<BlockPos, Integer> destructionQueue = new HashMap<BlockPos, Integer>();
	private List<Integer> destructionTicks = new ArrayList<Integer>();

	private float sndRand;

	private ISound wheel_sound;

	public EntityMoveableRollingStock(World world, String defID) {
		super(world, defID);
	}

	@Override
	public void readSpawnData(ByteBuf additionalData) {
		super.readSpawnData(additionalData);
		frontYaw = BufferUtil.readFloat(additionalData);
		rearYaw = BufferUtil.readFloat(additionalData);
		tickPosID = additionalData.readInt();
		tickSkew = additionalData.readDouble();
		
		positions = new ArrayList<TickPos>();
		
		for (int numPositions =additionalData.readInt(); numPositions > 0; numPositions --) {
			TickPos pos = new TickPos();
			pos.read(additionalData);
			positions.add(pos);
		}
	}

	@Override
	public void writeSpawnData(ByteBuf buffer) {
		super.writeSpawnData(buffer);
		BufferUtil.writeFloat(buffer, frontYaw);
		BufferUtil.writeFloat(buffer, rearYaw);
		buffer.writeInt((int)tickPosID);
		double tickTime = ConfigDebug.serverTickCompensation ? 20 : CommonProxy.getServerTPS(world, positions.size());
		buffer.writeDouble(tickTime/20);
		
		buffer.writeInt(positions.size());
		for (TickPos pos : positions ) {
			pos.write(buffer);
		}
	}

	@Override
	protected void writeEntityToNBT(NBTTagCompound nbttagcompound) {
		super.writeEntityToNBT(nbttagcompound);
		if (frontYaw != null) {
			nbttagcompound.setFloat("frontYaw", frontYaw);
		}
		if (rearYaw != null) {
			nbttagcompound.setFloat("rearYaw", rearYaw);
		}
		nbttagcompound.setFloat("distanceTraveled", distanceTraveled);
	}

	@Override
	protected void readEntityFromNBT(NBTTagCompound nbttagcompound) {
		super.readEntityFromNBT(nbttagcompound);
		if (nbttagcompound.hasKey("frontYaw")) {
			frontYaw = nbttagcompound.getFloat("frontYaw");
		}
		if (nbttagcompound.hasKey("rearYaw")) {
			rearYaw = nbttagcompound.getFloat("rearYaw");
		}
		distanceTraveled = nbttagcompound.getFloat("distanceTraveled");
		
		if (frontYaw == null) {
			frontYaw = rotationYaw;
		}
		if (rearYaw == null) {
			rearYaw = rotationYaw;
		}
		initPositions();
	}
	
	public void initPositions() {
		this.positions = new ArrayList<TickPos>();
		this.positions.add(new TickPos((int)this.tickPosID, this.getCurrentSpeed(), this.getPositionVector(), this.rotationYaw, this.rotationYaw, this.rotationYaw, this.rotationPitch, false));
	}

	public void initPositions(TickPos tp) {
		this.positions = new ArrayList<TickPos>();
		this.positions.add(tp);
	}

	/*
	 * Entity Overrides for BB
	 */

	@Override
	public AxisAlignedBB getCollisionBoundingBox() {
		return this.getEntityBoundingBox().contract(0, 0.5, 0).offset(0, 0.5, 0);
	}
	
	public void clearHeightMap() {
		this.heightMapCache = null;
		this.boundingBox = null;
	}
	
	private double[][] getHeightMap() {
		if (this.heightMapCache == null) {
			this.heightMapCache = this.getDefinition().createHeightMap(this);
		}
		return this.heightMapCache;
	}

	@Override
	public AxisAlignedBB getEntityBoundingBox() {
		if (this.boundingBox == null) {
			this.boundingBox = this.getDefinition().getBounds(this, this.gauge).withHeightMap(this.getHeightMap());
		}
		return this.boundingBox;
	}
	
	@Override
	@SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox()
    {
		AxisAlignedBB bb = this.getEntityBoundingBox();
        return new AxisAlignedBB(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
    }
	
	/*
	 * Disable standard entity sync
	 */

	@Override
	@SideOnly(Side.CLIENT)
	public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
		// We need our own custom sync packets, see MRSSyncPacket
	}

	@Override
	public void setVelocity(double x, double y, double z) {
		// We need our own custom sync packets, see MRSSyncPacket
	}
	
	/*
	 * Speed Info
	 */

	public Speed getCurrentSpeed() {
		if (currentSpeed == null) {
			//Fallback
			// does not work for curves
			float speed = MathHelper.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
			if (Float.isNaN(speed)) {
				speed = 0;
			}
			currentSpeed = Speed.fromMinecraft(speed);
		}
		return currentSpeed;
	}
	public void setCurrentSpeed(Speed newSpeed) {
		this.currentSpeed = newSpeed;
	}
	
	public void handleTickPosPacket(List<TickPos> newPositions, double serverTPS) {
		this.tickSkew = serverTPS / 20;
		
		if (newPositions.size() != 0) {
			this.clearPositionCache();
			double delta = newPositions.get(0).tickID - this.tickPosID;
			if (Math.abs(delta) > 10) {
				this.tickPosID = newPositions.get(0).tickID;
			} else {
				tickSkew += Math.max(-5, Math.min(5, delta)) / 100;
			}
		}
		this.positions = newPositions;
	}
	
	public TickPos getTickPos(int tickID) {
		if (positions.size() == 0) {
			return null;
		}
		for (TickPos pos : positions) {
			if (pos.tickID == tickID) {
				return pos;
			}
		}
		
		return positions.get(positions.size()-1);
	}
	
	public TickPos getCurrentTickPosAndPrune() {
		if (positions.size() == 0) {
			return null;
		}
		if (positions.get(0).tickID != (int)this.tickPosID) {
			// Prune list
			while (positions.get(0).tickID < (int)this.tickPosID && positions.size() > 1) {
				positions.remove(0);
			}
		}
		return positions.get(0);
	}
	
	public int getRemainingPositions() {
		return positions.size();
	}
	
	private double skewScalar(double curr, double next) {
		if (world.isRemote) {
			return curr + (next - curr) * this.getTickSkew();
		}
		return next;
	}
	private float skewScalar(float curr, float next) {
		if (world.isRemote) {
			return curr + (next - curr) * this.getTickSkew();
		}
		return next;
	}
	
	private float fixAngleInterp(float curr, float next) {
		if (curr - next > 180) {
			curr -= 360;
    	}
    	if (next - curr > 180) {
    		curr += 360;
    	}
    	return curr;
	}
	
	@Override
	public void onUpdate() {
		super.onUpdate();
		

		if (!world.isRemote) {
			if (ConfigDebug.serverTickCompensation) {
				this.tickSkew = 20 / CommonProxy.getServerTPS(getEntityWorld(), 1);
			} else {
				this.tickSkew = 1;
			}
			
			if (this.ticksExisted % 10 == 0) {
				// Wipe this now and again to force a refresh
				// Could also be implemented as a wipe from the track rail base (might be more efficient?)
				lastRetarderPos = null;
			}
		}
		
		if (world.isRemote) {
			if (ConfigSound.soundEnabled) {
				if (this.wheel_sound == null) {
					wheel_sound = ImmersiveRailroading.proxy.newSound(this.getDefinition().wheel_sound, true, 40, gauge);
					this.sndRand = (float)Math.random()/10;
				}
				
				if (Math.abs(this.getCurrentSpeed().metric()) > 5) {
					if (!wheel_sound.isPlaying()) {
						wheel_sound.play(this.getPositionVector());
					}
					float adjust = (float) Math.abs(this.getCurrentSpeed().metric()) / 300;
					wheel_sound.setPitch(adjust + 0.7f + this.sndRand);
					wheel_sound.setVolume(adjust);
					
					wheel_sound.setPosition(getPositionVector());
					wheel_sound.setVelocity(getVelocity());
					wheel_sound.update();
				} else {
					if (wheel_sound.isPlaying()) {
						wheel_sound.stop();;
					}
				}
			}
		}
		
		this.tickPosID += this.getTickSkew();
		
		// Apply position tick
		TickPos currentPos = getCurrentTickPosAndPrune();
		if (currentPos == null) {
			// Not loaded yet or not moving
			return;
		}
		

	    this.prevPosX = this.posX;
	    this.prevPosY = this.posY;
	    this.prevPosZ = this.posZ;
	    this.lastTickPosX = this.posX;
	    this.lastTickPosY = this.posY;
	    this.lastTickPosZ = this.posZ;
	    this.prevRotationYaw = this.rotationYaw;
	    this.prevRotationPitch = this.rotationPitch;
		

	    this.posX = skewScalar(this.posX, currentPos.position.x);
	    this.posY = skewScalar(this.posY, currentPos.position.y);
	    this.posZ = skewScalar(this.posZ, currentPos.position.z);

	    if (world.isRemote) {
	    	this.prevRotationYaw = fixAngleInterp(this.prevRotationYaw, currentPos.rotationYaw);
	    	this.rotationYaw = fixAngleInterp(this.rotationYaw, currentPos.rotationYaw);
	    	this.frontYaw = fixAngleInterp(this.frontYaw == null ? this.rotationYaw : this.frontYaw, currentPos.frontYaw);
	    	this.rearYaw = fixAngleInterp(this.rearYaw == null ? this.rotationYaw : this.rearYaw, currentPos.rearYaw);
	    }
		    
	    this.rotationYaw = skewScalar(this.rotationYaw, currentPos.rotationYaw);
	    this.rotationPitch = skewScalar(this.rotationPitch, currentPos.rotationPitch);
	    this.frontYaw = skewScalar(this.frontYaw == null ? this.rotationYaw : this.frontYaw, currentPos.frontYaw);
	    this.rearYaw = skewScalar(this.rearYaw == null ? this.rotationYaw : this.rearYaw, currentPos.rearYaw);
	    
	    this.currentSpeed = currentPos.speed;
		distanceTraveled = skewScalar(distanceTraveled, distanceTraveled + (float)this.currentSpeed.minecraft());
		
	    this.motionX = this.posX - this.prevPosX;
	    this.motionY = this.posY - this.prevPosY;
	    this.motionZ = this.posZ - this.prevPosZ;

	    if (Math.abs(this.motionX) + Math.abs(this.motionY) + Math.abs(this.motionZ) > 0.001 ) {
	    	this.clearPositionCache();
	    }

	    if (this.getCurrentSpeed().metric() > 1) {
			List<Entity> entitiesWithin = world.getEntitiesWithinAABB(EntityLivingBase.class, this.getCollisionBoundingBox().offset(0, -0.5, 0));
			for (Entity entity : entitiesWithin) {
				if (entity instanceof EntityMoveableRollingStock) {
					// rolling stock collisions handled by looking at the front and
					// rear coupler offsets
					continue;
				} 
	
				if (entity.getRidingEntity() instanceof EntityMoveableRollingStock) {
					// Don't apply bb to passengers
					continue;
				}
				
				if (entity instanceof EntityPlayer) {
					if (entity.ticksExisted < 20 * 5) {
						// Give the player a chance to get out of the way
						continue;
					}
				}
	
				
				// Chunk.getEntitiesOfTypeWithinAABB() does a reverse aabb intersect
				// We need to do a forward lookup
				if (!this.getCollisionBoundingBox().intersects(entity.getEntityBoundingBox())) {
					// miss
					continue;
				}
	
				// Move entity

				entity.motionX = this.motionX * 2;
				entity.motionY = 0;
				entity.motionZ = this.motionZ * 2;
				// Force update
				entity.onUpdate();
	
				double speedDamage = this.getCurrentSpeed().metric() / ConfigDamage.entitySpeedDamage;
				if (speedDamage > 1) {
					entity.attackEntityFrom((new DamageSource("immersiverailroading:hitByTrain")).setDamageBypassesArmor(), (float) speedDamage);
				}
			}
	
			// Riding on top of cars
			AxisAlignedBB bb = this.getCollisionBoundingBox();
			bb = bb.offset(0, gauge.scale()*2, 0);
			List<Entity> entitiesAbove = world.getEntitiesWithinAABB(EntityLivingBase.class, bb);
			for (Entity entity : entitiesAbove) {
				if (entity instanceof EntityMoveableRollingStock) {
					continue;
				}
				if (entity.getRidingEntity() instanceof EntityMoveableRollingStock) {
					continue;
				}
	
				// Chunk.getEntitiesOfTypeWithinAABB() does a reverse aabb intersect
				// We need to do a forward lookup
				if (!bb.intersects(entity.getEntityBoundingBox())) {
					// miss
					continue;
				}
				
				//Vec3d pos = entity.getPositionVector();
				//pos = pos.addVector(this.motionX, this.motionY, this.motionZ);
				//entity.setPosition(pos.x, pos.y, pos.z);

				entity.motionX = this.motionX;
				entity.motionY = entity.motionY + this.motionY;
				entity.motionZ = this.motionZ;
			}
	    }
	    
	    if (this.ticksExisted % 5 == 0) {
			destructionQueue.entrySet().removeIf(blockPos -> !this.destructionTicks.contains(blockPos.getValue()));
			destructionTicks.removeIf(destroyTick -> destroyTick > this.tickPosID);
	    	breakBlocksInQueue();
	    }
	}

	protected void clearPositionCache() {
		this.boundingBox = null;
	}

	public TickPos moveRollingStock(double moveDistance, int lastTickID) {
		TickPos lastPos = this.getTickPos(lastTickID);
		return new MovementSimulator(world, lastPos, this.getDefinition().getBogeyFront(gauge), this.getDefinition().getBogeyRear(gauge), gauge.value()).nextPosition(moveDistance);
	}
	
	/*
	 * 
	 * Client side render guessing
	 */
	public class PosRot extends Vec3d {
		private float rotation;
		public PosRot(double xIn, double yIn, double zIn, float rotation) {
			super(xIn, yIn, zIn);
			this.rotation = rotation;
		}
		public PosRot(Vec3d nextFront, float yaw) {
			this(nextFront.x, nextFront.y, nextFront.z, yaw);
		}
		public float getRotation() {
			return rotation;
		}
	}

	
	public float getFrontYaw() {
		if (this.frontYaw != null) {
			return this.frontYaw;
		}
		return this.rotationYaw;
	}
	
	public float getRearYaw() {
		if (this.rearYaw != null) {
			return this.rearYaw;
		}
		return this.rotationYaw;
	}

	protected TickPos getCurrentTickPosOrFake() {
		return new TickPos(0, Speed.fromMetric(0), this.getPositionVector(), this.getFrontYaw(), this.getRearYaw(), this.rotationYaw, this.rotationPitch, false);
	}
	
	// Checks for collisions with blocks and evaluates hardness
	public float[] predictCollisionHardness(TickPos simPos) {
			Vec3d simOffset = simPos.position.subtract(new Vec3d(this.getPosition()));
			AxisAlignedBB bb = this.getCollisionBoundingBox().grow(-0.25 * gauge.scale(), 0, -0.25 * gauge.scale()).offset(simOffset);
			float[] blockCollisionHardness = {0f, 0f};
			Map<BlockPos, Integer> destructionQueueBuffer = new HashMap<BlockPos, Integer>();

			for (Vec3d pos : this.getDefinition().getBlocksInBounds(gauge)) {
				pos = VecUtil.rotateWrongYaw(pos, simPos.rotationYaw);
				pos = pos.add(simPos.position);
				BlockPos bp = new BlockPos(pos);

				if (!world.isBlockLoaded(bp)) {
					continue;
				}

				IBlockState state = world.getBlockState(bp);
				if (state.getBlock() != Blocks.AIR) {
					if (!BlockUtil.isIRRail(world, bp)) {
						AxisAlignedBB bbb = state.getCollisionBoundingBox(world, bp);
						if (bbb == null) {
							continue;
						}
						bbb = bbb.offset(bp);
						// The correct direction is the reverse..., does yaw change according to velocity? Please explain if possible. 
						int blockCollisionInFront = 0;
						double angleVelocityToBlock = VecUtil.toYaw(pos.subtract(simPos.position)) - simPos.rotationYaw + 180;
						angleVelocityToBlock = (angleVelocityToBlock + 180) % 360 - 180;

						if (Math.abs(angleVelocityToBlock) < 45) {
							blockCollisionInFront = 1;
						} else if (Math.abs(angleVelocityToBlock) > 135) {
							blockCollisionInFront = 0;
						} else {
							continue; // Only check blocks that our velocity brings us into
						}

						double collisionSpeed = Math.abs(simPos.speed.metric());
						float blockHardness = state.getBlockHardness(world, bp);

						// Final check to avoid doing intersect
						if (pos.lengthVector() > this.getDefinition().getLength(gauge) / 2) {
							if (!bb.intersects(bbb)) { // this is slow, do it as little as possible
								continue;
							}
						}
						if (!BlockUtil.isIRRail(world, bp.up()) && state.getBlockHardness(world, bp) >= 0) {
							//ImmersiveRailroading.info("Colliding block at %s degrees, speed is %f", angleVelocityToBlock, collisionSpeed);
							if (blockCollisionHardness[blockCollisionInFront] >= 0) blockCollisionHardness[blockCollisionInFront] += blockHardness;
							if (!destructionQueue.containsKey(bp)) destructionQueueBuffer.put(bp, simPos.tickID);
							
							if (Config.ConfigDamage.explosionsEnabled && collisionSpeed > 60) {
								if (!this.isDead) {
									this.onDeath(collisionSpeed > 80 ? StockDeathType.CATACYSM : StockDeathType.EXPLOSION);
								}
								world.removeEntity(this);
								float[] empty = {0f,0f};
								return empty;
							}
						} else if (state.getBlockHardness(world, bp) < 0) { // This should absolutely stop the entire consist.
							//ImmersiveRailroading.info("Unmovable block at %s degrees, velocity is %f", angleVelocityToBlock, simPos.speed.metric());
							blockCollisionHardness[blockCollisionInFront] = -1;
							return blockCollisionHardness;
						}
					} else {
						TileRailBase te = TileRailBase.get(world, bp);
						if (te != null) {
							te.cleanSnow();
							continue;
						}
					}
				}
			}
			if (simPos.speed.metric() != 0) destructionQueue.putAll(destructionQueueBuffer);
			return blockCollisionHardness;
	}
	
	/**
	 * 
	 */
	public Speed solidMovement(PhysicsAccummulator physics, TickPos simPos) {
		double collisionVelocity = physics.getVelocity().minecraft();
		int direction = collisionVelocity > 0 ? 1 : 0;
		double collisionForce = physics.blockCollisionForceNewtons[direction];
		ImmersiveRailroading.info("Solid velocity: %s", collisionVelocity);
		if (collisionVelocity == 0) return Speed.fromMinecraft(collisionVelocity);
		if (physics.blockCollisionForceNewtons[0] != 0.0 || physics.blockCollisionForceNewtons[1] != 0.0) {
			//ImmersiveRailroading.info("blockCollisionForceNewtons : %s,%s", physics.blockCollisionForceNewtons[0], physics.blockCollisionForceNewtons[1]);
		}
		
		if (!ConfigDamage.TrainsBreakBlocks || collisionForce < 0 || Math.abs(collisionVelocity) * physics.massToMoveKg < collisionForce) {
			//ImmersiveRailroading.info("Stopped velocity: 0\n");
			return Speed.ZERO;
		}
		
		if (!destructionTicks.contains(simPos.tickID) && collisionForce != 0) destructionTicks.add(simPos.tickID);
		double blockCollisionAccell = physics.blockCollisionForceNewtons[direction] / physics.massToMoveKg;
		if (collisionForce != 0) {
			collisionVelocity -= blockCollisionAccell > Math.abs(collisionVelocity) ? collisionVelocity : Math.copySign(blockCollisionAccell, collisionVelocity);
		}
		//ImmersiveRailroading.info("Final velocity: %s\n", collisionVelocity);
		return Speed.fromMinecraft(collisionVelocity);
	}
	
	public void breakBlocksInQueue() {
		Iterator<BlockPos> iter = destructionQueue.keySet().iterator();
		while (iter.hasNext()){
			BlockPos bp = iter.next();
			if (!world.isBlockLoaded(bp) || this.tickPosID < destructionQueue.get(bp)) continue;
			IBlockState state = world.getBlockState(bp);
			world.destroyBlock(bp, Config.ConfigDamage.dropSnowBalls || !(state.getBlock() == Blocks.SNOW || state.getBlock() == Blocks.SNOW_LAYER));
			iter.remove();
		}
	}
	
	public Vec3d predictFrontBogeyPosition(float offset) {
		return predictFrontBogeyPosition(getCurrentTickPosOrFake(), offset);
	}
	public Vec3d predictFrontBogeyPosition(TickPos pos, float offset) {
		MovementSimulator sim = new MovementSimulator(world, pos, this.getDefinition().getBogeyFront(gauge), this.getDefinition().getBogeyRear(gauge), gauge.value());
		Vec3d nextFront = sim.nextPosition(sim.frontBogeyPosition(), pos.rotationYaw, pos.frontYaw, offset);
		return new PosRot(pos.position.subtract(nextFront), VecUtil.toYaw(pos.position.subtract(nextFront)));
	}
	
	public PosRot predictRearBogeyPosition(float offset) {
		return predictRearBogeyPosition(getCurrentTickPosOrFake(), offset);
	}
	public PosRot predictRearBogeyPosition(TickPos pos, float offset) {
		MovementSimulator sim = new MovementSimulator(world, pos, this.getDefinition().getBogeyRear(gauge), this.getDefinition().getBogeyRear(gauge), gauge.value());
		Vec3d nextRear = sim.nextPosition(sim.rearBogeyPosition(), pos.rotationYaw, pos.rearYaw, offset);
		return new PosRot(pos.position.subtract(nextRear), VecUtil.toYaw(pos.position.subtract(nextRear)));
	}

	private BlockPos lastRetarderPos = null;
	private int lastRetarderValue = 0;
	public int getSpeedRetarderSlowdown(TickPos latest) {
		if (new BlockPos(latest.position).equals(lastRetarderPos)) {
			return lastRetarderValue;
		}
		
		int over = 0;
		int max = 0;
		for (Vec3d pos : this.getDefinition().getBlocksInBounds(gauge)) {
			if (pos.y != 0) {
				continue;
			}
			pos = VecUtil.rotateWrongYaw(pos, latest.rotationYaw);
			pos = pos.add(latest.position);
			BlockPos bp = new BlockPos(pos);
			
			if (!world.isBlockLoaded(bp)) {
				continue;
			}
			
			try {
				TileEntity potentialTE = world.getChunkFromBlockCoords(bp).getTileEntity(bp, EnumCreateEntityType.CHECK);
				if (potentialTE != null && potentialTE instanceof TileRailBase) {
					TileRailBase te = (TileRailBase)potentialTE;
					if (te.getAugment() == Augment.SPEED_RETARDER) {
						max = Math.max(max, RedstoneUtil.getPower(world, bp));
						over += 1;
					}
				}
			} catch (Exception ex) {
				// eat this exception
				// Faster than calling isOutsideBuildHeight
				ImmersiveRailroading.catching(ex);
			}
		}
		lastRetarderPos = new BlockPos(latest.position);
		lastRetarderValue = over * max; 
		return lastRetarderValue;
	}

	public float getTickSkew() {
		return (float) this.tickSkew;
	}

	public Vec3d getVelocity() {
		return new Vec3d(this.motionX, this.motionY, this.motionZ);
	}
	
	@Override
	public void setDead() {
		super.setDead();
		if (this.wheel_sound != null) {
			wheel_sound.stop();
		}
	}
}
