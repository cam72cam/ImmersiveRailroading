package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.entity.HandCar;
import com.google.gson.JsonObject;

public class HandCarDefinition extends LocomotiveUnfueledDefinition {
    public HandCarDefinition(String defID, JsonObject data) throws Exception {
        super(HandCar.class, defID, data);
    }

    @Override
    public double getBrakePower() {
        return 0.1;
    }
}
