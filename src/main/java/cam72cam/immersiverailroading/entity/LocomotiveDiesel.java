package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.KeyTypes;
import cam72cam.immersiverailroading.registry.LocomotiveDieselDefinition;
import cam72cam.immersiverailroading.util.BurnUtil;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.fluid.FluidStack;
import cam72cam.mod.gui.GuiRegistry;
import cam72cam.mod.serialization.TagField;

import java.util.List;

public class LocomotiveDiesel extends Locomotive {

	private float soundThrottle;
	private float internalBurn = 0;
	private int turnOnOffDelay = 0;

	@TagSync
	@TagField("ENGINE_TEMPERATURE")
	private float engineTemperature;

	@TagSync
	@TagField("TURNED_ON")
	private boolean turnedOn = false;

	@TagSync
	@TagField("ENGINE_OVERHEATED")
	private boolean engineOverheated = false;

	public LocomotiveDiesel() {
		engineTemperature = ambientTemperature();
	}

	@Override
	public int getInventoryWidth() {
		return 2;
	}

	public float getEngineTemperature() {
		return engineTemperature;
	}
	
	private void setEngineTemperature(float temp) {
		engineTemperature = temp;
	}
	
	public void setTurnedOn(boolean value) {
		turnedOn = value;
	}
	
	public boolean isTurnedOn() {
		return turnedOn;
	}
	
	public void setEngineOverheated(boolean value) {
		engineOverheated = value;
	}
	
	public boolean isEngineOverheated() {
		return engineOverheated && Config.ConfigBalance.canDieselEnginesOverheat;
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
	public GuiRegistry.EntityGUI guiType() {
		return GuiTypes.DIESEL_LOCOMOTIVE;
	}

	/*
	 * Sets the throttle or brake on all connected diesel locomotives if the throttle or brake has been changed
	 */
	@Override
	public void handleKeyPress(Player source, KeyTypes key) {
		switch(key) {
			case START_STOP_ENGINE:
				if (turnOnOffDelay == 0) {
					turnOnOffDelay = 10;
					setTurnedOn(!isTurnedOn());
				}
				break;
			default:
				super.handleKeyPress(source, key);
		}
	}

	@Override
	protected int getAvailableHP() {
		if (isRunning() && (getEngineTemperature() > 75 || !Config.isFuelRequired(gauge))) {
			return this.getDefinition().getHorsePower(gauge);
		}
		return 0;
	}



	@Override
	public void onTick() {
		super.onTick();
		
		if (getWorld().isClient) {
			float absThrottle = Math.abs(this.getThrottle());
			if (this.soundThrottle > absThrottle) {
				this.soundThrottle -= Math.min(0.01f, this.soundThrottle - absThrottle);
			} else if (this.soundThrottle < absThrottle) {
				this.soundThrottle += Math.min(0.01f, absThrottle - this.soundThrottle);
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
				theTank.drain(new FluidStack(theTank.getContents().getFluid(), 1), false);
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

	public float getSoundThrottle() {
		return soundThrottle;
	}
}