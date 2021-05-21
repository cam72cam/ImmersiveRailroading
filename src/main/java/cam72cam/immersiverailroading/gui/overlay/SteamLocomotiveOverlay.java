package cam72cam.immersiverailroading.gui.overlay;

import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.fluid.Fluid;

public class SteamLocomotiveOverlay extends ControlPanelOverlay {
	public void draw() {
		Entity riding = MinecraftClient.getPlayer().getRiding();
		if (riding == null) {
			return;
		}
		if (!(riding instanceof LocomotiveSteam)) {
			return;
		}
		LocomotiveSteam loco = (LocomotiveSteam) riding;
		drawBackground(loco);
		drawGauge(0xAA0F0FFF, ((float)loco.getLiquidAmount())/ Fluid.BUCKET_VOLUME, loco.getTankCapacity().Buckets(), "B");
		drawGauge(0x99DDDDDD, loco.getBoilerPressure(), loco.getDefinition().getMaxPSI(loco.gauge), "PSI");
		
		int boilerColor = 0x99d1c715;
		if (loco.getBoilerTemperature() > 95) {
			boilerColor = 0x99d16c15;
		}
		if (loco.getBoilerTemperature() > 105) {
			boilerColor = 0x99a21010;
		}
		
		drawGauge(boilerColor, loco.getBoilerTemperature(), 150, "C");
		
		drawScalar(GuiText.LABEL_BRAKE.toString(), loco.getAirBrake()*10, 0, 10);
		drawScalar(GuiText.LABEL_THROTTLE.toString(), loco.getThrottle()*10, -10, 10);
		
		drawSpeedDisplay(loco, 20);
	}
}
