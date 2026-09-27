package net.ranold.coupling;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class CouplingInteraction {

    private CouplingInteraction() {
    }

    private static final long SELECTION_TICKS = 600L;

    private static final Map<Player, Selection> SELECTIONS = new WeakHashMap<>();

    private record Selection(UUID cart, long expiresAt) {
    }

    public static InteractionResult onUseEntity(final Player player, final Level level, final InteractionHand hand,
                                                final Entity entity, final @Nullable EntityHitResult hit) {
        if (!(entity instanceof AbstractMinecart cart) || player.isSpectator()) {
            return InteractionResult.PASS;
        }

        final ItemStack stack = player.getItemInHand(hand);

        if (stack.is(Items.IRON_CHAIN)) {
            if (level instanceof ServerLevel serverLevel) {
                useChain(player, serverLevel, cart, stack);
            }

            return InteractionResult.SUCCESS;
        }

        if (stack.is(Items.SHEARS) && isCoupled(cart)) {
            if (level instanceof ServerLevel serverLevel) {
                MinecartCoupling.unlinkAll(cart, !player.hasInfiniteMaterials());
                stack.hurtAndBreak(1, player, hand);
                play(serverLevel, cart, SoundEvents.SHEARS_SNIP);
            }

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private static boolean isCoupled(final AbstractMinecart cart) {
        if (!cart.level().isClientSide()) {
            return !MinecartCoupling.links(cart).isEmpty();
        }

        final CoupledMinecart coupled = (CoupledMinecart) cart;

        for (int slot = 0; slot < MinecartLinks.SLOTS; slot++) {
            if (coupled.transcontinental$syncedPartner(slot) != MinecartCoupling.NO_PARTNER) {
                return true;
            }
        }

        return false;
    }

    private static void useChain(final Player player, final ServerLevel level, final AbstractMinecart cart,
                                 final ItemStack stack) {
        final AbstractMinecart first = selected(player, level);

        if (first == null) {
            if (MinecartCoupling.links(cart).freeSlot() < 0) {
                message(player, "full");
                return;
            }

            SELECTIONS.put(player, new Selection(cart.getUUID(), level.getGameTime() + SELECTION_TICKS));
            play(level, cart, SoundEvents.CHAIN_HIT);
            message(player, "selected");
            return;
        }

        SELECTIONS.remove(player);

        if (first == cart) {
            message(player, "cancelled");
            return;
        }

        if (MinecartCoupling.isLinked(first, cart)) {
            message(player, "already");
            return;
        }

        if (MinecartCoupling.links(first).freeSlot() < 0 || MinecartCoupling.links(cart).freeSlot() < 0) {
            message(player, "full");
            return;
        }

        if (!MinecartCoupling.canLink(first, cart)) {
            message(player, "loop");
            return;
        }

        if (!MinecartCoupling.isEndFree(first, MinecartCoupling.facingEnd(first, cart))
                || !MinecartCoupling.isEndFree(cart, MinecartCoupling.facingEnd(cart, first))) {
            message(player, "end_taken");
            return;
        }

        if (MinecartCoupling.gap(first, cart) > MinecartCoupling.LINK_GAP) {
            message(player, "too_far");
            return;
        }

        MinecartCoupling.link(first, cart);
        stack.consume(1, player);
        play(level, cart, SoundEvents.CHAIN_PLACE);
    }

    private static @Nullable AbstractMinecart selected(final Player player, final ServerLevel level) {
        final Selection selection = SELECTIONS.get(player);

        if (selection == null) {
            return null;
        }

        if (level.getGameTime() > selection.expiresAt()
                || !(level.getEntity(selection.cart()) instanceof AbstractMinecart cart)
                || cart.isRemoved()) {
            SELECTIONS.remove(player);
            return null;
        }

        return cart;
    }

    private static void message(final Player player, final String key) {
        player.sendOverlayMessage(Component.translatable("message.transcontinental.coupling." + key));
    }

    private static void play(final ServerLevel level, final AbstractMinecart cart, final SoundEvent sound) {
        level.playSound(null, cart.getX(), cart.getY(), cart.getZ(), sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }
}
