package org.lts.voicecaptions;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lts.voicecaptions.command.VoiceCaptionsCommands;
import org.lts.voicecaptions.plasmo.VoiceCaptionsAddon;
import org.lts.voicecaptions.render.CaptionRenderer;

public final class VoiceCaptionsClient implements ClientModInitializer {
    public static final String MOD_ID = "voicecaptions";
    public static final ModLog LOGGER = ModLog.root();
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

    public static KeyMapping toggleKey;

    @Override
    public void onInitializeClient() {
        VoiceCaptionsConfig.load();
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.voicecaptions.toggle", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(VoiceCaptionsClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> VoiceCaptionsManager.getInstance().shutdown());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "captions"), CaptionRenderer::render);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                VoiceCaptionsCommands.register(dispatcher));

        if (FabricLoader.getInstance().isModLoaded("plasmovoice")) {
            VoiceCaptionsAddon.install();
        } else {
            LOGGER.warn("Plasmo Voice is not loaded; captions stay idle");
        }
        LOGGER.ready();
    }

    private static void tick(Minecraft minecraft) {
        while (toggleKey.consumeClick()) {
            toggle(minecraft);
        }
        VoiceCaptionsManager.getInstance().tick(minecraft);
    }

    public static void toggle(Minecraft minecraft) {
        VoiceCaptionsConfig config = VoiceCaptionsConfig.currentCopy();
        config.enabled = !config.enabled;
        VoiceCaptionsConfig.save(config);
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable(
                    config.enabled ? "voicecaptions.toggle.on" : "voicecaptions.toggle.off")
                    .withStyle(config.enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
    }
}
