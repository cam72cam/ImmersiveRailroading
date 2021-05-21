package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.fluid.Fluid;

import java.util.ArrayList;
import java.util.List;

public abstract class LocomotiveUnfueled extends Locomotive {

	@Override
	public FluidQuantity getTankCapacity() {
		return FluidQuantity.ZERO;
	}

	@Override
	public List<Fluid> getFluidFilter() {
		return new ArrayList<>();
	}

	@Override
	public int getInventoryWidth() {
		return 2;
	}
}
