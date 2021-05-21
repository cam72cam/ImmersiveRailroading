package cam72cam.immersiverailroading.gui.overlay;

import cam72cam.immersiverailroading.entity.BrakeVan;
import cam72cam.immersiverailroading.entity.ControlCar;
import cam72cam.immersiverailroading.entity.ControllableStock;
import cam72cam.immersiverailroading.entity.LocomotiveUnfueled;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.mod.MinecraftClient;
import cam72cam.mod.entity.Entity;

public class BrakeOverlay extends ControlPanelOverlay {
	public void draw() {
		Entity riding = MinecraftClient.getPlayer().getRiding();
		if (riding == null) {
			return;
		}
		if (!(riding instanceof BrakeVan)) {
			return;
		}
		ControllableStock controlledStock = (ControllableStock) riding;
		drawBackground(controlledStock);
		addSpace(19);
		
		drawScalar(GuiText.LABEL_BRAKE.toString(), controlledStock.getAirBrake()*10, 0, 10);

		drawSpeedDisplay(controlledStock, 0);
	}
}
