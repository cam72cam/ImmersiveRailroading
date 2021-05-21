package cam72cam.immersiverailroading.model;

import cam72cam.immersiverailroading.entity.ControllableStock;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.part.Bell;
import cam72cam.immersiverailroading.registry.ControllableStockDefinition;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;

import java.util.List;

public class ControllableStockModel<T extends ControllableStock> extends FreightModel<T> {
    private Bell bell;

    public ControllableStockModel(ControllableStockDefinition def) throws Exception {
        super(def);
    }

    @Override
    protected void parseComponents(ComponentProvider provider, EntityRollingStockDefinition def) {
        super.parseComponents(provider, def);
        bell = Bell.get(
                provider,
                ((ControllableStockDefinition)def).bell
        );
    }

    @Override
    protected void effects(T stock) {
        super.effects(stock);
        bell.effects(stock, stock.getBell() > 0 ? 0.8f : 0);
    }

    @Override
    protected void removed(T stock) {
        super.removed(stock);
        bell.removed(stock);
    }

    @Override
    protected void render(T stock, ComponentRenderer draw, double distanceTraveled) {
        super.render(stock, draw, distanceTraveled);
        bell.render(draw);
    }
}
