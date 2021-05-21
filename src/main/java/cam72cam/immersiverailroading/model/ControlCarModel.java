package cam72cam.immersiverailroading.model;

import cam72cam.immersiverailroading.entity.ControlCar;
import cam72cam.immersiverailroading.model.components.ComponentProvider;
import cam72cam.immersiverailroading.model.part.Horn;
import cam72cam.immersiverailroading.registry.ControlCarDefinition;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;

public class ControlCarModel extends ControllableStockModel<ControlCar> {
    private Horn horn;

    public ControlCarModel(ControlCarDefinition def) throws Exception {
        super(def);
    }

    @Override
    protected void parseComponents(ComponentProvider provider, EntityRollingStockDefinition def) {
        super.parseComponents(provider, def);
        horn = Horn.get(provider, ((ControlCarDefinition)def).horn, ((ControlCarDefinition)def).getHornSus());
    }

    @Override
    protected void effects(ControlCar stock) {
        super.effects(stock);
        horn.effects(stock,
                stock.getHornTime() > 0
                        ? stock.getDefinition().getHornSus() ? stock.getHornTime() / 10f : 1
                        : 0);
    }

    @Override
    protected void removed(ControlCar stock) {
        super.removed(stock);
        horn.removed(stock);
    }

    @Override
    protected void render(ControlCar stock, ComponentRenderer draw, double distanceTraveled) {
        super.render(stock, draw, distanceTraveled);
        horn.render(draw);
    }
}
