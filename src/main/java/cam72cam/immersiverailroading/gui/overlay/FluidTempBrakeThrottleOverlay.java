package cam72cam.immersiverailroading.gui.overlay;

import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.fluid.Fluid;

public class FluidTempBrakeThrottleOverlay extends ControlPanelOverlay {
	private final static int cold = 0x992a81af;
	private final static int warm = 0x992a933a;
	private final static int hot = 0x99d16c15;
	private final static int danger = 0x99a21010;
	
	
	
	public void draw() {
		Entity riding = MinecraftClient.getPlayer().getRiding();
		if (riding == null) {
			return;
		}
		if (!(riding instanceof LocomotiveDiesel)) {
			return;
		}
		LocomotiveDiesel loco = (LocomotiveDiesel) riding;
		drawBackground(loco);
		drawGauge(0xAA79650c, ((float)loco.getLiquidAmount())/ Fluid.BUCKET_VOLUME, loco.getTankCapacity().Buckets(), "B");
		int heatColor = cold;
		if (loco.getEngineTemperature() > 75) {
			heatColor = warm;
		}
		if (loco.getEngineTemperature() > 100) {
			heatColor = hot;
		}
		if (loco.getEngineTemperature() > 140) {
			heatColor = danger;
		}
		drawGauge(heatColor, loco.getEngineTemperature(), 150, "C");
		
		drawScalar(GuiText.LABEL_BRAKE.toString(), loco.getAirBrake()*10, 0, 10);
		drawScalar(GuiText.LABEL_THROTTLE.toString(), loco.getThrottle()*10, -10, 10);
		
		drawSpeedDisplay(loco, 8);
	}
}
