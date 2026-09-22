package com.yetonia.economy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class YetoniaEconomy implements net.fabricmc.api.ModInitializer {
    public static final String MOD_ID = "yetonia_economy";
    public static final String GEM_BANKER_TAG = MOD_ID + ":gem_banker";
    public static final String QUARTERMASTER_TAG = MOD_ID + ":quartermaster";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Yetonia Economy");

    private static volatile YetoniaConfig config;
    private static final Map<UUID, String> QUARTERMASTER_SELECTIONS = new LinkedHashMap<>();

    @Override
    public void onInitialize() {
        config = YetoniaConfig.load();

        PayloadTypeRegistry.clientboundPlay().register(YetoniaBalancePayload.TYPE, YetoniaBalancePayload.STREAM_CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> initializePlayer(server, handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register(YetoniaEconomy::registerCommands);
        UseEntityCallback.EVENT.register(YetoniaEconomy::handleNpcUse);

        LOGGER.info("Yetonia Economy 1.1.0 initialized.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static YetoniaConfig config() {
        return config;
    }

    public static void reloadConfig() {
        config = YetoniaConfig.load();
    }

    public static YetoniaLedger ledger(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld is unavailable.");
        return overworld.getDataStorage().computeIfAbsent(YetoniaLedger.TYPE);
    }

    private static void initializePlayer(MinecraftServer server, ServerPlayer player) {
        YetoniaLedger ledger = ledger(server);
        if (!ledger.contains(player.getUUID())) {
            ledger.set(player.getUUID(), config.startingBalance);
        }
        syncBalance(server, player);
    }

    public static long balance(MinecraftServer server, UUID uuid) {
        return ledger(server).get(uuid);
    }

    public static void setBalance(MinecraftServer server, UUID uuid, long amount) {
        ledger(server).set(uuid, Math.max(0L, amount));
        syncBalance(server, uuid);
    }

    public static long addBalance(MinecraftServer server, UUID uuid, long amount) {
        long next = ledger(server).add(uuid, amount);
        syncBalance(server, uuid);
        return next;
    }

    public static boolean takeBalance(MinecraftServer server, UUID uuid, long amount) {
        boolean ok = ledger(server).remove(uuid, amount);
        if (ok) syncBalance(server, uuid);
        return ok;
    }

    private static void syncBalance(MinecraftServer server, ServerPlayer player) {
        syncBalance(server, player.getUUID());
    }

    private static void syncBalance(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            ServerPlayNetworking.send(player, new YetoniaBalancePayload(balance(server, uuid)));
        }
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
                                         net.minecraft.commands.CommandBuildContext buildContext,
                                         Commands.CommandSelection selection) {
        dispatcher.register(
                Commands.literal("balance")
                        .executes(ctx -> showBalance(ctx, ctx.getSource().getPlayer()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showBalance(ctx, EntityArgument.getPlayer(ctx, "player"))))
        );

        dispatcher.register(
                Commands.literal("pay")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(YetoniaEconomy::pay)))
        );

        dispatcher.register(
                Commands.literal("economy")
                        .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> adminSet(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> adminAdd(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(Commands.literal("take")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> adminTake(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    reloadConfig();
                                    ctx.getSource().sendSuccess(() -> Component.literal("Yetonia Economy config reloaded from " + config.file()), true);
                                    return 1;
                                }))
                        .then(Commands.literal("npc")
                                .then(Commands.literal("banker").executes(ctx -> spawnNpc(ctx, true)))
                                .then(Commands.literal("quartermaster").executes(ctx -> spawnNpc(ctx, false))))
        );

        dispatcher.register(
                Commands.literal("shop")
                        .then(Commands.literal("list").executes(YetoniaEconomy::shopList))
                        .then(Commands.literal("buy")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .then(Commands.argument("bundles", IntegerArgumentType.integer(1, 64))
                                                .executes(YetoniaEconomy::shopBuy))))
                        .then(Commands.literal("sell")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .then(Commands.argument("bundles", IntegerArgumentType.integer(1, 64))
                                                .executes(YetoniaEconomy::shopSell))))
        );
    }

    private static int showBalance(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("You must be a player to use /balance without a target."));
            return 0;
        }
        long amount = balance(ctx.getSource().getServer(), target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " has Y " + format(amount) + " (Yetonia Coins)."), false);
        return 1;
    }

    private static int pay(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer sender = source.getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");

        if (sender.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.literal("You cannot pay yourself."));
            return 0;
        }
        if (!takeBalance(source.getServer(), sender.getUUID(), amount)) {
            source.sendFailure(Component.literal("Insufficient Yetonia Coins."));
            return 0;
        }
        addBalance(source.getServer(), target.getUUID(), amount);
        source.sendSuccess(() -> Component.literal("Paid " + target.getName().getString() + " Y " + format(amount) + "."), false);
        target.sendSystemMessage(Component.literal("Received Y " + format(amount) + " Yetonia Coins from " + sender.getName().getString() + "."));
        return 1;
    }

    private static int adminSet(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        setBalance(ctx.getSource().getServer(), target.getUUID(), amount);
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " balance set to Y " + format(amount) + "."), true);
        return 1;
    }

    private static int adminAdd(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        long next = addBalance(ctx.getSource().getServer(), target.getUUID(), amount);
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " balance is now Y " + format(next) + "."), true);
        return 1;
    }

    private static int adminTake(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        boolean ok = takeBalance(ctx.getSource().getServer(), target.getUUID(), amount);
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("Target does not have enough Yetonia Coins."));
            return 0;
        }
        long next = balance(ctx.getSource().getServer(), target.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " balance is now Y " + format(next) + "."), true);
        return 1;
    }

    private static int shopList(CommandContext<CommandSourceStack> ctx) {
        if (config.shop.isEmpty()) return fail(ctx, "The Yetonia shop is empty.");
        List<String> entries = new ArrayList<>();
        config.shop.forEach((id, entry) -> entries.add(id + " : " + entry.quantity() + "x | buy Y " + entry.buyPrice() + " | sell Y " + entry.sellPrice()));
        ctx.getSource().sendSuccess(() -> Component.literal(String.join("  •  ", entries)), false);
        return 1;
    }

    private static int shopBuy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String itemId = YetoniaConfig.normalizeId(StringArgumentType.getString(ctx, "item"));
        int bundles = IntegerArgumentType.getInteger(ctx, "bundles");
        YetoniaConfig.ShopEntry entry = config.shop.get(itemId);
        if (entry == null) return fail(ctx, "That item is not configured in config/yetonia_economy.json.");
        Item item = item(itemId);
        if (item == null) return fail(ctx, "Unknown item id: " + itemId);

        long cost;
        int amount;
        try {
            cost = entry.totalBuy(bundles);
            amount = Math.multiplyExact(entry.quantity(), bundles);
        } catch (ArithmeticException ex) {
            return fail(ctx, "That purchase is too large.");
        }

        if (!takeBalance(ctx.getSource().getServer(), player.getUUID(), cost)) {
            return fail(ctx, "You need Y " + format(cost) + " Yetonia Coins.");
        }

        player.getInventory().placeItemBackInInventory(new ItemStack(item, amount));
        player.sendSystemMessage(Component.literal("Bought " + amount + "x " + itemId + " for Y " + format(cost) + "."));
        return 1;
    }

    private static int shopSell(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String itemId = YetoniaConfig.normalizeId(StringArgumentType.getString(ctx, "item"));
        int bundles = IntegerArgumentType.getInteger(ctx, "bundles");
        YetoniaConfig.ShopEntry entry = config.shop.get(itemId);
        if (entry == null) return fail(ctx, "That item is not configured in config/yetonia_economy.json.");
        Item item = item(itemId);
        if (item == null) return fail(ctx, "Unknown item id: " + itemId);

        int amount;
        long payout;
        try {
            amount = Math.multiplyExact(entry.quantity(), bundles);
            payout = entry.totalSell(bundles);
        } catch (ArithmeticException ex) {
            return fail(ctx, "That sale is too large.");
        }

        if (!removeItems(player.getInventory(), item, amount)) {
            return fail(ctx, "You need " + amount + "x " + itemId + " to sell that bundle.");
        }
        addBalance(ctx.getSource().getServer(), player.getUUID(), payout);
        player.sendSystemMessage(Component.literal("Sold " + amount + "x " + itemId + " for Y " + format(payout) + "."));
        return 1;
    }

    private static int spawnNpc(CommandContext<CommandSourceStack> ctx, boolean banker) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition().relative(player.getDirection());
        Villager villager = EntityTypes.VILLAGER.spawn(level, pos, EntitySpawnReason.COMMAND);
        if (villager == null) return fail(ctx, "Could not spawn the trader here.");

        villager.setNoAi(true);
        villager.setInvulnerable(true);
        villager.setPersistenceRequired();
        villager.setCustomName(Component.literal(banker ? "The Gem Banker" : "The Material Quartermaster"));
        villager.setCustomNameVisible(true);
        villager.addTag(banker ? GEM_BANKER_TAG : QUARTERMASTER_TAG);

        player.sendSystemMessage(Component.literal(banker
                ? "Spawned The Gem Banker. Hold a configured gem and right-click.":
                "Spawned The Material Quartermaster. Shift-right-click to cycle items; right-click empty-handed to buy a bundle or hold a configured item to sell one bundle."));
        return 1;
    }

    private static InteractionResult handleNpcUse(net.minecraft.world.entity.player.Player player,
                                                   Level level,
                                                   InteractionHand hand,
                                                   Entity entity,
                                                   net.minecraft.world.phys.EntityHitResult hitResult) {
        boolean banker = entity.getTags().contains(GEM_BANKER_TAG);
        boolean quartermaster = entity.getTags().contains(QUARTERMASTER_TAG);
        if (!banker && !quartermaster) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.FAIL;
        return banker ? handleBanker(serverPlayer, hand) : handleQuartermaster(serverPlayer, hand);
    }

    private static InteractionResult handleBanker(ServerPlayer player, InteractionHand hand) {
        if (player.isCreative()) {
            player.sendSystemMessage(Component.literal("Creative mode cannot be used to deposit physical items."));
            return InteractionResult.SUCCESS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            player.sendSystemMessage(Component.literal("The Gem Banker accepts configured diamonds and emeralds."));
            return InteractionResult.SUCCESS;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        Long unitValue = config.bankerValues.get(itemId);
        if (unitValue == null) {
            player.sendSystemMessage(Component.literal("The Gem Banker does not accept " + itemId + "."));
            return InteractionResult.SUCCESS;
        }

        int count = held.getCount();
        long payout;
        try {
            payout = Math.multiplyExact(unitValue, (long) count);
        } catch (ArithmeticException ex) {
            player.sendSystemMessage(Component.literal("That deposit is too large."));
            return InteractionResult.SUCCESS;
        }

        held.shrink(count);
        addBalance(player.serverLevel().getServer(), player.getUUID(), payout);
        player.sendSystemMessage(Component.literal("Deposited " + count + "x " + itemId + " for Y " + format(payout) + "."));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult handleQuartermaster(ServerPlayer player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            String next = nextShopItem(player.getUUID());
            player.sendSystemMessage(Component.literal("Quartermaster selection: " + next));
            return InteractionResult.SUCCESS;
        }

        ItemStack held = player.getItemInHand(hand);
        String heldId = held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (heldId != null && config.shop.containsKey(heldId)) {
            return sellOneQuartermasterBundle(player, heldId);
        }

        String selected = QUARTERMASTER_SELECTIONS.getOrDefault(player.getUUID(), config.shop.keySet().stream().findFirst().orElse(null));
        if (selected == null) {
            player.sendSystemMessage(Component.literal("The Quartermaster has no configured shop entries."));
            return InteractionResult.SUCCESS;
        }
        return buyOneQuartermasterBundle(player, selected);
    }

    private static InteractionResult buyOneQuartermasterBundle(ServerPlayer player, String itemId) {
        YetoniaConfig.ShopEntry entry = config.shop.get(itemId);
        Item item = item(itemId);
        if (entry == null || item == null) {
            player.sendSystemMessage(Component.literal("That quartermaster item is invalid."));
            return InteractionResult.SUCCESS;
        }
        if (!takeBalance(player.serverLevel().getServer(), player.getUUID(), entry.buyPrice())) {
            player.sendSystemMessage(Component.literal("You need Y " + format(entry.buyPrice()) + " Yetonia Coins for " + entry.quantity() + "x " + itemId + "."));
            return InteractionResult.SUCCESS;
        }
        player.getInventory().placeItemBackInInventory(new ItemStack(item, entry.quantity()));
        player.sendSystemMessage(Component.literal("Bought " + entry.quantity() + "x " + itemId + " for Y " + format(entry.buyPrice()) + "."));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult sellOneQuartermasterBundle(ServerPlayer player, String itemId) {
        YetoniaConfig.ShopEntry entry = config.shop.get(itemId);
        Item item = item(itemId);
        if (entry == null || item == null) return InteractionResult.SUCCESS;
        if (!removeItems(player.getInventory(), item, entry.quantity())) {
            player.sendSystemMessage(Component.literal("You need " + entry.quantity() + "x " + itemId + " to sell a bundle."));
            return InteractionResult.SUCCESS;
        }
        addBalance(player.serverLevel().getServer(), player.getUUID(), entry.sellPrice());
        player.sendSystemMessage(Component.literal("Sold " + entry.quantity() + "x " + itemId + " for Y " + format(entry.sellPrice()) + "."));
        return InteractionResult.SUCCESS;
    }

    private static String nextShopItem(UUID playerId) {
        if (config.shop.isEmpty()) return "none";
        List<String> ids = new ArrayList<>(config.shop.keySet());
        String current = QUARTERMASTER_SELECTIONS.get(playerId);
        int index = current == null ? -1 : ids.indexOf(current);
        String next = ids.get((index + 1) % ids.size());
        QUARTERMASTER_SELECTIONS.put(playerId, next);
        return next;
    }

    private static Item item(String itemId) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(itemId)).orElse(null);
    }

    private static boolean removeItems(Inventory inventory, Item item, int amount) {
        int available = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                available += stack.getCount();
                if (available >= amount) break;
            }
        }
        if (available < amount) return false;

        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || stack.getItem() != item) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        return true;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal(message));
        return 0;
    }

    private static String format(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }
}
