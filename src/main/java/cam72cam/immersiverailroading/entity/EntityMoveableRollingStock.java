package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.Config.ConfigDebug;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.Augment;
import cam72cam.immersiverailroading.physics.MovementSimulator;
import cam72cam.immersiverailroading.physics.TickPos;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.BlockUtil;
import cam72cam.immersiverailroading.util.RealBB;
import cam72cam.immersiverailroading.util.Speed;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.custom.ICollision;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.sound.ISound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class EntityMoveableRollingStock extends EntityRidableRollingStock implements ICollision {

    public static final String DAMAGE_SOURCE_HIT = "immersiverailroading:hitByTrain";

    @TagField("frontYaw")
    private Float frontYaw;
    @TagField("rearYaw")
    private Float rearYaw;
    @TagField("distanceTraveled")
    public float distanceTraveled = 0;
    @TagField("tickPosID")
    public double tickPosID = 0;
    private Speed currentSpeed;
    @TagField(value = "positions", mapper = TickPos.ListTagMapper.class)
    public List<TickPos> positions = new ArrayList<>();
    private RealBB boundingBox;
    private float[][] heightMapCache;
    @TagField("tickSkew")
    private double tickSkew = 1;

    private float sndRand;
    private boolean ignoreSlope;

    private ISound wheel_sound;
    private ISound clackFront;
    private ISound clackRear;
    private Vec3i clackFrontPos;
    private Vec3i clackRearPos;

    @Override
    public void load(TagCompound data) {
        super.load(data);

        if (positions.isEmpty()) {
            this.tickPosID = 0;
            positions.add(getCurrentTickPosOrFake());
        }

        if (frontYaw == null) {
            frontYaw = getRotationYaw();
        }
        if (rearYaw == null) {
            rearYaw = getRotationYaw();
        }
    }

    public void initPositions(TickPos tp) {
        this.positions = Arrays.asList(tp);
    }

    /*
     * Entity Overrides for BB
     */

    public void clearHeightMap() {
        this.heightMapCache = null;
        this.boundingBox = null;
    }

    private float[][] getHeightMap() {
        if (this.heightMapCache == null) {
            this.heightMapCache = this.getDefinition().createHeightMap(this);
        }
        return this.heightMapCache;
    }

    @Override
    public RealBB getCollision() {
        if (this.boundingBox == null) {
            this.boundingBox = this.getDefinition().getBounds(this, this.gauge)
                    .withHeightMap(this.getHeightMap())
                    .contract(new Vec3d(0, 0.5 * this.gauge.scale(), 0)).offset(new Vec3d(0, 0.5 * this.gauge.scale(), 0));
        }
        return this.boundingBox;
    }

    /*
     * Speed Info
     */

    public Speed getCurrentSpeed() {
        if (currentSpeed == null) {
            //Fallback
            // does not work for curves
            Vec3d motion = this.getVelocity();
            float speed = (float) Math.sqrt(motion.x * motion.x + motion.y * motion.y + motion.z * motion.z);
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

        return positions.get(positions.size() - 1);
    }

    public TickPos getCurrentTickPosAndPrune() {
        if (positions.size() == 0) {
            return null;
        }
        if (positions.get(0).tickID != (int) this.tickPosID) {
            // Prune list
            while (positions.get(0).tickID < (int) this.tickPosID && positions.size() > 1) {
                positions.remove(0);
            }
        }
        return positions.get(0);
    }

    public int getRemainingPositions() {
        return positions.size();
    }

    private double skewScalar(double curr, double next) {
        if (getWorld().isClient) {
            return curr + (next - curr) * this.getTickSkew();
        }
        return next;
    }

    private float skewScalar(float curr, float next) {
        if (getWorld().isClient) {
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
    public void onTick() {
        super.onTick();


        if (getWorld().isServer) {
            if (ConfigDebug.serverTickCompensation) {
                this.tickSkew = 20 / getWorld().getTPS(1);
            } else {
                this.tickSkew = 1;
            }

            if (this.getTickCount() % 10 == 0) {
                // Wipe this now and again to force a refresh
                // Could also be implemented as a wipe from the track rail base (might be more efficient?)
                lastRetarderPos = null;
            }
        }

        if (getWorld().isClient) {
            getDefinition().getModel().onClientTick(this);


            if (ConfigSound.soundEnabled) {
                if (this.wheel_sound == null) {
                    wheel_sound = ImmersiveRailroading.newSound(this.getDefinition().wheel_sound, true, 40, gauge);
                    this.sndRand = (float) Math.random() / 10;
                }
                if (this.clackFront == null) {
                    clackFront = ImmersiveRailroading.newSound(this.getDefinition().clackFront, false, 30, gauge);
                }
                if (this.clackRear == null) {
                    clackRear = ImmersiveRailroading.newSound(this.getDefinition().clackRear, false, 30, gauge);
                }
                float adjust = (float) Math.abs(this.getCurrentSpeed().metric()) / 300;
                float pitch = adjust + 0.7f;
                if (getDefinition().shouldScalePitch()) {
                    pitch = (float) (pitch/ gauge.scale());
                }
                float volume = 0.01f + adjust;

                if (Math.abs(this.getCurrentSpeed().metric()) > 5 && MinecraftClient.getPlayer().getPosition().distanceTo(getPosition()) < 40) {
                    if (!wheel_sound.isPlaying()) {
                        wheel_sound.play(getPosition());
                    }
                    wheel_sound.setPitch(pitch + this.sndRand);
                    wheel_sound.setVolume(volume);

                    wheel_sound.setPosition(getPosition());
                    wheel_sound.setVelocity(getVelocity());
                    wheel_sound.update();
                } else {
                    if (wheel_sound.isPlaying()) {
                        wheel_sound.stop();
                    }
                }

                volume = Math.min(1, volume * 2);

                Vec3i posFront = new Vec3i(VecUtil.fromWrongYawPitch(getDefinition().getBogeyFront(gauge), getRotationYaw(), getRotationPitch()).add(getPosition()));
                if (BlockUtil.isIRRail(getWorld(), posFront)) {
                    TileRailBase rb = getWorld().getBlockEntity(posFront, TileRailBase.class);
                    rb = rb != null ? rb.getParentTile() : null;
                    if (rb != null && !rb.getPos().equals(clackFrontPos)) {
                        clackFront.setPitch(pitch);
                        clackFront.setVolume(volume);
                        clackFront.play(new Vec3d(posFront));
                        clackFrontPos = rb.getPos();
                    }
                }
                Vec3i posRear = new Vec3i(VecUtil.fromWrongYawPitch(getDefinition().getBogeyRear(gauge), getRotationYaw(), getRotationPitch()).add(getPosition()));
                if (BlockUtil.isIRRail(getWorld(), posRear)) {
                    TileRailBase rb = getWorld().getBlockEntity(posRear, TileRailBase.class);
                    rb = rb != null ? rb.getParentTile() : null;
                    if (rb != null && !rb.getPos().equals(clackRearPos)) {
                        clackRear.setPitch(pitch);
                        clackRear.setVolume(volume);
                        clackRear.play(new Vec3d(posRear));
                        clackRearPos = rb.getPos();
                    }
                }
            }
        }

        this.tickPosID += this.getTickSkew();

        // Apply position onTick
        TickPos currentPos = getCurrentTickPosAndPrune();
        if (currentPos == null) {
            // Not loaded yet or not moving
            return;
        }

        Vec3d prevPos = this.getPosition();
        double prevPosX = prevPos.x;
        double prevPosY = prevPos.y;
        double prevPosZ = prevPos.z;
        float prevRotationYaw = this.getRotationYaw();
        float prevRotationPitch = this.getRotationPitch();


        if (getWorld().isClient) {
            //TODO this.prevRotationYaw = fixAngleInterp(this.prevRotationYaw, currentPos.rotationYaw);
            //TODO this.rotationYaw = fixAngleInterp(this.rotationYaw, currentPos.rotationYaw);
            this.frontYaw = fixAngleInterp(this.frontYaw == null ? prevRotationYaw : this.frontYaw, currentPos.frontYaw);
            this.rearYaw = fixAngleInterp(this.rearYaw == null ? prevRotationYaw : this.rearYaw, currentPos.rearYaw);
            prevRotationYaw = fixAngleInterp(prevRotationYaw, currentPos.rotationYaw);
        }

        this.setRotationYaw(skewScalar(prevRotationYaw, currentPos.rotationYaw));
        this.setRotationPitch(skewScalar(prevRotationPitch, currentPos.rotationPitch));
        this.frontYaw = skewScalar(this.frontYaw == null ? prevRotationYaw : this.frontYaw, currentPos.frontYaw);
        this.rearYaw = skewScalar(this.rearYaw == null ? prevRotationYaw : this.rearYaw, currentPos.rearYaw);

        this.currentSpeed = currentPos.speed;
        distanceTraveled = skewScalar(distanceTraveled, distanceTraveled + (float) this.currentSpeed.minecraft());

        this.setPosition(new Vec3d(
                        skewScalar(prevPosX, currentPos.position.x),
                        skewScalar(prevPosY, currentPos.position.y),
                        skewScalar(prevPosZ, currentPos.position.z)
                )
        );
        this.setVelocity(getPosition().subtract(prevPosX, prevPosY, prevPosZ));

        if (this.getVelocity().length() > 0.001) {
            this.clearPositionCache();
        }

        if (this.getCurrentSpeed().metric() > 1) {
			List<Entity> entitiesWithin = getWorld().getEntities((Entity entity) -> entity.isLiving() || entity.isPlayer() && this.getCollision().intersects(entity.getBounds()), Entity.class);
			for (Entity entity : entitiesWithin) {
				if (entity instanceof EntityMoveableRollingStock) {
					// rolling stock collisions handled by looking at the front and
					// rear coupler offsets
					continue;
				} 
	
				if (entity.getRiding() instanceof EntityMoveableRollingStock) {
					// Don't apply bb to passengers
					continue;
				}
				
				if (entity.isPlayer()) {
					if (entity.getTickCount() < 20 * 5) {
						// Give the internal a chance to getContents out of the way
						continue;
					}
				}
	
				
				// Chunk.getEntitiesOfTypeWithinAABB() does a reverse aabb intersect
				// We need to do a forward lookup
				if (!this.getCollision().intersects(entity.getBounds())) {
					// miss
					continue;
				}
	
				// Move entity

				entity.setVelocity(this.getVelocity().scale(2));
				// Force update
				//TODO entity.onUpdate();
	
				double speedDamage = this.getCurrentSpeed().metric() / Config.ConfigDamage.entitySpeedDamage;
				if (speedDamage > 1) {
				    entity.directDamage(DAMAGE_SOURCE_HIT, speedDamage);
				}
			}
	
			// Riding on top of cars
			final RealBB bb = this.getCollision().offset(new Vec3d(0, gauge.scale()*2, 0));
            List<Entity> entitiesAbove = getWorld().getEntities((Entity entity) -> entity.isLiving() || entity.isPlayer() && bb.intersects(entity.getBounds()), Entity.class);
			for (Entity entity : entitiesAbove) {
				if (entity instanceof EntityMoveableRollingStock) {
					continue;
				}
				if (entity.getRiding() instanceof EntityMoveableRollingStock) {
					continue;
				}
	
				// Chunk.getEntitiesOfTypeWithinAABB() does a reverse aabb intersect
				// We need to do a forward lookup
				if (!bb.intersects(entity.getBounds())) {
					// miss
					continue;
				}
				
				//Vec3d pos = entity.getPositionVector();
				//pos = pos.addVector(this.motionX, this.motionY, this.motionZ);
				//entity.setPosition(pos.x, pos.y, pos.z);

				entity.setVelocity(this.getVelocity().add(0, entity.getVelocity().y, 0));
			}
	    }
		if (getWorld().isServer && this.getTickCount() % 5 == 0 && Config.ConfigDamage.TrainsBreakBlocks && Math.abs(this.getCurrentSpeed().metric()) > 0.5) {
            RealBB bb = this.getCollision().grow(new Vec3d(-0.25 * gauge.scale(), 0, -0.25 * gauge.scale()));

            for (Vec3i bp : getWorld().blocksInBounds(bb)) {
                if (!BlockUtil.isIRRail(getWorld(), bp)) {
                    if (getWorld().canEntityCollideWith(bp, DAMAGE_SOURCE_HIT)) {
                        if (!BlockUtil.isIRRail(getWorld(), bp.up())) {
                            getWorld().breakBlock(bp, Config.ConfigDamage.dropSnowBalls || !(getWorld().isSnow(bp)));
                        }
                    }
                } else {
                    TileRailBase te = getWorld().getBlockEntity(bp, TileRailBase.class);
                    if (te != null) {
                        te.cleanSnow();
                    }
                }
            }
        }
    }

    protected void clearPositionCache() {
        this.boundingBox = null;
    }

    public TickPos moveRollingStock(double moveDistance, int lastTickID) {
        TickPos lastPos = this.getTickPos(lastTickID);
        if (moveDistance > MovementSimulator.MAX_MOVE_DISTANCE) { // over 1000 mph
            ImmersiveRailroading.warn("Trying to move %s at over 1000 mph, cam72cam's physics really sucks", getUUID());
        }
        return new MovementSimulator(getWorld(), lastPos, this.getDefinition().getBogeyFront(gauge), this.getDefinition().getBogeyRear(gauge), gauge.value()).nextPosition(moveDistance);
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
        return this.getRotationYaw();
    }

    public float getRearYaw() {
        if (this.rearYaw != null) {
            return this.rearYaw;
        }
        return this.getRotationYaw();
    }

    public TickPos getCurrentTickPosOrFake() {
        return new TickPos(0, Speed.fromMetric(0), getPosition(), this.getFrontYaw(), this.getRearYaw(), this.getRotationYaw(), this.getRotationPitch(), false);
    }

    public Vec3d predictFrontBogeyPosition(float offset) {
        return predictFrontBogeyPosition(getCurrentTickPosOrFake(), offset);
    }

    public Vec3d predictFrontBogeyPosition(TickPos pos, float offset) {
        MovementSimulator sim = new MovementSimulator(getWorld(), pos, this.getDefinition().getBogeyFront(gauge), this.getDefinition().getBogeyRear(gauge), gauge.value());
        Vec3d nextFront = sim.nextPosition(sim.frontBogeyPosition(), pos.rotationYaw, pos.frontYaw, offset);
        return new PosRot(pos.position.subtract(nextFront), VecUtil.toYaw(pos.position.subtract(nextFront)));
    }

    public Vec3d predictRearBogeyPosition(float offset) {
        return predictRearBogeyPosition(getCurrentTickPosOrFake(), offset);
    }

    public Vec3d predictRearBogeyPosition(TickPos pos, float offset) {
        MovementSimulator sim = new MovementSimulator(getWorld(), pos, this.getDefinition().getBogeyRear(gauge), this.getDefinition().getBogeyRear(gauge), gauge.value());
        Vec3d nextRear = sim.nextPosition(sim.rearBogeyPosition(), pos.rotationYaw, pos.rearYaw, offset);
        return new PosRot(pos.position.subtract(nextRear), VecUtil.toYaw(pos.position.subtract(nextRear)));
    }

    private Vec3i lastRetarderPos = null;
    private int lastRetarderValue = 0;

    public int getSpeedRetarderSlowdown(TickPos latest) {
        if (new Vec3i(latest.position).equals(lastRetarderPos)) {
            return lastRetarderValue;
        }

        int over = 0;
        int max = 0;
        for (Vec3i bp : getWorld().blocksInBounds(this.getCollision().offset(new Vec3d(0, gauge.scale(), 0)))) {
            TileRailBase te = getWorld().getBlockEntity(bp, TileRailBase.class);
            if (te != null) {
                if (te.getAugment() == Augment.SPEED_RETARDER) {
                    max = Math.max(max, getWorld().getRedstone(bp));
                    over += 1;
                }
            }
        }
        lastRetarderPos = new Vec3i(latest.position);
        lastRetarderValue = over * max;
        return lastRetarderValue;
    }

    public float getTickSkew() {
        return (float) this.tickSkew;
    }

    @Override
    public void onRemoved() {
        super.onRemoved();

        if (getWorld().isClient) {
            this.getDefinition().getModel().onClientRemoved(this);
        }

        if (this.wheel_sound != null) {
            wheel_sound.stop();
        }
        if (this.clackFront != null) {
            clackFront.stop();
        }
    }

    @Override
    protected void createExplosion(Vec3d pos, float size, boolean damageTerrain) {
        super.createExplosion(pos, size, damageTerrain);
    }

    public void setIgnoreSlope(boolean ignoreSlopeIn) {
        ignoreSlope = ignoreSlopeIn;
    }

    public boolean ignoreSlope() {
        return ignoreSlope;
    }
}
