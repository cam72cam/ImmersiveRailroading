package cam72cam.immersiverailroading.entity;

import cam72cam.immersiverailroading.library.KeyTypes;
import cam72cam.immersiverailroading.registry.BrakeVanDefinition;
import cam72cam.immersiverailroading.util.BurnUtil;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.fluid.Fluid;

import java.util.List;

public class BrakeVan extends ControllableStock {

    @Override
    public int getInventoryWidth() {
        return 2;
    }

    @Override
    public BrakeVanDefinition getDefinition() {
        return super.getDefinition(BrakeVanDefinition.class);
    }

    @Override
    public List<Fluid> getFluidFilter() {
        return BurnUtil.burnableFluids();
    }

    @Override
    public FluidQuantity getTankCapacity() {
        return FluidQuantity.ZERO;
    }

    @Override
    protected void realAirBrake(float newAirBrake) {
        if(this.getDefinition().multiUnitCapable) {
            super.realAirBrake(newAirBrake);
        }
    }

    @Override
    public void setAirBrake(float newAirBrake) {
        super.realAirBrake(newAirBrake);
    }
}
