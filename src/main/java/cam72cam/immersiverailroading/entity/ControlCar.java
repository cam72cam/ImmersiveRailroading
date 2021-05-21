package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.registry.ControlCarDefinition;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.fluid.Fluid;

import java.util.ArrayList;
import java.util.List;

public class ControlCar extends ControllableStock {

    @Override
    public int getInventoryWidth() {
        return 2;
    }

    @Override
    public ControlCarDefinition getDefinition() {
        return super.getDefinition(ControlCarDefinition.class);
    }

    @Override
    public void onTick() {
        super.onTick();
    }

    @Override
    public List<Fluid> getFluidFilter() {
        return new ArrayList<>();
    }

    @Override
    public FluidQuantity getTankCapacity() {
        return FluidQuantity.ZERO;
    }
}
