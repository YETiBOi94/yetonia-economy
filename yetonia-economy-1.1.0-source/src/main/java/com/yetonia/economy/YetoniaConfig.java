package com.yetonia.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Human-editable server configuration. */
public final class YetoniaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("yetonia_economy.json");

    public long startingBalance = 0L;
    public final Map<String, Long> bankerValues = new LinkedHashMap<>();
    public final Map<String, ShopEntry> shop = new LinkedHashMap<>();

    public static YetoniaConfig defaults() {
        YetoniaConfig c = new YetoniaConfig();
        c.bankerValues.put("minecraft:diamond", 100L);
        c.bankerValues.put("minecraft:emerald", 10L);
        c.shop.put("minecraft:oak_log", new ShopEntry(100L, 100L, 100));
        return c;
    }

    public static YetoniaConfig load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE)) {
                YetoniaConfig c = defaults();
                c.save();
                return c;
            }

            YetoniaConfig defaults = defaults();
            YetoniaConfig c = new YetoniaConfig();

            try (Reader reader = Files.newBufferedReader(FILE)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

                c.startingBalance = readLong(root, "startingBalance", "starting_balance", defaults.startingBalance);

                JsonObject bankerObject = object(root, "bankerValues", "banker");
                if (bankerObject != null) {
                    for (Map.Entry<String, JsonElement> e : bankerObject.entrySet()) {
                        if (e.getValue().isJsonPrimitive()) {
                            c.bankerValues.put(normalizeId(e.getKey()), Math.max(0L, e.getValue().getAsLong()));
                        }
                    }
                }
                if (c.bankerValues.isEmpty()) {
                    c.bankerValues.putAll(defaults.bankerValues);
                }

                JsonObject shopObject = object(root, "shop");
                if (shopObject != null) {
                    for (Map.Entry<String, JsonElement> e : shopObject.entrySet()) {
                        if (!e.getValue().isJsonObject()) continue;
                        JsonObject o = e.getValue().getAsJsonObject();
                        long buy = readLong(o, "buyPrice", "buy_price", 0L);
                        long sell = readLong(o, "sellPrice", "sell_price", 0L);
                        int quantity = Math.max(1, (int) readLong(o, "quantity", "quantity", 100L));
                        c.shop.put(normalizeId(e.getKey()), new ShopEntry(buy, sell, quantity));
                    }
                }
                if (c.shop.isEmpty()) {
                    c.shop.putAll(defaults.shop);
                }
            }

            return c;
        } catch (Exception ex) {
            YetoniaEconomy.LOGGER.error("Could not load {}. Using defaults.", FILE, ex);
            YetoniaConfig c = defaults();
            try { c.save(); } catch (IOException ignored) { }
            return c;
        }
    }

    private static JsonObject object(JsonObject root, String primary, String fallback) {
        if (root.has(primary) && root.get(primary).isJsonObject()) return root.getAsJsonObject(primary);
        if (fallback != null && root.has(fallback) && root.get(fallback).isJsonObject()) return root.getAsJsonObject(fallback);
        return null;
    }

    private static long readLong(JsonObject object, String primary, String fallback, long defaultValue) {
        if (object.has(primary) && object.get(primary).isJsonPrimitive()) return object.get(primary).getAsLong();
        if (fallback != null && object.has(fallback) && object.get(fallback).isJsonPrimitive()) return object.get(fallback).getAsLong();
        return defaultValue;
    }

    public void save() throws IOException {
        Files.createDirectories(FILE.getParent());
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(this, writer);
        }
    }

    public Path file() {
        return FILE;
    }

    public static String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT).trim();
    }

    public record ShopEntry(long buyPrice, long sellPrice, int quantity) {
        public long totalBuy(int bundles) {
            return Math.multiplyExact(buyPrice, bundles);
        }

        public long totalSell(int bundles) {
            return Math.multiplyExact(sellPrice, bundles);
        }
    }
}
