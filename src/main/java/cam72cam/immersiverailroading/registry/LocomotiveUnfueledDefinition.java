package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.HandCar;
import cam72cam.immersiverailroading.entity.LocomotiveUnfueled;
import com.google.gson.JsonObject;

public abstract class LocomotiveUnfueledDefinition extends LocomotiveDefinition {
    public LocomotiveUnfueledDefinition(String defID, JsonObject data) throws Exception {
        super(LocomotiveUnfueled.class, defID, data);
    }

    public LocomotiveUnfueledDefinition(Class<? extends EntityRollingStock> type, String defID, JsonObject data) throws Exception {
        super(type, defID, data);
    }
}
