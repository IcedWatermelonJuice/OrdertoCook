package cn.breezeth.ordertocook.command;

import cn.breezeth.ordertocook.api.client.ChatOrderClientApi;
import cn.breezeth.ordertocook.config.ConfigManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.text.Text;

/**
 * 纯客户端命令（/chatorder ...）。
 * 根名与服务端 /ordertocook 不同，不会覆盖客户端补全树中的服务端子命令。
 */
public final class ModClientCommands {
    private ModClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("chatorder")
                    .executes(context -> {
                        context.getSource().sendError(Text.literal("用法: /chatorder enable | disable | danmaku <msg>"));
                        return 0;
                    })
                    .then(ClientCommandManager.literal("enable")
                            .executes(context -> {
                                ConfigManager.setChatOrderEnabled(true);
                                context.getSource().sendFeedback(Text.literal("弹幕点单已启用，配置已保存"));
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("disable")
                            .executes(context -> {
                                ConfigManager.setChatOrderEnabled(false);
                                context.getSource().sendFeedback(Text.literal("弹幕点单已禁用，配置已保存"));
                                return 1;
                            }))
                    .then(ClientCommandManager.literal("danmaku")
                            .then(ClientCommandManager.argument("msg", StringArgumentType.greedyString())
                                    .executes(context -> ChatOrderClientApi.submitMessage(
                                            "client_command", StringArgumentType.getString(context, "msg")) ? 1 : 0))));
        });
    }
}
