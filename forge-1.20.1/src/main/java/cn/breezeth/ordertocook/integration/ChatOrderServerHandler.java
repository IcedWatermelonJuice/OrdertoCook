package cn.breezeth.ordertocook.integration;

import cn.breezeth.ordertocook.OrderToCookMod;
import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import cn.breezeth.ordertocook.config.ConfigManager;
import cn.breezeth.ordertocook.core.OrderGenerator;
import cn.breezeth.ordertocook.core.RestaurantRegistry;
import cn.breezeth.ordertocook.registry.ModSounds;
import cn.breezeth.ordertocook.util.DataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** 校验客户端请求，并委托现有订单逻辑在服务端权威生成订单。 */
public final class ChatOrderServerHandler {
    private static final String CHAT_ORDER_MARKER = "ordertocook.chat_order";

    private ChatOrderServerHandler() {}

    public static void handle(ServerPlayer player, String customerName,
                              boolean deliveryRequested, int menuIndex) {
        boolean devMode = ConfigManager.isDevModeEnabled();
        if (devMode) OrderToCookMod.LOGGER.info(
                "[ChatOrder/Dev] 服务端收到请求：player=\"{}\", customerName=\"{}\", deliveryRequested={}, menuIndex={}, playerPos={}, dimension={}",
                player.getGameProfile().getName(), customerName == null || customerName.isBlank() ? "<random>" : customerName,
                deliveryRequested, menuIndex, player.blockPosition().toShortString(),
                player.serverLevel().dimension().location());
        if (!ConfigManager.get().chatOrderEnabled) {
            if (devMode) OrderToCookMod.LOGGER.info("[ChatOrder/Dev] 拒绝请求：弹幕点单功能未启用");
            return;
        }
        String normalizedCustomerName = customerName == null || customerName.isBlank() ? null : customerName.trim();
        OrderMachineBlockEntity machine = findNearestOwnedMachine(player);
        if (machine == null) {
            if (devMode) OrderToCookMod.LOGGER.info(
                    "[ChatOrder/Dev] 拒绝请求：玩家 \"{}\" 当前维度内没有在线、激活且归其所有的订单机",
                    player.getGameProfile().getName());
            return;
        }
        ServerLevel world = player.serverLevel();
        BlockPos machinePos = machine.getBlockPos();
        List<Item> fullMenu = OrderMachineBlockEntity.getBoundBoardMenuFoods(world, machinePos);
        if (devMode) OrderToCookMod.LOGGER.info(
                "[ChatOrder/Dev] 已选择订单机：machinePos={}, restaurantLevel={}, menuSize={}",
                machinePos.toShortString(), machine.getRestaurantLevel(), fullMenu.size());
        List<Item> orderMenu = fullMenu;
        if (menuIndex >= 0) {
            if (menuIndex >= fullMenu.size()) {
                if (devMode) OrderToCookMod.LOGGER.info(
                        "[ChatOrder/Dev] 拒绝请求：menuIndex={} 超出菜单范围（menuSize={}）", menuIndex, fullMenu.size());
                return;
            }
            orderMenu = List.of(fullMenu.get(menuIndex));
        } else if (menuIndex < -1) {
            if (devMode) OrderToCookMod.LOGGER.info("[ChatOrder/Dev] 拒绝请求：非法 menuIndex={}", menuIndex);
            return;
        }
        OrderToCookMod.LOGGER.info(
                "[ChatOrder] 服务器确认生成订单：player=\"{}\", customerName=\"{}\", deliveryRequested={}, menuIndex={}, machinePos={}, menuSize={}",
                player.getGameProfile().getName(), normalizedCustomerName == null ? "<random>" : normalizedCustomerName, deliveryRequested,
                menuIndex, machinePos.toShortString(), fullMenu.size());
        ItemStack order = OrderGenerator.generateRandomOrder(
                world, machinePos, machine.getRestaurantLevel(), orderMenu, normalizedCustomerName,
                deliveryRequested ? Boolean.TRUE : null);
        markAsChatOrder(order);
        int targetSlot = insertOrder(world, machine, order);
        if (targetSlot >= 0) {
            world.playSound(null, machinePos, ModSounds.ORDER_REFRESH.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        if (devMode) {
            if (targetSlot >= 0) OrderToCookMod.LOGGER.info(
                    "[ChatOrder/Dev] 订单写入完成：machinePos={}, targetSlot={}", machinePos.toShortString(), targetSlot);
            else OrderToCookMod.LOGGER.info(
                    "[ChatOrder/Dev] 放弃生成结果：所有已解锁槽位均为弹幕订单，没有可安全替换的槽位");
        }
    }

    /** 位置只从已认证的发送玩家读取，不信任数据包中的位置数据。 */
    private static OrderMachineBlockEntity findNearestOwnedMachine(ServerPlayer player) {
        ServerLevel world = player.serverLevel();
        String dimension = world.dimension().location().toString();
        String owner = player.getGameProfile().getName();
        OrderMachineBlockEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (OrderMachineBlockEntity.RestaurantStats stats : RestaurantRegistry.allOnline()) {
            if (!dimension.equals(stats.dimension()) || !owner.equalsIgnoreCase(stats.owner())) continue;
            BlockPos pos = BlockPos.of(stats.posLong());
            if (!(world.getBlockEntity(pos) instanceof OrderMachineBlockEntity candidate) || !candidate.isActive()) continue;
            double distance = pos.distSqr(player.blockPosition());
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static void markAsChatOrder(ItemStack order) {
        CompoundTag nbt = DataCompat.copy(order);
        if (nbt == null) nbt = new CompoundTag();
        nbt.putBoolean(CHAT_ORDER_MARKER, true);
        DataCompat.set(order, nbt);
    }

    /** 优先使用空槽位；没有空槽位时，只允许替换普通自动生成的订单。 */
    private static int insertOrder(ServerLevel world, OrderMachineBlockEntity machine, ItemStack order) {
        int targetSlot = -1;
        for (int slot = 0; slot < unlockedSlots(machine.getRestaurantLevel()); slot++) {
            ItemStack current = machine.getItems().get(slot);
            if (current.isEmpty()) {
                targetSlot = slot;
                break;
            }
            CompoundTag nbt = DataCompat.copy(current);
            if (targetSlot < 0 && (nbt == null || !nbt.getBoolean(CHAT_ORDER_MARKER))) targetSlot = slot;
        }
        if (targetSlot < 0) return -1;
        machine.getItems().set(targetSlot, order);
        machine.setChanged();
        world.sendBlockUpdated(machine.getBlockPos(), machine.getBlockState(), machine.getBlockState(), Block.UPDATE_CLIENTS);
        return targetSlot;
    }

    private static int unlockedSlots(int level) {
        return switch (level) {
            case 0 -> 1;
            case 1 -> 2;
            case 2, 3 -> 3;
            case 4 -> 4;
            default -> 5;
        };
    }
}
