package cn.breezeth.ordertocook.command;

import cn.breezeth.ordertocook.api.client.ChatOrderClientApi;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

public final class ModClientCommands {
    private ModClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // The server already owns this root; Brigadier merges our unique client-only child.
            dispatcher.register(ClientCommandManager.literal("ordertocook")
                    .then(ClientCommandManager.literal("danmuku")
                            .then(ClientCommandManager.argument("msg", StringArgumentType.greedyString())
                                    .executes(context -> ChatOrderClientApi.submitMessage(
                                            "client_command", StringArgumentType.getString(context, "msg")) ? 1 : 0))));
            // Disabled due to conflict with server-side commands
            /*
            dispatcher.register(ClientCommandManager.literal("ordertocook")
                    .then(ClientCommandManager.literal("totlemoney")
                            .executes(context -> {
                                boolean sent = ModClientNetworking.sendPrestigeQuery();
                                if (!sent) {
                                    context.getSource().sendError(Text.translatable("command.ordertocook.not_connected"));
                                    return 0;
                                }
                                context.getSource().sendFeedback(Text.translatable("command.ordertocook.prestige_query_sent"));
                                return 1;
                            })));
            */
        });
    }
}
