package com.yetonia.economy;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/** Client-only HUD renderer. The server remains authoritative over the balance. */
public final class YetoniaEconomyClient implements ClientModInitializer {
    private static final Identifier HUD_ID = YetoniaEconomy.id("coin_balance_hud");
    private static final Identifier ICON = YetoniaEconomy.id("textures/gui/coin_icon.png");
    private static volatile long balance = 0L;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(YetoniaBalancePayload.TYPE,
                (payload, context) -> balance = Math.max(0L, payload.balance()));

        HudElementRegistry.addLast(HUD_ID, new HudElement() {
            @Override
            public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
                renderHud(graphics);
            }
        });
    }

    private static void renderHud(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        String amount = "Y " + String.format(Locale.ROOT, "%,d", balance);

        int width = graphics.guiWidth();
        int panelWidth = 184;
        int panelHeight = 54;
        int x = width - panelWidth - 12;
        int y = 12;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xCC11141C);
        graphics.fill(x, y, x + 3, y + panelHeight, 0xFFFFC83D);

        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON,
                x + 10, y + 10,
                0.0f, 0.0f,
                16, 16,
                16, 16);
        graphics.text(font, "Yetonia Coins", x + 34, y + 7, 0xFFFFFFFF, true);
        graphics.text(font, amount, x + 34, y + 27, 0xFFFFD84A, true);
    }
}
