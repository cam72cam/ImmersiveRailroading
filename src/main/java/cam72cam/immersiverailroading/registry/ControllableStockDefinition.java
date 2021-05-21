package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.model.ControllableStockModel;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.mod.resource.Identifier;
import com.google.gson.JsonObject;

import java.util.List;

public abstract class ControllableStockDefinition extends FreightDefinition {
    public boolean toggleBell;
    public Identifier bell;
    private String works;
    public boolean multiUnitCapable;

    ControllableStockDefinition(Class<? extends EntityRollingStock> type, String defID, JsonObject data) throws Exception {
        super(type, defID, data);

        // Handle null data
        if (works == null) {
            works = "Unknown";
        }
    }

    @Override
    public void parseJson(JsonObject data) throws Exception {
        super.parseJson(data);

        works = data.get("works").getAsString();

        JsonObject properties = data.get("properties").getAsJsonObject();

        toggleBell = true;
        if (properties.has("toggle_bell")) {
            toggleBell = properties.get("toggle_bell").getAsBoolean();
        }
    }

    @Override
    protected StockModel<?> createModel() throws Exception {
        return new ControllableStockModel<>(this);
    }

    @Override
    public List<String> getTooltip(Gauge gauge) {
        List<String> tips = super.getTooltip(gauge);
        tips.add(GuiText.LOCO_WORKS.toString(this.works));
        return tips;
    }

    public int getStartingTractionNewtons(Gauge gauge) {
        return 0;
    }

    public double getBrakePower() {
        return 1;
    }

}
