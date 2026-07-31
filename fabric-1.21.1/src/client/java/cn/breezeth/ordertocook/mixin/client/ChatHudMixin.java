package cn.breezeth.ordertocook.mixin.client;

import cn.breezeth.ordertocook.api.client.ChatOrderClientApi;
import cn.breezeth.ordertocook.config.ConfigManager;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 捕获绕过原生消息事件、直接写入 Minecraft 聊天界面的模组消息。 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void orderToCook$captureDirectMessage(Text message, CallbackInfo ci) {
        if (ConfigManager.get().chatOrderCaptureChatHud) {
            ChatOrderClientApi.submitMessage("chat_hud", message.getString());
        }
    }
}
