package cam72cam.immersiverailroading.entity;

import java.util.List;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.KeyTypes;
import cam72cam.immersiverailroading.library.RenderComponentType;
import cam72cam.immersiverailroading.model.RenderComponent;
import cam72cam.immersiverailroading.registry.LocomotiveDieselDefinition;
import cam72cam.immersiverailroading.sound.ISound;
import cam72cam.immersiverailroading.util.BurnUtil;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.immersiverailroading.util.VecUtil;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;

public class LocomotiveDiesel extends Locomotive {

	private ISound horn;
	private ISound idle;
	private ISound running;
	private float soundThrottle;
	private float internalBurn = 0;
	private int turnOnOffDelay = 0;
	
	private static DataParameter<Float> ENGINE_TEMPERATURE = EntityDataManager.createKey(LocomotiveDiesel.class, DataSerializers.FLOAT);
	private static DataParameter<Boolean> TURNED_ON = EntityDataManager.createKey(LocomotiveDiesel.class, DataSerializers.BOOLEAN);
	private static DataParameter<Boolean> ENGINE_OVERHEATED = EntityDataManager.createKey(LocomotiveDiesel.class, DataSerializers.BOOLEAN);

	public LocomotiveDiesel(World world) {
		this(world, null);
	}

	public LocomotiveDiesel(World world, String defID) {
		super(world, defID);
		this.getDataManager().register(ENGINE_TEMPERATURE, ambientTemperature());
		this.getDataManager().register(TURNED_ON, false);
		this.getDataManager().register(ENGINE_OVERHEATED, false);
	}
	
	public float getEngineTemperature() {
		return this.dataManager.get(ENGINE_TEMPERATURE);
	}
	
	private void setEngineTemperature(float temp) {
		this.dataManager.set(ENGINE_TEMPERATURE, temp);
	}
	
	public void setTurnedOn(boolean value) {
		this.dataManager.set(TURNED_ON, value);
	}
	
	public boolean isTurnedOn() {
		return this.dataManager.get(TURNED_ON);
	}
	
	public void setEngineOverheated(boolean value) {
		this.dataManager.set(ENGINE_OVERHEATED, value);
	}
	
	public boolean isEngineOverheated() {
		return this.dataManager.get(ENGINE_OVERHEATED) && Config.ConfigBalance.canDieselEnginesOverheat;
	}
	
	public boolean isRunning() {
		if (!Config.isFuelRequired(gauge)) {
			return isTurnedOn();
		}
		return isTurnedOn() && !isEngineOverheated() && this.getLiquidAmount() > 0;
	}
	
	@Override
	public LocomotiveDieselDefinition getDefinition() {
		return super.getDefinition(LocomotiveDieselDefinition.class);
	}
	
	@Override
	public GuiTypes guiType() {
		return GuiTypes.DIESEL_LOCOMOTIVE;
	}
	
	@Override
	protected void writeEntityToNBT(NBTTagCompound nbttagcompound) {
		super.writeEntityToNBT(nbttagcompound);
		nbttagcompound.setFloat("engine_temperature", getEngineTemperature());
		nbttagcompound.setBoolean("turned_on", isTurnedOn());
		nbttagcompound.setBoolean("engine_overheated", isEngineOverheated());
	}
	
	@Override
	protected void readEntityFromNBT(NBTTagCompound nbttagcompound) {
		setEngineTemperature(nbttagcompound.getFloat("engine_temperature"));
		setTurnedOn(nbttagcompound.getBoolean("turned_on"));
		setEngineOverheated(nbttagcompound.getBoolean("engine_overheated"));
		super.readEntityFromNBT(nbttagcompound);
	}
	
	/*
	 * Sets the throttle or brake on all connected diesel locomotives if the throttle or brake has been changed
	 */
	@Override
	public void handleKeyPress(Entity source, KeyTypes key, boolean sprinting) {
		switch(key) {
			case START_STOP_ENGINE:
				if (turnOnOffDelay == 0) {
					turnOnOffDelay = 10;
					setTurnedOn(!isTurnedOn());
				}
				break;
			default:
				super.handleKeyPress(source, key, sprinting);
		}
	}
	
	private void setThrottleMap(EntityRollingStock stock, boolean direction) {
		if (stock instanceof LocomotiveDiesel && ((LocomotiveDiesel)stock).getDefinition().muliUnitCapable) {
			((LocomotiveDiesel) stock).realSetThrottle(this.getThrottle() * (direction ? 1 : -1));
			((LocomotiveDiesel) stock).realAirBrake(this.getAirBrake());
		}
	}
	
	private void realSetThrottle(float newThrottle) {
		if (Config.isFuelRequired(gauge)) {
			newThrottle = Math.copySign(Math.min(Math.abs(newThrottle), this.getEngineTemperature()/100), newThrottle);
		}
		super.setThrottle(newThrottle);
	}
	private void realAirBrake(float newAirBrake) {
		super.setAirBrake(newAirBrake);;
	}
	
	@Override
	public void setThrottle(float newThrottle) {
		realSetThrottle(newThrottle);
		if (this.getDefinition().muliUnitCapable) {
			this.mapTrain(this, true, false, this::setThrottleMap);
		}
	}
	
	@Override
	public void setAirBrake(float newAirBrake) {
		realAirBrake(newAirBrake);
		this.mapTrain(this, true, false, this::setThrottleMap);
	}
	
	@Override
	protected int getAvailableHP() {
		if (isRunning() && (getEngineTemperature() > 75 || !Config.isFuelRequired(gauge))) {
			return this.getDefinition().getHorsePower(gauge);
		}
		return 0;
	}

	@Override
	public void onUpdate() {
		super.onUpdate();
		
		if (world.isRemote) {
			if (ConfigSound.soundEnabled) {
				if (this.horn == null) {
					this.horn = ImmersiveRailroading.proxy.newSound(this.getDefinition().horn, false, 100, this.soundGauge());
					this.idle = ImmersiveRailroading.proxy.newSound(this.getDefinition().idle, true, 80, this.soundGauge());
					this.running = this.getDefinition().running != null ? ImmersiveRailroading.proxy.newSound(this.getDefinition().running, true, 80, this.soundGauge()) : null;
				}
				
				if (isRunning() && (this.getCurrentSpeed().metric() == 0 || running == null)) {
					if (!idle.isPlaying()) {
						this.idle.play(getPositionVector());
					}
				} else {
					if (idle.isPlaying()) {
						idle.stop();
					}
				}
				
				if (running != null) {
					if (isRunning() && this.getCurrentSpeed().metric() != 0) {
						if (!running.isPlaying()) {
							this.running.play(getPositionVector());
						}
					} else {
						if (running.isPlaying()) {
							running.stop();
						}
					}
				}
				
				if (this.getDataManager().get(HORN) != 0 && !horn.isPlaying() && isRunning()) {
					horn.play(getPositionVector());
				}
				
				float absThrottle = Math.abs(this.getThrottle());
				if (this.soundThrottle > absThrottle) {
					this.soundThrottle -= Math.min(0.01f, this.soundThrottle - absThrottle); 
				} else if (this.soundThrottle < absThrottle) {
					this.soundThrottle += Math.min(0.01f, absThrottle - this.soundThrottle);
				}
	
				if (horn.isPlaying()) {
					horn.setPosition(getPositionVector());
					horn.setVelocity(getVelocity());
					horn.update();
				}
				
				if (running != null) {
					if (running.isPlaying()) {
						running.setPitch(0.7f+this.soundThrottle/4);
						running.setVolume(Math.max(0.1f, this.soundThrottle));
						running.setPosition(getPositionVector());
						running.setVelocity(getVelocity());
						running.update();
					}
				} else {
					if (idle.isPlaying()) {
						idle.setPitch(0.7f+this.soundThrottle/4);
						idle.setVolume(Math.max(0.1f, this.soundThrottle));
						idle.setPosition(getPositionVector());
						idle.setVelocity(getVelocity());
						idle.update();
					}
				}
			}
			
			
			if (!ConfigGraphics.particlesEnabled) {
				return;
			}
			
			Vec3d fakeMotion = new Vec3d(this.motionX, this.motionY, this.motionZ);//VecUtil.fromYaw(this.getCurrentSpeed().minecraft(), this.rotationYaw);
			
			List<RenderComponent> exhausts = this.getDefinition().getComponents(RenderComponentType.DIESEL_EXHAUST_X, gauge);
			float throttle = Math.abs(this.getThrottle()) + 0.05f;
			if (exhausts != null && isRunning()) {
				for (RenderComponent exhaust : exhausts) {
					Vec3d particlePos = this.getPositionVector().add(VecUtil.rotateYaw(exhaust.center(), this.rotationYaw + 180)).addVector(0, 0.35 * gauge.scale(), 0);
					
					double smokeMod = (1 + Math.min(1, Math.max(0.2, Math.abs(this.getCurrentSpeed().minecraft())*2)))/2;
					
					EntitySmokeParticle sp = new EntitySmokeParticle(world, (int) (40 * (1+throttle) * smokeMod), throttle, throttle, exhaust.width());
					
					particlePos = particlePos.subtract(fakeMotion);
					
					sp.setPosition(particlePos.x, particlePos.y, particlePos.z);
					sp.setVelocity(fakeMotion.x, fakeMotion.y + 0.4 * gauge.scale(), fakeMotion.z);
					world.spawnEntity(sp);
				}
			}
			return;
		}
		
		float engineTemperature = getEngineTemperature();
		float heatUpSpeed = 0.0029167f * Config.ConfigBalance.dieselLocoHeatTimeScale / 1.7f;
		float ambientDelta = engineTemperature - ambientTemperature();
		float coolDownSpeed = heatUpSpeed * Math.copySign((float)Math.pow(ambientDelta / 130, 2), ambientDelta);
		
		engineTemperature -= coolDownSpeed;
		
		if (this.getLiquidAmount() > 0 && isRunning()) {
			float consumption = Math.abs(getThrottle()) + 0.05f;
			float burnTime = BurnUtil.getBurnTime(this.getLiquid());
			if (burnTime == 0) {
				burnTime = 200; //Default to 200 for unregistered liquids
			}
			burnTime *= getDefinition().getFuelEfficiency()/100f;
			burnTime *= (Config.ConfigBalance.locoDieselFuelEfficiency / 100f);
			
			while (internalBurn < 0 && this.getLiquidAmount() > 0) {
				internalBurn += burnTime;
				theTank.drain(1, true);
			}
			
			consumption *= 100;
			consumption *= gauge.scale();
			
			internalBurn -= consumption;
			
			engineTemperature += heatUpSpeed * (Math.abs(getThrottle()) + 0.2f);
			
			if (engineTemperature > 150) {
				engineTemperature = 150;
				setEngineOverheated(true);
			}
		}
		
		if (engineTemperature < 100 && isEngineOverheated()) {
			setEngineOverheated(false);
		}
		
		if (turnOnOffDelay > 0) {
			turnOnOffDelay -= 1;
		}
		
		setEngineTemperature(engineTemperature);
	}
	
	@Override
	public void setDead() {
		super.setDead();
		
		if (idle != null) {
			idle.stop();
		}
		if (running != null) {
			running.stop();
		}
		if (horn != null) {
			horn.stop();
		}
	}

	@Override
	public List<Fluid> getFluidFilter() {
		return BurnUtil.burnableFluids();
	}

	@Override
	public FluidQuantity getTankCapacity() {
		return this.getDefinition().getFuelCapacity(gauge);
	}
	
	@Override
	public void onDissassemble() {
		super.onDissassemble();
		setEngineTemperature(ambientTemperature());
		setEngineOverheated(false);
		setTurnedOn(false);
	}
}