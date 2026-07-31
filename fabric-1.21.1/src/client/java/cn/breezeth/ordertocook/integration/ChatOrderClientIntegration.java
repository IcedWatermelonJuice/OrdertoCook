package cn.breezeth.ordertocook.integration;

import cn.breezeth.ordertocook.api.client.ChatOrderClientApi;
import cn.breezeth.ordertocook.config.ConfigManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

/** 将 Fabric 原生消息事件接入弹幕点单公共解析器。 */
public final class ChatOrderClientIntegration {
    private ChatOrderClientIntegration() {}

    public static void register() {
        ChatOrderClientApi.initialize();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && ConfigManager.get().chatOrderCaptureNativeMessages) {
                ChatOrderClientApi.submitMessage("fabric_game", extractMatchableMessage(message));
            }
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (!ConfigManager.get().chatOrderCaptureNativeMessages) return;
            String whisperBody = extractIncomingWhisper(message);
            if (whisperBody != null) ChatOrderClientApi.submitMessage("vanilla_whisper", whisperBody);
        });
    }

    private static String extractMatchableMessage(Text message) {
        String whisperBody = extractIncomingWhisper(message);
        return whisperBody == null ? message.getString() : whisperBody;
    }

    /** 不处理公共玩家聊天；这里只接受原版收到私聊时使用的翻译键。 */
    private static String extractIncomingWhisper(Text message) {
        if (!(message.getContent() instanceof TranslatableTextContent translatable)
                || !"commands.message.display.incoming".equals(translatable.getKey())) return null;
        Object[] args = translatable.getArgs();
        return args.length >= 2 ? textArgument(args[args.length - 1]) : null;
    }

    private static String textArgument(Object argument) {
        return argument instanceof Text text ? text.getString() : String.valueOf(argument);
    }
}
