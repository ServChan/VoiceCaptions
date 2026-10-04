package org.lts.voicecaptions.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.lts.voicecaptions.gui.VoiceCaptionsConfigScreen;

public final class VoiceCaptionsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return VoiceCaptionsConfigScreen::new;
    }
}
