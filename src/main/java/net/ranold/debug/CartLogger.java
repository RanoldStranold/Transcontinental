package net.ranold.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.ranold.coupling.MinecartCoupling;
import net.ranold.coupling.MinecartLinks;
import org.jspecify.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public final class CartLogger {

    private CartLogger() {
    }

    private static final String HEADER = "tick,dimension,id,uuid,type,x,y,z,vx,vy,vz,yRot,xRot,onRails,onGround,rail,"
            + "partnerA,partnerAUuid,endA,separationA,methodA,"
            + "partnerB,partnerBUuid,endB,separationB,methodB";

    private static @Nullable BufferedWriter writer;

    private static @Nullable Path file;

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("transcontinental")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("cartlog")
                        .then(Commands.literal("start").executes(CartLogger::start))
                        .then(Commands.literal("stop").executes(CartLogger::stop))));
    }

    private static int start(final CommandContext<CommandSourceStack> context) {
        close();

        final String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        final Path target = FabricLoader.getInstance().getGameDir().resolve("transcontinental-cartlog-" + stamp + ".csv");

        try {
            writer = Files.newBufferedWriter(target);
            writer.write(HEADER);
            writer.newLine();
            file = target;
        } catch (final IOException exception) {
            writer = null;
            context.getSource().sendFailure(Component.translatable("commands.transcontinental.cartlog.failed",
                    exception.getMessage()));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable("commands.transcontinental.cartlog.started",
                target.toAbsolutePath().toString()), false);
        return 1;
    }

    private static int stop(final CommandContext<CommandSourceStack> context) {
        if (writer == null || file == null) {
            context.getSource().sendFailure(Component.translatable("commands.transcontinental.cartlog.idle"));
            return 0;
        }

        final String path = file.toAbsolutePath().toString();
        close();
        context.getSource().sendSuccess(() -> Component.translatable("commands.transcontinental.cartlog.stopped",
                path), false);
        return 1;
    }

    public static void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (final IOException ignored) {
            }
        }

        writer = null;
        file = null;
    }

    public static void onServerTick(final MinecraftServer server) {
        if (writer == null) {
            return;
        }

        try {
            for (final ServerLevel level : server.getAllLevels()) {
                for (final AbstractMinecart cart : level.getEntities(EntityTypeTest.forClass(AbstractMinecart.class),
                        cart -> true)) {
                    writer.write(row(level, cart));
                    writer.newLine();
                }
            }

            writer.flush();
        } catch (final IOException exception) {
            close();
        }
    }

    private static String row(final ServerLevel level, final AbstractMinecart cart) {
        final Vec3 velocity = cart.getDeltaMovement();
        final BlockState rail = level.getBlockState(cart.getCurrentBlockPosOrRailBelow());
        final StringBuilder line = new StringBuilder(256);

        line.append(level.getGameTime()).append(',')
                .append(level.dimension().identifier()).append(',')
                .append(cart.getId()).append(',')
                .append(shortId(cart.getUUID())).append(',')
                .append(cart.getType().getDescriptionId()).append(',')
                .append(number(cart.getX())).append(',')
                .append(number(cart.getY())).append(',')
                .append(number(cart.getZ())).append(',')
                .append(number(velocity.x)).append(',')
                .append(number(velocity.y)).append(',')
                .append(number(velocity.z)).append(',')
                .append(number(cart.getYRot())).append(',')
                .append(number(cart.getXRot())).append(',')
                .append(cart.isOnRails()).append(',')
                .append(cart.onGround()).append(',')
                .append(rail.getBlock() instanceof BaseRailBlock railBlock
                        ? rail.getValue(railBlock.getShapeProperty()).getSerializedName()
                        : "none");

        final MinecartLinks links = MinecartCoupling.links(cart);

        for (int slot = 0; slot < MinecartLinks.SLOTS; slot++) {
            final UUID partnerId = links.partner(slot);
            final AbstractMinecart partner = MinecartCoupling.resolve(cart, slot);
            line.append(',');

            if (partnerId == null) {
                line.append(",,,,");
                continue;
            }

            line.append(partner == null ? "missing" : Integer.toString(partner.getId())).append(',')
                    .append(shortId(partnerId)).append(',')
                    .append(links.end(slot)).append(',');

            if (partner == null) {
                line.append(',');
                continue;
            }

            final MinecartCoupling.Separation separation = MinecartCoupling.separation(cart, partner);
            line.append(number(separation.distance())).append(',')
                    .append(separation.path() != null ? "path" : "chord");
        }

        return line.toString();
    }

    private static String shortId(final UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String number(final double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
