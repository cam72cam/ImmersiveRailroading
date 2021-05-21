package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.library.ChatText;
import cam72cam.immersiverailroading.library.KeyTypes;
import cam72cam.immersiverailroading.registry.ControllableStockDefinition;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.entity.sync.TagSync;
import cam72cam.mod.gui.GuiRegistry;
import cam72cam.mod.item.ClickResult;
import cam72cam.mod.serialization.StrictTagMapper;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.world.World;

import java.util.UUID;

public abstract class ControllableStock extends FreightTank {
	private static final float throttleNotch = 0.04f;
	private static final float airBrakeNotch = 0.04f;

	@TagField("deadMansSwitch")
	private boolean deadMansSwitch;
	private int deadManChangeTimeout;

	@TagSync
	@TagField("THROTTLE")
	private float throttle = 0;

	@TagSync
	@TagField("AIR_BRAKE")
	private float airBrake = 0;

	@TagSync
	@TagField("HORN")
	protected int hornTime = 0;

	@TagSync
	@TagField(value = "HORN_PLAYER", mapper = StrictTagMapper.class)
	protected UUID hornPlayer = null;

	@TagSync
	@TagField("BELL")
	private int bellTime = 0;

	private int bellKeyTimeout;

	/*
	 * 
	 * Stock Definitions
	 * 
	 */
	
	@Override
	public ControllableStockDefinition getDefinition() {
		return super.getDefinition(ControllableStockDefinition.class);
	}

	/*
	 * 
	 * EntityRollingStock Overrides
	 */

	@Override
	public GuiRegistry.EntityGUI guiType() {
		return null;
	}

	@Override
	public void handleKeyPress(Player source, KeyTypes key) {
		switch(key) {
		case HORN:
			setHorn(10, source.getUUID());
			break;
        case BELL:
            if (this.getDefinition().toggleBell) {
            	if (bellKeyTimeout == 0) {
					bellTime = bellTime != 0 ? 0 : 10;
					bellKeyTimeout = 10;
				}
            } else {
                setBell(10);
            }
            break;
		case THROTTLE_UP:
			if (getThrottle() < 1) {
				setThrottle(getThrottle() + throttleNotch);
			}
			break;
		case THROTTLE_ZERO:
			setThrottle(0f);
			break;
		case THROTTLE_DOWN:
			if (getThrottle() > -1) {
				setThrottle(getThrottle() - throttleNotch);
			}
			break;
		case AIR_BRAKE_UP:
			if (getAirBrake() < 1) {
				setAirBrake(getAirBrake() + airBrakeNotch);
			}
			break;
		case AIR_BRAKE_ZERO:
			setAirBrake(0f);
			break;
		case AIR_BRAKE_DOWN:
			if (getAirBrake() > 0) {
				setAirBrake(getAirBrake() - airBrakeNotch);
			}
			break;
		case DEAD_MANS_SWITCH:
			if (deadManChangeTimeout == 0) { 
				deadMansSwitch = !deadMansSwitch;
				if (deadMansSwitch) {
					source.sendMessage(ChatText.DEADMANS_SWITCH_ENABLED.getMessage());
				} else {
					source.sendMessage(ChatText.DEADMANS_SWITCH_DISABLED.getMessage());
				}
				this.deadManChangeTimeout = 5;
			}
			break;
			default:
				super.handleKeyPress(source, key);
		}
	}

    public ClickResult onClick(Player player, Player.Hand hand) {
		return super.onClick(player, hand);
	}
	
	@Override
	public void onTick() {
		super.onTick();
		
		if (getWorld().isServer) {
			if (deadManChangeTimeout > 0) {
				deadManChangeTimeout -= 1;
			}
			if (bellKeyTimeout > 0) {
				bellKeyTimeout--;
			}
			
			if (deadMansSwitch && !this.getCurrentSpeed().isZero()) {
				boolean hasDriver = false;
				for (Entity entity : this.getPassengers()) {
					if (entity.isPlayer()) {
						hasDriver = true;
						break;
					}
				}
				if (!hasDriver) {
					this.setThrottle(0);
					this.setAirBrake(1);
				}
			}
			if (hornTime > 0) {
				hornTime--;
			} else if (hornPlayer != null) {
				hornPlayer = null;
			}
			if (bellTime > 0 && !this.getDefinition().toggleBell) {
				bellTime--;
			}
		}

		this.distanceTraveled += simulateWheelSlip();
	}

	// Throttle logic for MU'ed stock
	protected void setThrottleMap(EntityRollingStock stock, boolean direction) {
		if (stock instanceof ControllableStock && ((ControllableStock)stock).getDefinition().multiUnitCapable) {
			((ControllableStock) stock).realSetThrottle(this.getThrottle() * (direction ? 1 : -1));
			((ControllableStock) stock).realAirBrake(this.getAirBrake());
		}
	}


	protected double simulateWheelSlip() {
		return 0;
	}

	/*
	 * 
	 * Misc Helper functions
	 */


	/*
	 * Sets the throttle or brake on all connected diesel locomotives if the throttle or brake has been changed
	 */
	protected void realSetThrottle(float newThrottle) {
		if (this.getThrottle() != newThrottle) {
			throttle = newThrottle;
			triggerResimulate();
		}
	}

	protected void realAirBrake(float newAirBrake) {
		if (this.getAirBrake() != newAirBrake) {
			airBrake = newAirBrake;
			triggerResimulate();
		}
	}

	public void setThrottle(float newThrottle) {
		realSetThrottle(newThrottle);
		if (this.getDefinition().multiUnitCapable) {
			this.mapTrain(this, true, false, this::setThrottleMap);
		}
	}

	public void setAirBrake(float newAirBrake) {
		realAirBrake(newAirBrake);
		this.mapTrain(this, true, false, this::setThrottleMap);
	}

	public void setHorn(int val, UUID uuid) {
		if (hornPlayer == null && uuid != null) {
			hornPlayer = uuid;
		}
		if (hornPlayer == null || hornPlayer.equals(uuid)) {
			hornTime = val;
		}
	}

	public int getHornTime() {
		return hornTime;
	}

	public Entity getHornPlayer() {
		for (Entity pass : getPassengers()) {
			if (pass.getUUID().equals(hornPlayer)) {
				return pass;
			}
		}
		return null;
	}

	public float getAirBrake() {
		return airBrake;
	}

	public float getThrottle() {
		return throttle;
	}

	public int getBell() {
		return bellTime;
	}
	public void setBell(int newBell) {
		this.bellTime = newBell;
	}

	public double slipCoefficient() {
		double slipMult = 1.0;
		World world = getWorld();
		if (world.isPrecipitating() && world.canSeeSky(getBlockPosition())) {
			if (world.isRaining(getBlockPosition())) {
				slipMult = 0.6;
			}
			if (world.isSnowing(getBlockPosition())) {
				slipMult = 0.4;
			}
		}
		// Wheel balance messing with friction
		if (this.getCurrentSpeed().metric() != 0) {
			double balance = 1 - 0.004 * Math.abs(this.getCurrentSpeed().metric());
			slipMult *= balance;
		}
		return slipMult;
	}
	
	public float ambientTemperature() {
	    // null during registration
		return internal != null ? getWorld().getTemperature(getBlockPosition()) : 0f;
	}
}
