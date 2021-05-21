package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityBuildableRollingStock;
import cam72cam.immersiverailroading.entity.EntityCoupleableRollingStock.CouplerType;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.GuiText;
import cam72cam.immersiverailroading.library.ItemComponentType;
import cam72cam.immersiverailroading.library.ModelComponentType;
import cam72cam.immersiverailroading.model.StockModel;
import cam72cam.immersiverailroading.model.components.ModelComponent;
import cam72cam.immersiverailroading.util.RealBB;
import cam72cam.mod.entity.EntityRegistry;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.model.obj.OBJGroup;
import cam72cam.mod.model.obj.VertexBuffer;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.serialization.ResourceCache;
import cam72cam.mod.serialization.ResourceCache.GenericByteBuffer;
import cam72cam.mod.serialization.TagCompound;
import cam72cam.mod.serialization.TagField;
import cam72cam.mod.serialization.TagMapped;
import cam72cam.mod.text.TextUtil;
import cam72cam.mod.world.World;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@TagMapped(EntityRollingStockDefinition.TagMapper.class)
public abstract class EntityRollingStockDefinition {
    private static Identifier default_wheel_sound = new Identifier(ImmersiveRailroading.MODID, "sounds/default/track_wheels.ogg");
    private static Identifier default_clackFront = new Identifier(ImmersiveRailroading.MODID, "sounds/default/clack.ogg");
    private static Identifier default_clackRear = new Identifier(ImmersiveRailroading.MODID, "sounds/default/clack.ogg");

    public final String defID;
    private final Class<? extends EntityRollingStock> type;
    public Map<String, String> textureNames = null;
    public float dampeningAmount;
    public Gauge recommended_gauge;
    public Boolean shouldSit;
    public Identifier wheel_sound;
    public Identifier clackFront;
    public Identifier clackRear;
    public double internal_model_scale;
    double internal_inv_scale;
    private String name = "Unknown";
    private String modelerName = "N/A";
    private String packName = "N/A";
    public float darken;
    public Identifier modelLoc;
    private StockModel<?> model;
    private Vec3d passengerCenter = new Vec3d(0, 0, 0);
    private float bogeyFront;
    private float bogeyRear;
    private float couplerOffsetFront;
    private float couplerOffsetRear;
    private boolean scalePitch;
    private double frontBounds;
    private double rearBounds;
    private double heightBounds;
    private double widthBounds;
    private boolean brakeless;
    private double passengerCompartmentLength;
    private double passengerCompartmentWidth;
    private int weight;
    private int maxPassengers;
    private final Map<ModelComponentType, List<ModelComponent>> renderComponents;
    private final List<ItemComponentType> itemComponents;
    private final Function<EntityBuildableRollingStock, float[][]> heightmap;

    public EntityRollingStockDefinition(Class<? extends EntityRollingStock> type, String defID, JsonObject data) throws Exception {
        this.type = type;
        this.defID = defID;


        parseJson(data);

        this.model = createModel();

        this.renderComponents = new HashMap<>();
        for (ModelComponent component : model.allComponents) {
            renderComponents.computeIfAbsent(component.type, v -> new ArrayList<>())
                    .add(0, component);
        }

        itemComponents = model.allComponents.stream()
                .map(component -> component.type)
                .map(ItemComponentType::from)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        frontBounds = -model.minOfGroup(model.groups()).x + couplerOffsetFront;
        rearBounds = model.maxOfGroup(model.groups()).x + couplerOffsetRear;
        widthBounds = model.widthOfGroups(model.groups());

        // Bad hack for height bounds
        ArrayList<String> heightGroups = new ArrayList<>();
        for (String group : model.groups()) {
            boolean ignore = false;
            for (ModelComponentType rct : ModelComponentType.values()) {
                if (rct.collisionsEnabled) {
                    continue;
                }
                for (int i = 0; i < 10; i++) {
                    if (Pattern.matches(rct.regex.replace("#ID#", "" + i), group)) {
                        ignore = true;
                        break;
                    }
                }
                if (ignore) {
                    break;
                }
            }
            if (!ignore) {
                heightGroups.add(group);
            }
        }
        heightBounds = model.heightOfGroups(heightGroups);

        this.heightmap = initHeightmap();
    }

    public final EntityRollingStock spawn(World world, Vec3d pos, float yaw, Gauge gauge, String texture) {
        EntityRollingStock stock = (EntityRollingStock) EntityRegistry.create(world, type);
        stock.setPosition(pos);
        stock.setRotationYaw(yaw);
        // Override prev
        stock.setRotationYaw(yaw);
        stock.setup(defID, gauge, texture);

        return stock;
    }

    public boolean shouldScalePitch() {
        return scalePitch;
    }

    public void parseJson(JsonObject data) throws Exception {
        name = data.get("name").getAsString();
        if (data.has("modeler")) {
            this.modelerName = data.get("modeler").getAsString();
        }
        if (data.has("pack")) {
            this.packName = data.get("pack").getAsString();
        }
        darken = 0;
        if (data.has("darken_model")) {
            darken = data.get("darken_model").getAsFloat();
        }
        internal_model_scale = 1;
        internal_inv_scale = 1;
        // TODO Gauge.from(Gauge.STANDARD).value() what happens when != Gauge.STANDARD
        this.recommended_gauge = Gauge.from(Gauge.STANDARD);
        if (data.has("model_gauge_m")) {
            this.recommended_gauge = Gauge.from(data.get("model_gauge_m").getAsDouble());
            internal_model_scale = Gauge.STANDARD / data.get("model_gauge_m").getAsDouble();
        }
        if (data.has("recommended_gauge_m")) {
            this.recommended_gauge = Gauge.from(data.get("recommended_gauge_m").getAsDouble());
        }
        if (this.recommended_gauge != Gauge.from(Gauge.STANDARD)) {
            internal_inv_scale = Gauge.STANDARD / recommended_gauge.value();
        }

        textureNames = new LinkedHashMap<>();
        textureNames.put("", "Default");
        if (data.has("tex_variants")) {
            JsonElement variants = data.get("tex_variants");
            for (Entry<String, JsonElement> variant : variants.getAsJsonObject().entrySet()) {
                textureNames.put(variant.getValue().getAsString(), variant.getKey());
            }
        }

        Identifier alt_textures = new Identifier(ImmersiveRailroading.MODID, defID.replace(".json", "_variants.json"));
        try {
            List<InputStream> alts = alt_textures.getResourceStreamAll();
            for (InputStream input : alts) {
                JsonParser parser = new JsonParser();
                JsonElement variants = parser.parse(new InputStreamReader(input));
                for (Entry<String, JsonElement> variant : variants.getAsJsonObject().entrySet()) {
                    textureNames.put(variant.getValue().getAsString(), variant.getKey());
                }
            }
        } catch (java.io.FileNotFoundException ex) {
            //ignore
        }

        modelLoc = new Identifier(data.get("model").getAsString());

        JsonObject passenger = data.get("passenger").getAsJsonObject();
        passengerCenter = new Vec3d(0, passenger.get("center_y").getAsDouble() - 0.35, passenger.get("center_x").getAsDouble()).scale(internal_model_scale);
        passengerCompartmentLength = passenger.get("length").getAsDouble() * internal_model_scale;
        passengerCompartmentWidth = passenger.get("width").getAsDouble() * internal_model_scale;
        maxPassengers = passenger.get("slots").getAsInt();
        if (passenger.has("should_sit")) {
            shouldSit = passenger.get("should_sit").getAsBoolean();
        }
        if (data.has("brakeless")) {
            brakeless = passenger.get("brakeless").getAsBoolean();
        }

        bogeyFront = (float) (data.get("trucks").getAsJsonObject().get("front").getAsFloat() * internal_model_scale);
        bogeyRear = (float) (data.get("trucks").getAsJsonObject().get("rear").getAsFloat() * internal_model_scale);

        dampeningAmount = 0.75f;
        if (data.has("sound_dampening_percentage")) {
            if (data.get("sound_dampening_percentage").getAsFloat() >= 0.0f && data.get("sound_dampening_percentage").getAsFloat() <= 1.0f) {
                dampeningAmount = data.get("sound_dampening_percentage").getAsFloat();
            }
        }

        scalePitch = true;
        if (data.has("scale_pitch")) {
            scalePitch = data.get("scale_pitch").getAsBoolean();
        }

        if (data.has("couplers")) {
            couplerOffsetFront = (float) (data.get("couplers").getAsJsonObject().get("front_offset").getAsFloat() * internal_model_scale);
            couplerOffsetRear = (float) (data.get("couplers").getAsJsonObject().get("rear_offset").getAsFloat() * internal_model_scale);
        }

        weight = (int) Math.ceil(data.get("properties").getAsJsonObject().get("weight_kg").getAsInt() * internal_inv_scale);

        wheel_sound = default_wheel_sound;
        clackFront = default_clackFront;
        clackRear = default_clackRear;

        JsonObject sounds = data.has("sounds") ? data.get("sounds").getAsJsonObject() : null;
        if (sounds != null) {
            if (sounds.has("wheels")) {
                wheel_sound = new Identifier(ImmersiveRailroading.MODID, sounds.get("wheels").getAsString()).getOrDefault(default_wheel_sound);
            }

            if (sounds.has("clack")) {
                clackFront = new Identifier(ImmersiveRailroading.MODID, sounds.get("clack").getAsString()).getOrDefault(default_clackFront);
                clackRear = new Identifier(ImmersiveRailroading.MODID, sounds.get("clack").getAsString()).getOrDefault(default_clackRear);
            }
            if (sounds.has("clack_front")) {
                clackFront = new Identifier(ImmersiveRailroading.MODID, sounds.get("clack_front").getAsString()).getOrDefault(default_clackFront);
            }
            if (sounds.has("clack_rear")) {
                clackRear = new Identifier(ImmersiveRailroading.MODID, sounds.get("clack_rear").getAsString()).getOrDefault(default_clackRear);
            }
        }
    }

    public List<ModelComponent> getComponents(ModelComponentType name) {
        if (!renderComponents.containsKey(name)) {
            return null;
        }
        return renderComponents.get(name);
    }

    public Vec3d correctPassengerBounds(Gauge gauge, Vec3d pos, boolean shouldSit) {
        double gs = gauge.scale();
        Vec3d passengerCenter = this.passengerCenter.scale(gs);
        pos = pos.subtract(passengerCenter);
        if (pos.z > this.passengerCompartmentLength * gs) {
            pos = new Vec3d(pos.x, pos.y, this.passengerCompartmentLength * gs);
        }

        if (pos.z < -this.passengerCompartmentLength * gs) {
            pos = new Vec3d(pos.x, pos.y, -this.passengerCompartmentLength * gs);
        }

        if (Math.abs(pos.x) > this.passengerCompartmentWidth / 2 * gs) {
            pos = new Vec3d(Math.copySign(this.passengerCompartmentWidth / 2 * gs, pos.x), pos.y, pos.z);
        }

        pos = new Vec3d(pos.x, passengerCenter.y - (shouldSit ? 0.75 : 0), pos.z + passengerCenter.z);

        return pos;
    }

    public boolean isAtFront(Gauge gauge, Vec3d pos) {
        pos = pos.subtract(passengerCenter.scale(gauge.scale()));
        return pos.z >= this.passengerCompartmentLength * gauge.scale();
    }

    public boolean isAtRear(Gauge gauge, Vec3d pos) {
        pos = pos.subtract(passengerCenter.scale(gauge.scale()));
        return pos.z <= -this.passengerCompartmentLength * gauge.scale();
    }

    public List<ItemComponentType> getItemComponents() {
        return itemComponents;
    }

    public float getBogeyFront(Gauge gauge) {
        return (float) gauge.scale() * this.bogeyFront;
    }

    public float getBogeyRear(Gauge gauge) {
        return (float) gauge.scale() * this.bogeyRear;
    }

    public double getCouplerPosition(CouplerType coupler, Gauge gauge) {
        switch (coupler) {
            case FRONT:
                return gauge.scale() * (this.frontBounds);
            case BACK:
                return gauge.scale() * (this.rearBounds);
            default:
                return 0;
        }
    }

    private static class HeightMapData {
        final int xRes;
        final int zRes;
        final List<ModelComponent> components;
        final float[] data;

        HeightMapData(EntityRollingStockDefinition def) {
            ImmersiveRailroading.info("Generating heightmap %s", def.defID);

            double ratio = 8;
            int precision = (int) Math.ceil(def.heightBounds * 4);

            xRes = (int) Math.ceil((def.frontBounds + def.rearBounds) * ratio);
            zRes = (int) Math.ceil(def.widthBounds * ratio);
            components = def.renderComponents.values().stream()
                    .flatMap(Collection::stream)
                    .filter(rc -> rc.type.collisionsEnabled)
                    .collect(Collectors.toList());
            data = new float[components.size() * xRes * zRes];

            VertexBuffer vb = def.model.vbo.get();

            for (int i = 0; i < components.size(); i++) {
                ModelComponent rc = components.get(i);
                int idx = i * xRes * zRes;
                for (String group : rc.modelIDs) {
                    OBJGroup faces = def.model.groups.get(group);

                    for (int face = faces.faceStart; face <= faces.faceStop; face++) {
                        Path2D path = new Path2D.Float();
                        float fheight = 0;
                        boolean first = true;
                        for (int point = 0; point < vb.vertsPerFace; point++) {
                            int vertex = face * vb.vertsPerFace * vb.stride + point * vb.stride;
                            float vertX = vb.data[vertex + 0];
                            float vertY = vb.data[vertex + 1];
                            float vertZ = vb.data[vertex + 2];
                            vertX += def.frontBounds;
                            vertZ += def.widthBounds / 2;
                            if (first) {
                                path.moveTo(vertX * ratio, vertZ * ratio);
                                first = false;
                            } else {
                                path.lineTo(vertX * ratio, vertZ * ratio);
                            }
                            fheight += vertY / vb.vertsPerFace;
                        }
                        Rectangle2D bounds = path.getBounds2D();
                        if (bounds.getWidth() * bounds.getHeight() < 1) {
                            continue;
                        }
                        for (int x = 0; x < xRes; x++) {
                            for (int z = 0; z < zRes; z++) {
                                float relX = ((xRes - 1) - x);
                                float relZ = z;
                                if (bounds.contains(relX, relZ) && path.contains(relX, relZ)) {
                                    float relHeight = fheight / (float) def.heightBounds;
                                    relHeight = ((int) Math.ceil(relHeight * precision)) / (float) precision;
                                    data[idx + x * zRes + z] = Math.max(data[idx + x * zRes + z], relHeight);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private Function<EntityBuildableRollingStock, float[][]> initHeightmap() {
        String key = String.format(
                "heightmap-%s-%s-%s-%s-%s-%s",
                model.hash, frontBounds, rearBounds, widthBounds, heightBounds, renderComponents.size());
        try {
            ResourceCache<HeightMapData> cache = new ResourceCache<>(modelLoc, key, provider -> new HeightMapData(this));
            Supplier<GenericByteBuffer> data = cache.getResource("data.bin", builder -> new GenericByteBuffer(builder.data));
            Supplier<GenericByteBuffer> meta = cache.getResource("meta.nbt", builder -> {
                try {
                    return new GenericByteBuffer(new TagCompound()
                            .setInteger("xRes", builder.xRes)
                            .setInteger("zRes", builder.zRes)
                            .setList("components", builder.components, v -> new TagCompound().setString("key", v.key))
                            .toBytes()
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            cache.close();

            return (stock) -> {
                try {
                    float[] raw = data.get().floats();
                    TagCompound tc = new TagCompound(meta.get().bytes());

                    int xRes = tc.getInteger("xRes");
                    int zRes = tc.getInteger("zRes");
                    List<String> componentKeys = tc.getList("components", v -> v.getString("key"));

                    float[][] heightMap = new float[xRes][zRes];

                    List<ModelComponentType> availComponents = new ArrayList<>();
                    for (ItemComponentType item : stock.getItemComponents()) {
                        availComponents.addAll(item.render);
                    }

                    for (List<ModelComponent> rcl : this.renderComponents.values()) {
                        for (ModelComponent rc : rcl) {
                            if (!rc.type.collisionsEnabled) {
                                continue;
                            }

                            if (availComponents.contains(rc.type)) {
                                availComponents.remove(rc.type);
                            } else if (rc.type == ModelComponentType.REMAINING && stock.isBuilt()) {
                                //pass
                            } else {
                                continue;
                            }

                            int idx = componentKeys.indexOf(rc.key) * xRes * zRes;
                            for (int x = 0; x < xRes; x++) {
                                for (int z = 0; z < zRes; z++) {
                                    heightMap[x][z] = Math.max(heightMap[x][z], raw[idx + x * zRes + z]);
                                }
                            }
                        }
                    }

                    return heightMap;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public float[][] createHeightMap(EntityBuildableRollingStock stock) {
        return heightmap.apply(stock);
    }

    public RealBB getBounds(EntityMoveableRollingStock stock, Gauge gauge) {
        return new RealBB(gauge.scale() * frontBounds, gauge.scale() * -rearBounds, gauge.scale() * widthBounds,
                gauge.scale() * heightBounds, stock.getRotationYaw()).offset(stock.getPosition());
    }

    public String name() {
        String[] sp = this.defID.replaceAll(".json", "").split("/");
        String localStr = String.format("%s:entity.%s.%s", ImmersiveRailroading.MODID, sp[sp.length - 2], sp[sp.length - 1]);
        String transStr = TextUtil.translate(localStr);
        return !localStr.equals(transStr) ? transStr : name;
    }

    public List<String> getTooltip(Gauge gauge) {
        List<String> tips = new ArrayList<>();
        tips.add(GuiText.WEIGHT_TOOLTIP.toString(this.getWeight(gauge)));
        tips.add(GuiText.MODELER_TOOLTIP.toString(modelerName));
        tips.add(GuiText.PACK_TOOLTIP.toString(packName));
        return tips;
    }

    protected StockModel<?> createModel() throws Exception {
        return new StockModel<>(this);
    }
    public StockModel<?> getModel() {
        return this.model;
    }

    /**
     * @return Stock Weight in Kg
     */
    public int getWeight(Gauge gauge) {
        return (int) Math.ceil(gauge.scale() * this.weight);
    }

    public double getHeight(Gauge gauge) {
        return gauge.scale() * this.heightBounds;
    }

    public double getWidth(Gauge gauge) {
        return gauge.scale() * this.widthBounds;
    }

    public double getLength(Gauge gauge) {
        return gauge.scale() * this.frontBounds + this.rearBounds;
    }

    public int getMaxPassengers() {
        return this.maxPassengers;
    }

    public boolean acceptsPassengers() {
        return false;
    }

    public boolean acceptsLivestock() {
        return false;
    }

    static class TagMapper implements cam72cam.mod.serialization.TagMapper<EntityRollingStockDefinition> {
        @Override
        public TagAccessor<EntityRollingStockDefinition> apply(Class<EntityRollingStockDefinition> type, String fieldName, TagField tag) {
            return new TagAccessor<>(
                    (d, o) -> d.setString(fieldName, o == null ? null : o.defID),
                    d -> DefinitionManager.getDefinition(d.getString(fieldName))
            );
        }
    }

    public boolean hasBrakes() {
        return brakeless;
    }
}
