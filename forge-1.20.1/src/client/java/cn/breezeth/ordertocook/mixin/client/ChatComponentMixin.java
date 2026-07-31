package cn.breezeth.ordertocook.mixin.client;

import cn.breezeth.ordertocook.api.client.ChatOrderClientApi;
import cn.breezeth.ordertocook.config.ConfigManager;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 捕获绕过原生消息事件、直接写入 Minecraft 聊天界面的模组消息。 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = {
            "addMessage(Lnet/minecraft/network/chat/Component;)V",
            "m_93785_(Lnet/minecraft/network/chat/Component;)V"
    }, at = @At("HEAD"), remap = false)
    private void orderToCook$captureDirectMessage(Component message, CallbackInfo ci) {
        if (ConfigManager.get().chatOrderCaptureChatHud) {
            ChatOrderClientApi.submitMessage("chat_hud", message.getString());
        }
    }
}
