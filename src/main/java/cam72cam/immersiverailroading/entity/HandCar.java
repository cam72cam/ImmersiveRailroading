package cam72cam.immersiverailroading.entity;

import java.util.ArrayList;
import java.util.List;

import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.fluid.Fluid;

public class HandCar extends LocomotiveUnfueled {

	@Override
	protected int getAvailableHP() {
		int passengers = 0;
		for (Entity passenger : this.getPassengers()) {
			if (passenger.isPlayer()) {
				Player player = passenger.asPlayer();
				if (!player.isCreative()) {
					if (player.getFoodLevel() > 0) {
						passengers++;
					}
				} else {
					passengers++;
				}
			}
		}
		return this.getDefinition().getHorsePower(gauge) * passengers;
	}
	
	@Override
	public void onTick() {
		super.onTick();
		
		if (getWorld().isClient) {
			return;
		}
		
		if (this.getThrottle() != 0 && this.getTickCount() % (int)(200 * (1.1-Math.abs(this.getThrottle()))) == 0) {
			for (Entity passenger : this.getPassengers()) {
				if (passenger.isPlayer()) {
					Player player = passenger.asPlayer();
					if (!player.isCreative()) {
						if (player.getFoodLevel() > 0) {
							player.useFood(1);
						}
					}
				}
			}
		}
	}

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
