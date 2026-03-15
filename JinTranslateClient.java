package com.vjup.jintranslate;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

public class JinTranslateClient implements ClientModInitializer {

    private static volatile boolean sending = false;
    private static volatile boolean awaitingSelection = false;

    private static final Map<Integer, String> storedMessages = new HashMap<>();
    private static int msgCounter = 0;

    private static final List<String> candidates = new ArrayList<>();

    @Override
    public void onInitializeClient() {

        // 受信: メッセージに[訳]ボタンを追加
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            String raw = message.getString();
            if (raw == null || raw.isBlank()) return true;

            String body = getBody(raw);
            if (body.isBlank() || TranslationService.isJapanese(body)) return true;

            int id = msgCounter++;
            storedMessages.put(id, body);
            if (storedMessages.size() > 200) {
                storedMessages.remove(msgCounter - 200);
            }

            Text translateBtn = Text.literal(" §b[訳]").styled(s -> s
                    .withClickEvent(new ClickEvent.RunCommand("/jtr " + id))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("クリックで翻訳"))));

            MutableText full = Text.empty().append(message).append(translateBtn);

            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> mc.inGameHud.getChatHud().addMessage(full));

            return false;
        });

        // 送信: 日本語を検知して候補を表示
        ClientSendMessageEvents.ALLOW_CHAT.register(msg -> {
            if (sending) return true;
            if (msg == null || msg.isBlank()) return true;

            // 候補選択中
            if (awaitingSelection) {
                if (msg.equals("1") || msg.equals("2") || msg.equals("3")) {
                    int n = Integer.parseInt(msg) - 1;
                    if (n < candidates.size()) {
                        String chosen = candidates.get(n);
                        awaitingSelection = false;
                        candidates.clear();

                        MinecraftClient mc = MinecraftClient.getInstance();
                        mc.execute(() -> {
                            sending = true;
                            if (mc.getNetworkHandler() != null) {
                                mc.getNetworkHandler().sendChatMessage(chosen);
                            }
                            sending = false;
                        });
                    }
                } else if (msg.equals("0")) {
                    awaitingSelection = false;
                    candidates.clear();
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.execute(() -> mc.inGameHud.getChatHud().addMessage(Text.literal("§7[翻訳キャンセル]")));
                }
                return false;
            }

            if (!TranslationService.isJapanese(msg)) return true;

            String original = msg;
            TranslationService.getCandidates(original, "ja", "en").thenAccept(results -> {
                if (results == null || results.isEmpty()) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.execute(() -> {
                        mc.inGameHud.getChatHud().addMessage(Text.literal("§c[翻訳失敗]"));
                        sending = true;
                        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatMessage(original);
                        sending = false;
                    });
                    return;
                }

                candidates.clear();
                candidates.addAll(results);
                awaitingSelection = true;

                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    MutableText display = Text.literal("§e翻訳候補 §7(1〜" + candidates.size() + "を入力, 0でキャンセル)");
                    mc.inGameHud.getChatHud().addMessage(display);

                    for (int i = 0; i < candidates.size(); i++) {
                        String c = candidates.get(i);
                        Text line = Text.literal("§f  [" + (i + 1) + "] " + c).styled(s -> s
                                .withClickEvent(new ClickEvent.SuggestCommand(c))
                                .withHoverEvent(new HoverEvent.ShowText(Text.literal("クリックでチャット欄に入力"))));
                        mc.inGameHud.getChatHud().addMessage(line);
                    }
                });
            });

            return false;
        });

        // コマンド登録
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            // /jtr <id> - 受信メッセージを翻訳
            dispatcher.register(literal("jtr")
                    .then(argument("id", integer())
                            .executes(ctx -> {
                                int id = getInteger(ctx, "id");
                                String body = storedMessages.get(id);
                                if (body == null) return 0;

                                TranslationService.translate(body, "en", "ja").thenAccept(result -> {
                                    if (result == null) return;
                                    MinecraftClient mc = MinecraftClient.getInstance();
                                    mc.execute(() -> mc.inGameHud.getChatHud().addMessage(
                                            Text.literal("§7[訳] §f" + result)));
                                });
                                return 1;
                            })));
        });
    }

    private String getBody(String raw) {
        if (!raw.startsWith("<")) return raw;
        int i = raw.indexOf('>');
        if (i == -1 || i + 2 >= raw.length()) return raw;
        return raw.substring(i + 2);
    }

    private String getPrefix(String raw) {
        if (!raw.startsWith("<")) return null;
        int i = raw.indexOf('>');
        return i == -1 ? null : raw.substring(0, i + 1);
    }
}
