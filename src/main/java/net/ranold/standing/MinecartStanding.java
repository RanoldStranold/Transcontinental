package net.ranold.standing;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.ranold.rail.RailSpeeds;

import java.util.ArrayList;
import java.util.List;

public final class MinecartStanding {

    private MinecartStanding() {
    }

    public static final double FLOOR_BOTTOM = 0.0625D;

    public static final double FLOOR_TOP = 0.1875D;

    public static final double RIM_TOP = 0.6875D;

    public static final double INNER_HALF_LENGTH = 0.5D;

    public static final double OUTER_HALF_LENGTH = 0.625D;

    public static final double INNER_HALF_WIDTH = 0.375D;

    public static final double OUTER_HALF_WIDTH = 0.5D;

    private static final double MAX_PITCH = 1.0D;

    private static final double ALIGN_TOLERANCE = 1.0D;

    private static final double REACH = 0.25D;

    private static final double EPSILON = 1.0E-4D;

    public static boolean isStandable(final Entity entity) {
        return entity instanceof StandableMinecart standable && standable.transcontinental$standable();
    }

    public static boolean isStationary(final AbstractMinecart cart) {
        return (cart.isOnRails() || cart.onGround())
                && cart.getDeltaMovement().horizontalDistanceSqr()
                < RailSpeeds.MOVING_THRESHOLD * RailSpeeds.MOVING_THRESHOLD
                && Math.abs(cart.getXRot()) < MAX_PITCH;
    }

    public static boolean isAboveFloor(final AbstractMinecart cart, final Entity entity) {
        return entity.getBoundingBox().minY >= cart.getY() + FLOOR_TOP - EPSILON;
    }

    public static List<VoxelShape> withStandingCollisions(final Entity source,
                                                      final AABB area,
                                                      final List<VoxelShape> original) {
        final List<AABB> boxes = standingBoxes(source, area);

        if (boxes.isEmpty()) {
            return original;
        }

        final List<VoxelShape> shapes = new ArrayList<>(original);

        for (final AABB box : boxes) {
            shapes.add(Shapes.create(box));
        }

        return shapes;
    }

    public static boolean noStandingCollision(final Entity source, final AABB area) {
        for (final AABB box : standingBoxes(source, area)) {
            if (box.intersects(area)) {
                return false;
            }
        }

        return true;
    }

    private static List<AABB> standingBoxes(final Entity source, final AABB area) {
        if (!(source instanceof Player player) || player.isSpectator()) {
            return List.of();
        }

        final List<AABB> boxes = new ArrayList<>();

        for (final AbstractMinecart cart : player.level().getEntitiesOfClass(
                AbstractMinecart.class,
                area.inflate(REACH),
                cart -> isStandable(cart)
                        && !player.isPassengerOfSameVehicle(cart)
                        && isAboveFloor(cart, player))) {
            addCartBoxes(cart, boxes);
        }

        return boxes;
    }

    private static void addCartBoxes(final AbstractMinecart cart, final List<AABB> boxes) {
        final double yaw = Math.abs(Mth.wrapDegrees(cart.getYRot())) % 180.0D;
        final boolean alongX = yaw < ALIGN_TOLERANCE || yaw > 180.0D - ALIGN_TOLERANCE;
        final boolean alongZ = Math.abs(yaw - 90.0D) < ALIGN_TOLERANCE;

        if (!alongX && !alongZ) {
            boxes.add(cart.getBoundingBox());
            return;
        }

        boxes.add(piece(cart, alongX, -INNER_HALF_LENGTH, INNER_HALF_LENGTH,
                -INNER_HALF_WIDTH, INNER_HALF_WIDTH, FLOOR_BOTTOM, FLOOR_TOP));
        boxes.add(piece(cart, alongX, INNER_HALF_LENGTH, OUTER_HALF_LENGTH,
                -OUTER_HALF_WIDTH, OUTER_HALF_WIDTH, FLOOR_BOTTOM, RIM_TOP));
        boxes.add(piece(cart, alongX, -OUTER_HALF_LENGTH, -INNER_HALF_LENGTH,
                -OUTER_HALF_WIDTH, OUTER_HALF_WIDTH, FLOOR_BOTTOM, RIM_TOP));
        boxes.add(piece(cart, alongX, -INNER_HALF_LENGTH, INNER_HALF_LENGTH,
                INNER_HALF_WIDTH, OUTER_HALF_WIDTH, FLOOR_BOTTOM, RIM_TOP));
        boxes.add(piece(cart, alongX, -INNER_HALF_LENGTH, INNER_HALF_LENGTH,
                -OUTER_HALF_WIDTH, -INNER_HALF_WIDTH, FLOOR_BOTTOM, RIM_TOP));
    }

    private static AABB piece(final AbstractMinecart cart, final boolean alongX,
                                    final double lengthMin, final double lengthMax,
                                    final double widthMin, final double widthMax,
                                    final double yMin, final double yMax) {
        final double x = cart.getX();
        final double y = cart.getY();
        final double z = cart.getZ();

        return alongX
                ? new AABB(x + lengthMin, y + yMin, z + widthMin, x + lengthMax, y + yMax, z + widthMax)
                : new AABB(x + widthMin, y + yMin, z + lengthMin, x + widthMax, y + yMax, z + lengthMax);
    }
}
