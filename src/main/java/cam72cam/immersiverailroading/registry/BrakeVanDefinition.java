package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.BrakeVan;
import cam72cam.immersiverailroading.model.ControllableStockModel;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.mod.resource.Identifier;
import com.google.gson.JsonObject;

public class BrakeVanDefinition extends ControllableStockDefinition {
    private static Identifier default_bell = new Identifier(ImmersiveRailroading.MODID, "sounds/diesel/default/bell.ogg");

    public BrakeVanDefinition(String defID, JsonObject data) throws Exception {
        super(BrakeVan.class, defID, data);
        super.multiUnitCapable = false;
    }

    @Override
    public void parseJson(JsonObject data) throws Exception {
        super.parseJson(data);

        JsonObject sounds = data.has("sounds") ? data.get("sounds").getAsJsonObject() : null;

        if(sounds != null){
            if (sounds.has("bell")) {
                bell = new Identifier(ImmersiveRailroading.MODID, sounds.get("bell").getAsString()).getOrDefault(default_bell);
            }
        }
    }

    @Override
    protected StockModel<?> createModel() throws Exception {
        return new ControllableStockModel<>(this);
    }

    @Override
    public ControllableStockModel getModel() {
        return (ControllableStockModel) super.getModel();
    }
}
