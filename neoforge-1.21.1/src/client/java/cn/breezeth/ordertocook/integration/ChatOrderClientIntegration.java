package cn.breezeth.ordertocook.integration;

import cn.breezeth.ordertocook.api.client.ChatOrderClientApi;
import cn.breezeth.ordertocook.config.ConfigManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 将 NeoForge 原生消息事件接入弹幕点单公共解析器。 */
public final class ChatOrderClientIntegration {
    private ChatOrderClientIntegration() {}

    public static void register() {
        ChatOrderClientApi.initialize();
        NeoForge.EVENT_BUS.addListener(ChatOrderClientIntegration::onSystemChat);
        NeoForge.EVENT_BUS.addListener(ChatOrderClientIntegration::onPlayerChat);
    }

    private static void onSystemChat(ClientChatReceivedEvent.System event) {
        if (!event.isOverlay() && ConfigManager.get().chatOrderCaptureNativeMessages) {
            ChatOrderClientApi.submitMessage("neoforge_system", extractMatchableMessage(event.getMessage()));
        }
    }

    private static void onPlayerChat(ClientChatReceivedEvent.Player event) {
        if (!ConfigManager.get().chatOrderCaptureNativeMessages) return;
        String whisperBody = extractIncomingWhisper(event.getMessage());
        if (whisperBody != null) ChatOrderClientApi.submitMessage("vanilla_whisper", whisperBody);
    }

    private static String extractMatchableMessage(Component message) {
        String whisperBody = extractIncomingWhisper(message);
        return whisperBody == null ? message.getString() : whisperBody;
    }

    /** 不处理公共玩家聊天；这里只接受原版收到私聊时使用的翻译键。 */
    private static String extractIncomingWhisper(Component message) {
        if (!(message.getContents() instanceof TranslatableContents translatable)
                || !"commands.message.display.incoming".equals(translatable.getKey())) return null;
        Object[] args = translatable.getArgs();
        return args.length >= 2 ? textArgument(args[args.length - 1]) : null;
    }

    private static String textArgument(Object argument) {
        return argument instanceof Component component ? component.getString() : String.valueOf(argument);
    }
}
