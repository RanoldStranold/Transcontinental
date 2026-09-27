package net.ranold.client.coupling;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.phys.Vec3;
import net.ranold.coupling.CoupledMinecart;
import net.ranold.coupling.MinecartCoupling;
import net.ranold.coupling.MinecartLinks;

import java.util.List;

public final class ChainRenderer {

    private ChainRenderer() {
    }

    private static final RenderType RENDER_TYPE =
            RenderTypes.entityCutout(Identifier.withDefaultNamespace("textures/block/iron_chain.png"));

    private static final double HALF_WIDTH = 1.5D / 16.0D;

    private static final double ATTACH_HEIGHT = 0.4375D;

    private static final float FIRST_PLANE_U = 0.0F;

    private static final float SECOND_PLANE_U = 3.0F / 16.0F;

    private static final float PLANE_WIDTH_U = 3.0F / 16.0F;

    public static void extract(final AbstractMinecart cart, final MinecartRenderState state, final float partialTicks) {
        final ChainSegments segments = ((ChainRenderState) state).transcontinental$chains();
        segments.clear();

        final CoupledMinecart coupled = (CoupledMinecart) cart;
        Vec3 self = null;

        for (int slot = 0; slot < MinecartLinks.SLOTS; slot++) {
            final int partnerId = coupled.transcontinental$syncedPartner(slot);

            if (partnerId == MinecartCoupling.NO_PARTNER
                    || !(cart.level().getEntity(partnerId) instanceof AbstractMinecart partner)) {
                continue;
            }

            if (self == null) {
                self = renderPosition(cart, partialTicks);
            }

            final Vec3 other = renderPosition(partner, partialTicks);
            final Vec3 ownEnd = attachPoint(cart, self, other, coupled.transcontinental$syncedEnd(slot), partialTicks);
            final Vec3 partnerEnd = attachPoint(partner, other, self, partnerEnd(partner, cart), partialTicks);
            final Vec3 middle = ownEnd.add(partnerEnd).scale(0.5D);
            final double half = ownEnd.distanceTo(partnerEnd) * 0.5D;
            final double start = cart.getId() < partner.getId() ? 0.0D : half * 2.0D;

            segments.add(ownEnd.subtract(self), middle.subtract(self), start, half);
        }
    }

    public static void submit(final MinecartRenderState state, final PoseStack poseStack,
                              final SubmitNodeCollector collector) {
        final ChainSegments segments = ((ChainRenderState) state).transcontinental$chains();

        if (segments.isEmpty()) {
            return;
        }

        final List<ChainSegments.Segment> snapshot = segments.snapshot();
        final int light = state.lightCoords;

        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, consumer) -> {
            for (final ChainSegments.Segment segment : snapshot) {
                drawChain(pose, consumer, segment, light);
            }
        });
    }

    private static Vec3 renderPosition(final AbstractMinecart cart, final float partialTicks) {
        if (cart.getBehavior() instanceof NewMinecartBehavior behavior && behavior.cartHasPosRotLerp()) {
            return behavior.getCartLerpPosition(partialTicks);
        }

        return cart.getPosition(partialTicks);
    }

    private static int partnerEnd(final AbstractMinecart partner, final AbstractMinecart cart) {
        final CoupledMinecart coupled = (CoupledMinecart) partner;

        for (int slot = 0; slot < MinecartLinks.SLOTS; slot++) {
            if (coupled.transcontinental$syncedPartner(slot) == cart.getId()) {
                return coupled.transcontinental$syncedEnd(slot);
            }
        }

        return 0;
    }

    private static Vec3 attachPoint(final AbstractMinecart cart, final Vec3 position, final Vec3 toward,
                                    final int end, final float partialTicks) {
        final float yRotDegrees;
        final float xRotDegrees;

        if (cart.getBehavior() instanceof NewMinecartBehavior behavior && behavior.cartHasPosRotLerp()) {
            yRotDegrees = behavior.getCartLerpYRot(partialTicks);
            xRotDegrees = behavior.getCartLerpXRot(partialTicks);
        } else {
            yRotDegrees = cart.getYRot();
            xRotDegrees = cart.getXRot();
        }

        return attachPoint(position, toward, end, yRotDegrees, xRotDegrees);
    }

    private static Vec3 attachPoint(final Vec3 position, final Vec3 toward, final int end,
                                    final float yRotDegrees, final float xRotDegrees) {
        final double yRot = Math.toRadians(yRotDegrees);
        final double xRot = Math.toRadians(xRotDegrees);
        final Vec3 axis = MinecartCoupling.modelAxis(yRotDegrees, xRotDegrees);
        final Vec3 up = new Vec3(Math.sin(xRot) * Math.cos(yRot), Math.cos(xRot), -Math.sin(xRot) * Math.sin(yRot));
        final double side = end != 0 ? end : axis.dot(toward.subtract(position)) < 0.0D ? -1.0D : 1.0D;

        return position.add(up.scale(ATTACH_HEIGHT)).add(axis.scale(side * MinecartCoupling.HALF_LENGTH));
    }

    private static void drawChain(final PoseStack.Pose pose, final VertexConsumer consumer,
                                  final ChainSegments.Segment segment, final int light) {
        final Vec3 span = segment.to().subtract(segment.from());
        final double length = span.length();
        final double vRange = segment.vTo() - segment.vFrom();

        if (length < 1.0E-4D || Math.abs(vRange) < 1.0E-6D) {
            return;
        }

        final Vec3 axis = span.scale(Math.signum(vRange) / length);
        final Vec3 reference = Math.abs(axis.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        final Vec3 across = axis.cross(reference).normalize();
        final Vec3 normal = axis.cross(across);
        final Vec3 firstSide = across.add(normal).normalize().scale(HALF_WIDTH);
        final Vec3 secondSide = across.subtract(normal).normalize().scale(HALF_WIDTH);

        double cursor = Math.min(segment.vFrom(), segment.vTo());
        final double limit = Math.max(segment.vFrom(), segment.vTo());

        while (cursor < limit - 1.0E-6D) {
            final double tile = Math.floor(cursor);
            final double next = Math.min(tile + 1.0D, limit);
            final Vec3 start = segment.from().add(span.scale((cursor - segment.vFrom()) / vRange));
            final Vec3 end = segment.from().add(span.scale((next - segment.vFrom()) / vRange));
            final float vStart = (float) (cursor - tile);
            final float vEnd = (float) (next - tile);

            quad(pose, consumer, start, end, firstSide, FIRST_PLANE_U, vStart, vEnd, light);
            quad(pose, consumer, start, end, secondSide, SECOND_PLANE_U, vStart, vEnd, light);
            cursor = next;
        }
    }

    private static void quad(final PoseStack.Pose pose, final VertexConsumer consumer, final Vec3 start,
                             final Vec3 end, final Vec3 side, final float u, final float vStart, final float vEnd,
                             final int light) {
        vertex(pose, consumer, start.subtract(side), u, vStart, light);
        vertex(pose, consumer, start.add(side), u + PLANE_WIDTH_U, vStart, light);
        vertex(pose, consumer, end.add(side), u + PLANE_WIDTH_U, vEnd, light);
        vertex(pose, consumer, end.subtract(side), u, vEnd, light);
    }

    private static void vertex(final PoseStack.Pose pose, final VertexConsumer consumer, final Vec3 position,
                               final float u, final float v, final int light) {
        consumer.addVertex(pose, (float) position.x, (float) position.y, (float) position.z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
