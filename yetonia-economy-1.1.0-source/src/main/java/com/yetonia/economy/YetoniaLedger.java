package com.yetonia.economy;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent UUID -> Yetonia Coins ledger. */
public final class YetoniaLedger extends SavedData {
    private static final Codec<YetoniaLedger> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(
            map -> {
                YetoniaLedger ledger = new YetoniaLedger();
                map.forEach((uuidText, value) -> {
                    try {
                        ledger.balances.put(UUID.fromString(uuidText), Math.max(0L, value));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore malformed rows rather than failing the whole world.
                    }
                });
                return ledger;
            },
            ledger -> {
                Map<String, Long> out = new LinkedHashMap<>();
                ledger.balances.forEach((uuid, balance) -> out.put(uuid.toString(), Math.max(0L, balance)));
                return out;
            }
    );

    public static final SavedDataType<YetoniaLedger> TYPE = new SavedDataType<>(
            YetoniaEconomy.id("ledger"),
            YetoniaLedger::new,
            CODEC,
            null
    );

    private final Map<UUID, Long> balances = new LinkedHashMap<>();

    public boolean contains(UUID playerId) {
        return balances.containsKey(playerId);
    }

    public long get(UUID playerId) {
        return Math.max(0L, balances.getOrDefault(playerId, 0L));
    }

    public void set(UUID playerId, long amount) {
        balances.put(playerId, Math.max(0L, amount));
        setDirty();
    }

    public long add(UUID playerId, long amount) {
        long old = get(playerId);
        long next = Math.max(0L, safeAdd(old, amount));
        set(playerId, next);
        return next;
    }

    public boolean remove(UUID playerId, long amount) {
        if (amount < 0L || get(playerId) < amount) return false;
        set(playerId, get(playerId) - amount);
        return true;
    }

    private static long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        if (b < 0 && a < Long.MIN_VALUE - b) return Long.MIN_VALUE;
        return a + b;
    }
}
