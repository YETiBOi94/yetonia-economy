package com.yetonia.economy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client authoritative balance sync for the HUD. */
public record YetoniaBalancePayload(long balance) implements CustomPacketPayload {
    public static final Type<YetoniaBalancePayload> TYPE = new Type<>(YetoniaEconomy.id("balance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, YetoniaBalancePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG,
                    YetoniaBalancePayload::balance,
                    YetoniaBalancePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
