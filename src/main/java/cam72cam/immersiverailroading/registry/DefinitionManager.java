package cam72cam.immersiverailroading.registry;

import cam72cam.immersiverailroading.Config.ConfigPerformance;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.model.TrackModel;
import cam72cam.mod.gui.Progress;
import cam72cam.mod.resource.Identifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefinitionManager {

    /**
     * How much memory in bytes does the loading of a stock take.
     * This is used to determine whether loading stock in a multithreaded way is possible.
     */
    private static final long STOCK_LOAD_MEMORY_PER_PROCESSOR = 1024 * 1024 * 1024 + 512 * 1024 * 1204;

    private static Map<String, EntityRollingStockDefinition> definitions;
    private static Map<String, TrackDefinition> tracks;
    private static Map<String, JsonLoader> jsonLoaders;

    static {
        jsonLoaders = new LinkedHashMap<>();
        jsonLoaders.put("locomotives", (String defID, JsonObject data) -> {
            String era = data.get("era").getAsString();
            switch (era) {
                case "steam":
                    return new LocomotiveSteamDefinition(defID, data);
                case "diesel":
                    return new LocomotiveDieselDefinition(defID, data);
                case "cable":
                    return new CableLocomotiveDefinition(defID, data);
                default:
                    ImmersiveRailroading.warn("Invalid era %s in %s", era, defID);
                    return null;
            }
        });
        jsonLoaders.put("control", (String defID, JsonObject data) -> {
            String type = data.get("type").getAsString();
            switch (type) {
                case "cabcar":
                    return new ControlCarDefinition(defID, data);
                case "brakevan":
                    return new BrakeVanDefinition(defID, data);
                default:
                    ImmersiveRailroading.warn("Invalid type %s in %s", type, defID);
                    return null;
            }
        });
        jsonLoaders.put("tender", TenderDefinition::new);
        jsonLoaders.put("passenger", CarPassengerDefinition::new);
        jsonLoaders.put("freight", CarFreightDefinition::new);
        jsonLoaders.put("tank", CarTankDefinition::new);
        jsonLoaders.put("hand_car", HandCarDefinition::new);
    }

    private static void initGauges() throws IOException {
        for (Gauge value : new ArrayList<>(Gauge.values())) {
            Gauge.remove(value.value());
        }

        Identifier gauges_json = new Identifier(ImmersiveRailroading.MODID, "rolling_stock/gauges.json");

        List<Double> toRemove = new ArrayList<>();

        List<InputStream> inputs = gauges_json.getResourceStreamAll();
        for (InputStream input : inputs) {
            JsonParser parser = new JsonParser();
            JsonObject gauges = parser.parse(new InputStreamReader(input)).getAsJsonObject();
            input.close();

            if (gauges.has("register")) {
                for (Entry<String, JsonElement> gauge : gauges.get("register").getAsJsonObject().entrySet()) {
                    Gauge.register(gauge.getValue().getAsDouble(), gauge.getKey());
                }
            }
            if (gauges.has("remove")) {
                for (JsonElement gauge : gauges.get("remove").getAsJsonArray()) {
                    toRemove.add(gauge.getAsDouble());
                }
            }
        }

        for (double gauge : toRemove) {
            Gauge.remove(gauge);
        }
    }

    public static void initDefinitions() {
        try {
            initGauges();
        } catch (Exception e) {
            throw new RuntimeException("Unable to load gauges, do you have a broken pack?", e);
        }

        // Parallel streams use numCPUs-1 threads for stream workloads.
        Runtime runtime = Runtime.getRuntime();
        int processors = runtime.availableProcessors() - 1;

        // Manual garbage collection so we get an accurate quantity of free memory.
        runtime.gc();

        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        if (maxMemory == Long.MAX_VALUE) {
            maxMemory = totalMemory;
        }

        int loadingThreads = Math.max(1, Math.min(processors, (int) (maxMemory / STOCK_LOAD_MEMORY_PER_PROCESSOR)));
        ForkJoinPool stockLoadingPool = new ForkJoinPool(loadingThreads, pool -> {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("ImmersiveRailroading-" + worker.getPoolIndex());
            return worker;
        }, null, false);
        try {
            stockLoadingPool.submit(() -> {
                try {
                    initModels();
                } catch (IOException e) {
                    throw new RuntimeException("Unable to load rolling stock, do you have a broken pack?", e);
                }
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            // the pool broke in unexpected ways or was killed
            throw new RuntimeException(e);
        } finally {
            stockLoadingPool.shutdown();
        }
        try {
            initTracks();
        } catch (Exception e) {
            throw new RuntimeException("Unable to load tracks, do you have a broken pack?", e);
        }
    }

    private static void initModels() throws IOException {
        ImmersiveRailroading.info("Loading stock models.");

        Set<String> defTypes = jsonLoaders.keySet();
        List<String> blacklist = getModelBlacklist(defTypes);

        LinkedHashMap<String, String> definitionIDMap = new LinkedHashMap<>();
        Identifier stock_json = new Identifier(ImmersiveRailroading.MODID, "rolling_stock/stock.json");
        List<InputStream> inputs = stock_json.getResourceStreamAll();
        for (InputStream input : inputs) {

            JsonParser parser = new JsonParser();
            JsonObject stock = parser.parse(new InputStreamReader(input)).getAsJsonObject();
            input.close();

            for (String defType : defTypes) {
                if (stock.has(defType)) {
                    for (JsonElement defName : stock.get(defType).getAsJsonArray()) {
                        if (blacklist.contains(defName.getAsString())) {
                            ImmersiveRailroading.info("Skipping blacklisted %s", defName.getAsString());
                            continue;
                        }

                        String defID = String.format("rolling_stock/%s/%s.json", defType, defName.getAsString());
                        if (definitionIDMap.containsKey(defID)) {
                            continue;
                        }

                        definitionIDMap.put(defID, defType);
                    }
                }
            }
        }

        Progress.Bar bar = Progress.push("Loading Models", definitionIDMap.size());

        Map<String, EntityRollingStockDefinition> loaded = getStockLoadingStream(definitionIDMap.entrySet()).map(tuple -> {
            String defID = tuple.getKey();
            String defType = tuple.getValue();

            ImmersiveRailroading.debug("Loading stock " + defID);
            Identifier resource = new Identifier(ImmersiveRailroading.MODID, defID);

            try (InputStream input = resource.getResourceStream()) {
                JsonParser parser = new JsonParser();
                JsonObject jsonData = parser.parse(new InputStreamReader(input)).getAsJsonObject();
                input.close();

                EntityRollingStockDefinition stockDefinition = jsonLoaders.get(defType).apply(defID, jsonData);

                return Pair.of(stockDefinition.defID, stockDefinition);
            } catch (Exception e) {
                ImmersiveRailroading.error("Error loading model %s of type %s", defID, defType);
                ImmersiveRailroading.catching(e);

                return null;
            } finally {
                synchronized (bar) {
                    bar.step(defID);
                }
            }
        }).filter(Objects::nonNull).collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        definitions = new LinkedHashMap<>();
        definitionIDMap.keySet().stream().filter(loaded::containsKey).forEach(x -> definitions.put(x, loaded.get(x)));

        Progress.pop(bar);
    }

    private static List<String> getModelBlacklist(Set<String> defTypes) throws IOException {
        List<String> blacklist = new ArrayList<>();
        Identifier blacklist_json = new Identifier(ImmersiveRailroading.MODID, "rolling_stock/blacklist.json");

        List<InputStream> inputs = blacklist_json.getResourceStreamAll();
        for (InputStream input : inputs) {
            JsonParser parser = new JsonParser();
            JsonObject stock = parser.parse(new InputStreamReader(input)).getAsJsonObject();
            input.close();

            for (String defType : defTypes) {
                if (stock.has(defType)) {
                    for (JsonElement defName : stock.get(defType).getAsJsonArray()) {
                        blacklist.add(defName.getAsString());
                    }
                }
            }
        }

        return blacklist;
    }

    private static void initTracks() throws IOException {
        tracks = new LinkedHashMap<>();

        ImmersiveRailroading.info("Loading tracks.");
        Identifier track_json = new Identifier(ImmersiveRailroading.MODID, "track/track.json");

        List<InputStream> inputs = track_json.getResourceStreamAll();
        for (InputStream input : inputs) {

            JsonParser parser = new JsonParser();
            JsonObject track = parser.parse(new InputStreamReader(input)).getAsJsonObject();
            input.close();

            JsonArray types = track.getAsJsonArray("types");
            Progress.Bar bar = Progress.push("Loading Tracks", types.size());

            for (JsonElement def : types) {
                bar.step(def.getAsString());
                String trackID = String.format("immersiverailroading:track/%s.json", def.getAsString());
                ImmersiveRailroading.debug("Loading Track %s", trackID);
                JsonParser trackParser = new JsonParser();
                JsonObject trackData = trackParser.parse(new InputStreamReader(new Identifier(trackID).getResourceStream())).getAsJsonObject();
                try {
                    tracks.put(trackID, new TrackDefinition(trackID, trackData));
                } catch (Exception e) {
                    ImmersiveRailroading.catching(e);
                }
            }

            Progress.pop(bar);
        }
    }

    /**
     * Get a stream for a collection that is used to load stocks in a singlethreaded or a multithreaded way.
     *
     * @param collection Collection of items.
     * @param <E> Type of item.
     * @return Singlethreaded or multithreaded stream.
     */
    private static <E> Stream<E> getStockLoadingStream(Collection<E> collection) {
        if (!ConfigPerformance.multithreadedStockLoading) {
            return collection.stream();
        }
        return collection.parallelStream();
    }

    public static EntityRollingStockDefinition getDefinition(String defID) {
        return definitions.get(defID);
    }

    public static Collection<EntityRollingStockDefinition> getDefinitions() {
        return definitions.values();
    }

    public static Set<String> getDefinitionNames() {
        return definitions.keySet();
    }

    public static List<TrackDefinition> getTracks() {
        return new ArrayList<>(tracks.values());
    }

    public static List<String> getTrackIDs() {
        return new ArrayList<>(tracks.keySet());
    }

    public static TrackModel getTrack(String track, double value) {
        return getTrack(track).getTrackForGauge(value);
    }

    public static TrackDefinition getTrack(String track) {
        TrackDefinition def = tracks.get(track);
        if (def == null) {
            def = tracks.values().stream().findFirst().get();
        }
        return def;
    }

    @FunctionalInterface
    private interface JsonLoader {
        EntityRollingStockDefinition apply(String defID, JsonObject data) throws Exception;
    }

}
