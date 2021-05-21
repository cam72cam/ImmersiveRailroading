package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.LocomotiveCable;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.model.CableLocomotiveModel;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.mod.resource.Identifier;
import com.google.gson.JsonObject;

public class CableLocomotiveDefinition extends LocomotiveDefinition {
    private static Identifier default_horn = new Identifier(ImmersiveRailroading.MODID, "sounds/diesel/default/horn.ogg");
    private static Identifier default_bell = new Identifier(ImmersiveRailroading.MODID, "sounds/diesel/default/bell.ogg");

    public Identifier idle;
    public Identifier horn;
    private boolean hornSus;

    public CableLocomotiveDefinition(String defID, JsonObject data) throws Exception {
        super(LocomotiveCable.class, defID, data);
    }

    @Override
    public void parseJson(JsonObject data) throws Exception {
        super.parseJson(data);

        JsonObject properties = data.get("properties").getAsJsonObject();

        hornSus = false;
        if (properties.has("horn_sustained")) {
            hornSus = properties.get("horn_sustained").getAsBoolean();
        }
        JsonObject sounds = data.has("sounds") ? data.get("sounds").getAsJsonObject() : null;

        horn = default_horn;
        bell = default_bell;

        if(sounds != null){
            if (sounds.has("horn")) {
                horn = new Identifier(ImmersiveRailroading.MODID, sounds.get("horn").getAsString()).getOrDefault(default_horn);
            }

            if (sounds.has("bell")) {
                bell = new Identifier(ImmersiveRailroading.MODID, sounds.get("bell").getAsString()).getOrDefault(default_bell);
            }
        }
    }

    @Override
    protected StockModel<?> createModel() throws Exception {
        return new CableLocomotiveModel(this);
    }

    @Override
    public CableLocomotiveModel getModel() {
        return (CableLocomotiveModel) super.getModel();
    }

    //checks to see if horn is sustained, on by default
    public boolean getHornSus() {
        return hornSus;
    }

    public FluidQuantity getFuelCapacity(Gauge gauge) {
        return FluidQuantity.ZERO;
    }
}
