package com.example.horror;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Mod("horrorpanel")
public class HorrorPanel {

    private static final List<Task> TASKS = new ArrayList<>();

    public HorrorPanel() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("horror")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> showMenu(ctx.getSource()))
                .then(Commands.literal("fly").executes(ctx -> run(ctx.getSource(), this::fly)))
                .then(Commands.literal("jumpscare").executes(ctx -> run(ctx.getSource(), this::jumpscare)))
                .then(Commands.literal("dark").executes(ctx -> run(ctx.getSource(), this::darkness)))
                .then(Commands.literal("whispers").executes(ctx -> run(ctx.getSource(), this::whispers)))
                .then(Commands.literal("chase").executes(ctx -> run(ctx.getSource(), this::chase)))
        );
    }

    private int showMenu(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("§4§l=== لوحة الرعب ==="), false);
        src.sendSuccess(() -> button("🪶 طيران ثم سقوط", "/horror fly", ChatFormatting.AQUA), false);
        src.sendSuccess(() -> button("💀 جمبسكير", "/horror jumpscare", ChatFormatting.WHITE), false);
        src.sendSuccess(() -> button("🌑 ظلام", "/horror dark", ChatFormatting.DARK_GRAY), false);
        src.sendSuccess(() -> button("👂 همسات مخيفة", "/horror whispers", ChatFormatting.DARK_AQUA), false);
        src.sendSuccess(() -> button("🧟 مطاردة", "/horror chase", ChatFormatting.DARK_GREEN), false);
        return 1;
    }

    private Component button(String label, String command, ChatFormatting color) {
        return Component.literal("[" + label + "]").withStyle(
            Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
        );
    }

    private int run(CommandSourceStack src, Consumer<ServerPlayer> action) {
        ServerPlayer host = src.getPlayer();
        if (host == null) return 0;

        List<ServerPlayer> targets = src.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p != host)
                .toList();

        if (targets.isEmpty()) {
            host.sendSystemMessage(Component.literal("§cما فيه أحد غيرك في العالم"));
            return 0;
        }

        targets.forEach(action);
        return 1;
    }

    // يطير لفوق ثم يطيح لتحت
    private void fly(ServerPlayer p) {
        p.setDeltaMovement(0, 1.8, 0);
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
        play(p, SoundEvents.ENDERMAN_TELEPORT, 0.5f);

        later(35, () -> {
            p.setDeltaMovement(0, -2.5, 0);
            p.connection.send(new ClientboundSetEntityMotionPacket(p));
            play(p, SoundEvents.GHAST_SCREAM, 0.6f);
        });
    }

    // شيء واقف قدامه بوجهه مع صرخة وعمى
    private void jumpscare(ServerPlayer p) {
        title(p, "§4§lBOO!");
        play(p, SoundEvents.GHAST_SCREAM, 0.5f);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));

        ServerLevel lvl = (ServerLevel) p.getLevel();
        Vec3 pos = p.position().add(p.getLookAngle().scale(2));
        Zombie z = EntityType.ZOMBIE.create(lvl);
        if (z == null) return;

        z.moveTo(pos.x, p.getY(), pos.z, 0, 0);
        z.setNoAi(true);
        z.setCustomName(Component.literal("§4§lأنت ما تشوفني"));
        z.setCustomNameVisible(true);
        z.lookAt(EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
        lvl.addFreshEntity(z);

        later(60, z::discard);
    }

    // ظلام وصوت كهف
    private void darkness(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300, 1));
        play(p, SoundEvents.AMBIENT_CAVE, 0.5f);
    }

    // أصوات عشوائية متتالية
    private void whispers(ServerPlayer p) {
        var sounds = new net.minecraft.sounds.SoundEvent[]{
                SoundEvents.AMBIENT_CAVE,
                SoundEvents.ENDERMAN_STARE,
                SoundEvents.WARDEN_HEARTBEAT,
                SoundEvents.PHANTOM_AMBIENT
        };
        for (int i = 0; i < 8; i++) {
            later(i * 40, () -> {
                var s = sounds[ThreadLocalRandom.current().nextInt(sounds.length)];
                float pitch = 0.4f + ThreadLocalRandom.current().nextFloat() * 0.6f;
                play(p, s, pitch);
            });
        }
    }

    // زومبي ورا اللاعب يلاحقه
    private void chase(ServerPlayer p) {
        title(p, "§c§lاركض!");

        ServerLevel lvl = (ServerLevel) p.getLevel();
        Vec3 pos = p.position().subtract(p.getLookAngle().scale(6));
        Zombie z = EntityType.ZOMBIE.create(lvl);
        if (z == null) return;

        z.moveTo(pos.x, p.getY(), pos.z, 0, 0);
        z.setCustomName(Component.literal("§2§lيطاردك"));
        z.setCustomNameVisible(true);
        z.setTarget(p);
        lvl.addFreshEntity(z);

        later(200, z::discard);
    }

    private void play(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float pitch) {
        ServerLevel lvl = (ServerLevel) p.getLevel();
        lvl.playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.AMBIENT, 1f, pitch);
    }

    private void title(ServerPlayer p, String text) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(text)));
    }

    // جدولة تنفيذ بعد عدد من التكات (20 تك = ثانية)
    private void later(int ticks, Runnable r) {
        TASKS.add(new Task(ticks, r));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            t.ticks--;
            if (t.ticks <= 0) {
                t.run.run();
                it.remove();
            }
        }
    }

    private static class Task {
        int ticks;
        final Runnable run;

        Task(int ticks, Runnable run) {
            this.ticks = ticks;
            this.run = run;
        }
    }
}
