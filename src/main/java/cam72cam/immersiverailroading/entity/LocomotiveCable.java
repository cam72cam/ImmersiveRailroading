package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.registry.CableLocomotiveDefinition;
import cam72cam.immersiverailroading.util.FluidQuantity;

public class LocomotiveCable extends LocomotiveUnfueled {

	public LocomotiveCable() {
		super();
		this.setIgnoreSlope(true);
	}

	@Override
	public int getInventoryWidth() {
		return 2;
	}

	@Override
	public CableLocomotiveDefinition getDefinition() {
		return super.getDefinition(CableLocomotiveDefinition.class);
	}

	@Override
	protected int getAvailableHP() {
		return this.getDefinition().getHorsePower(gauge);
	}

	@Override
	public FluidQuantity getTankCapacity() {
		return FluidQuantity.ZERO;
	}
}