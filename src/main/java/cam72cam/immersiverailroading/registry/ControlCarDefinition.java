package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.ControlCar;
import cam72cam.immersiverailroading.model.ControlCarModel;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.mod.resource.Identifier;
import com.google.gson.JsonObject;

public class ControlCarDefinition extends ControllableStockDefinition {
    private static Identifier default_horn = new Identifier(ImmersiveRailroading.MODID, "sounds/diesel/default/horn.ogg");
    private static Identifier default_bell = new Identifier(ImmersiveRailroading.MODID, "sounds/diesel/default/bell.ogg");

    public Identifier horn;
    private boolean hornSus;

    public ControlCarDefinition(String defID, JsonObject data) throws Exception {
        super(ControlCar.class, defID, data);
        super.multiUnitCapable = true;
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
        return new ControlCarModel(this);
    }

    @Override
    public ControlCarModel getModel() {
        return (ControlCarModel) super.getModel();
    }

    //checks to see if horn is sustained, on by default
    public boolean getHornSus() {
        return hornSus;
    }
}
