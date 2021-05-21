package cam72cam.immersiverailroading.model;

import cam72cam.immersiverailroading.entity.LocomotiveCable;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.part.Horn;
import cam72cam.immersiverailroading.registry.CableLocomotiveDefinition;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;

public class CableLocomotiveModel extends LocomotiveModel<LocomotiveCable> {
    private Horn horn;

    public CableLocomotiveModel(CableLocomotiveDefinition def) throws Exception {
        super(def);
    }

    @Override
    protected void parseComponents(ComponentProvider provider, EntityRollingStockDefinition def) {
        super.parseComponents(provider, def);
        horn = Horn.get(provider, ((CableLocomotiveDefinition)def).horn, ((CableLocomotiveDefinition)def).getHornSus());
    }

    @Override
    protected void effects(LocomotiveCable stock) {
        super.effects(stock);
        horn.effects(stock,
                stock.getHornTime() > 0
                        ? stock.getDefinition().getHornSus() ? stock.getHornTime() / 10f : 1
                        : 0);
    }

    @Override
    protected void removed(LocomotiveCable stock) {
        super.removed(stock);
        horn.removed(stock);
    }

    @Override
    protected void render(LocomotiveCable stock, ComponentRenderer draw, double distanceTraveled) {
        super.render(stock, draw, distanceTraveled);
        horn.render(draw);
    }
}
