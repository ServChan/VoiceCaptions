package org.lts.voicecaptions.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lts.voicecaptions.VoiceCaptionsClient;
import org.lts.voicecaptions.VoiceCaptionsConfig;
import org.lts.voicecaptions.VoiceCaptionsManager;
import org.lts.voicecaptions.engine.EngineCatalog;
import org.lts.voicecaptions.gui.VoiceCaptionsConfigScreen;
import org.lts.voicecaptions.stt.TranscriptionClient;

public final class VoiceCaptionsCommands {
    private VoiceCaptionsCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("voicecaptions");
        root.executes(context -> {
            Minecraft client = context.getSource().getClient();
            client.execute(() -> client.setScreenAndShow(new VoiceCaptionsConfigScreen(null)));
            return 1;
        });
        root.then(ClientCommands.literal("toggle").executes(context -> {
            Minecraft client = context.getSource().getClient();
            client.execute(() -> VoiceCaptionsClient.toggle(client));
            return 1;
        }));
        root.then(ClientCommands.literal("check").executes(context -> {
            check(context.getSource().getClient());
            return 1;
        }));
        root.then(ClientCommands.literal("engine")
                .executes(context -> engineStatus(context.getSource().getClient()))
                .then(ClientCommands.literal("status").executes(context -> engineStatus(context.getSource().getClient())))
                .then(ClientCommands.literal("install").executes(context -> install(context.getSource().getClient())))
                .then(ClientCommands.literal("cancel").executes(context -> {
                    VoiceCaptionsManager.getInstance().cancelInstall();
                    return engineStatus(context.getSource().getClient());
                })));
        root.then(ClientCommands.literal("test")
                .executes(context -> test(context.getSource(),
                        Component.translatable("voicecaptions.test.sample").getString()))
                .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                        .executes(context -> test(context.getSource(),
                                StringArgumentType.getString(context, "text")))));
        dispatcher.register(root);
    }

    public static void check(Minecraft client) {
        if (client.player != null) {
            client.player.sendSystemMessage(prefix().append(
                    Component.translatable("voicecaptions.check.started", VoiceCaptionsConfig.current().endpoint)
                            .withStyle(ChatFormatting.GRAY)));
        }
        VoiceCaptionsManager.getInstance().probe(result -> client.execute(() -> {
            if (client.player == null) {
                return;
            }
            Component body;
            if (result instanceof TranscriptionClient.Success success) {
                body = Component.translatable("voicecaptions.check.ok", VoiceCaptionsManager.backendName(success), success.latencyMs())
                        .withStyle(ChatFormatting.GREEN);
            } else {
                body = VoiceCaptionsManager.failureText((TranscriptionClient.Failure) result).copy()
                        .withStyle(ChatFormatting.RED);
            }
            client.player.sendSystemMessage(prefix().append(body));
        }));
    }

    private static int engineStatus(Minecraft client) {
        if (client.player != null) {
            client.player.sendSystemMessage(prefix().append(Component.translatable("voicecaptions.engine.now",
                    VoiceCaptionsManager.getInstance().engineSummary()).withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int install(Minecraft client) {
        VoiceCaptionsConfig config = VoiceCaptionsConfig.current();
        EngineCatalog.Model model = EngineCatalog.Model.byId(config.gpuModel);
        VoiceCaptionsManager manager = VoiceCaptionsManager.getInstance();
        Component message;
        if (!EngineCatalog.isSupportedPlatform()) {
            message = Component.translatable("voicecaptions.error.unsupported").withStyle(ChatFormatting.RED);
        } else if (manager.gpuInstalled(model)) {
            message = Component.translatable("voicecaptions.engine.already_installed",
                    Component.translatable(model.translationKey())).withStyle(ChatFormatting.GREEN);
        } else if (manager.installEngine(model)) {
            message = Component.translatable("voicecaptions.engine.install_started",
                    Component.translatable(model.translationKey())).withStyle(ChatFormatting.GRAY);
        } else {
            message = Component.translatable("voicecaptions.engine.install_running").withStyle(ChatFormatting.GRAY);
        }
        if (client.player != null) {
            client.player.sendSystemMessage(prefix().append(message));
        }
        return 1;
    }

    private static int test(FabricClientCommandSource source, String text) {
        return showTest(source.getClient(), text);
    }

    public static int showTest(Minecraft client, String text) {
        AbstractClientPlayer self = client.player;
        if (self == null || client.level == null) {
            return 0;
        }
        AbstractClientPlayer target = self;
        double best = Double.MAX_VALUE;
        double limit = (double) VoiceCaptionsConfig.current().maxDistance * VoiceCaptionsConfig.current().maxDistance;
        for (AbstractClientPlayer player : client.level.players()) {
            if (player == self) {
                continue;
            }
            double distance = player.distanceToSqr(self);
            if (distance < best && distance <= limit) {
                best = distance;
                target = player;
            }
        }
        VoiceCaptionsManager.getInstance().showTest(target.getUUID(), target.getGameProfile().name(), text);
        Component feedback = target == self
                ? Component.translatable("voicecaptions.test.self")
                : Component.translatable("voicecaptions.test.player", target.getGameProfile().name());
        self.sendSystemMessage(prefix().append(feedback.copy().withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static MutableComponent prefix() {
        return Component.translatable("voicecaptions.notice.prefix").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" "));
    }
}
