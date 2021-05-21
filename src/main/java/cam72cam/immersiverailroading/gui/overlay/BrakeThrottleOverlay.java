package cam72cam.immersiverailroading.gui.overlay;

import cam72cam.immersiverailroading.entity.*;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Entity;

public class BrakeThrottleOverlay extends ControlPanelOverlay {
	public void draw() {
		Entity riding = MinecraftClient.getPlayer().getRiding();
		if (riding == null) {
			return;
		}
		if (!(riding instanceof LocomotiveUnfueled || riding instanceof ControlCar)) {
			return;
		}
		ControllableStock loco = (ControllableStock) riding;
		drawBackground(loco);
		addSpace(10);
		
		drawScalar(GuiText.LABEL_BRAKE.toString(), loco.getAirBrake()*10, 0, 10);
		drawScalar(GuiText.LABEL_THROTTLE.toString(), loco.getThrottle()*10, -10, 10);

		drawSpeedDisplay(loco, 0);
	}
}
