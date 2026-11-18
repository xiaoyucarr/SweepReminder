package com.example.sweepreminder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.regex.Pattern;

public final class SweepReminderClient implements ClientModInitializer {
    private static final long INTERVAL_MS = 20L * 60_000L + 10_000L;
    private static final long WARNING_MS = 60_000L;
    private static final long ANNOUNCEMENT_DEBOUNCE_MS = 5_000L;

    // Typical announcements about an upcoming item clear, including the user's example.
    // Exact announcement on this server: "60秒后就要大扫除啦！东西别落了~".
    private static final Pattern SERVER_EXACT_WARNING = Pattern.compile(
            "60\\s*秒\\s*后\\s*就要\\s*大扫除", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPCOMING_CLEAR = Pattern.compile(
            "(?:(?:还(?:有|剩)|剩余|将在|倒计时|距离|再过|将于|约)\\s*(?:60|六十|1\\s*分(?:钟)?)\\s*(?:秒|s|S|分(?:钟)?)?\\s*(?:后|内)?\\s*[^\\n]{0,30}(?:清理|清除|扫地|清空)[^\\n]{0,16}(?:掉落物|掉落物品|物品|实体)?" +
            "|(?:60|六十)\\s*(?:秒|s|S)\\s*(?:后|倒计时)?\\s*[^\\n]{0,30}(?:清理|清除|扫地|清空)" +
            "|(?:清理|清除|扫地|清空)[^\\n]{0,30}(?:60|六十)\\s*(?:秒|s|S)\\s*(?:后)?" +
            "|(?:items|entities|ground items|dropped items)[^\\n]{0,35}(?:clear|remove|delete)[^\\n]{0,20}(?:60 seconds|one minute|1 minute))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FINISHED_CLEAR = Pattern.compile(
            "扫地完成|清理完成|清除完成|已清理(?:地面)?(?:掉落物|掉落物品|物品|实体)|已清除(?:地面)?(?:掉落物|掉落物品|物品|实体)|"
                    + "(?:成功)?清理了.{0,25}(?:掉落物|物品|实体)|(?:items|entities) (?:have been )?(?:cleared|removed)",
            Pattern.CASE_INSENSITIVE);

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("sweepreminder", "general"));
    private static final KeyMapping SYNC = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.sweepreminder.sync", InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY));

    private long nextSweepAt = -1L;
    private long lastWarningDetectedAt = -1L;
    private long lastFinishedDetectedAt = -1L;
    private boolean wasInWorld = false;
    private boolean warnedOneMinute = false;
    private boolean syncedToServer = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean inWorld = client.player != null && client.level != null;
            if (!inWorld) {
                wasInWorld = false;
                return;
            }
            if (!wasInWorld) {
                // Connection time is only a fallback, not the server's real clean time.
                nextSweepAt = System.currentTimeMillis() + INTERVAL_MS;
                warnedOneMinute = false;
                syncedToServer = false;
                wasInWorld = true;
                client.player.displayClientMessage(Component.literal(
                        "§e[扫地提醒] 右下角开始估算计时；检测到服务器60秒公告会自动校准。"), false);
            }
            while (SYNC.consumeClick()) {
                // R is pressed just AFTER cleanup has completed.
                syncAfterSweep(client, "手动同步");
            }
            long now = System.currentTimeMillis();
            long remaining = nextSweepAt - now;
            if (remaining <= WARNING_MS && remaining > 0 && !warnedOneMinute) {
                warnedOneMinute = true;
                client.player.displayClientMessage(Component.literal(
                        "§6[扫地提醒] 预计还有60秒清理掉落物！"), false);
            }
            if (remaining <= 0) {
                long missedIntervals = Math.max(1L, (now - nextSweepAt) / INTERVAL_MS + 1L);
                nextSweepAt += missedIntervals * INTERVAL_MS;
                warnedOneMinute = false;
                client.player.displayClientMessage(Component.literal(
                        "§c[扫地提醒] 预计扫地时间到！已开始下一轮20分10秒倒计时。"), false);
            }
        });

        // Server system messages and action-bar messages.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onServerMessage(message.getString()));
        // Some servers send announcements through ordinary chat instead.
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                onServerMessage(message.getString()));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("sweepreminder", "timer"),
                (graphics, tickCounter) -> drawHud(graphics));
    }

    private void onServerMessage(String plain) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || plain == null || plain.isBlank()) return;
        long now = System.currentTimeMillis();
        if (FINISHED_CLEAR.matcher(plain).find()) {
            if (lastFinishedDetectedAt < 0 || now - lastFinishedDetectedAt >= ANNOUNCEMENT_DEBOUNCE_MS) {
                lastFinishedDetectedAt = now;
                syncAfterSweep(client, "检测到服务器已扫地");
            }
        } else if (SERVER_EXACT_WARNING.matcher(plain).find() || UPCOMING_CLEAR.matcher(plain).find()) {
            if (lastWarningDetectedAt < 0 || now - lastWarningDetectedAt >= ANNOUNCEMENT_DEBOUNCE_MS) {
                lastWarningDetectedAt = now;
                nextSweepAt = now + WARNING_MS;
                warnedOneMinute = true;
                syncedToServer = true;
                client.player.displayClientMessage(Component.literal(
                        "§c[扫地提醒] 已检测到服务器60秒清理提示！倒计时同步为 01:00。"), false);
            }
        }
    }

    private void syncAfterSweep(Minecraft client, String source) {
        nextSweepAt = System.currentTimeMillis() + INTERVAL_MS;
        warnedOneMinute = false;
        syncedToServer = true;
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "§a[扫地提醒] " + source + "，下一次预计20分10秒后。"), false);
        }
    }

    private void drawHud(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || nextSweepAt < 0) return;
        long seconds = Math.max(0L, (nextSweepAt - System.currentTimeMillis() + 999L) / 1000L);
        String display = String.format("扫地倒计时 %02d:%02d", seconds / 60, seconds % 60);
        int color = seconds <= 60 ? 0xFFFF5555 : seconds <= 300 ? 0xFFFFCC55 : 0xFF55FF88;
        int width = client.font.width(display);
        // Show only the countdown text, transparent, anchored to the bottom-right.
        int x = graphics.guiWidth() - width - 8;
        int y = graphics.guiHeight() - client.font.lineHeight - 8;
        graphics.drawString(client.font, display, x, y, color, true);
    }
}
