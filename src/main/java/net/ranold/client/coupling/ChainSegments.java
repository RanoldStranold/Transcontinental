package net.ranold.client.coupling;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ChainSegments {

    public record Segment(Vec3 from, Vec3 to, double vFrom, double vTo) {
    }

    private final List<Segment> segments = new ArrayList<>(2);

    public void clear() {
        this.segments.clear();
    }

    public void add(final Vec3 from, final Vec3 to, final double vFrom, final double vTo) {
        this.segments.add(new Segment(from, to, vFrom, vTo));
    }

    public boolean isEmpty() {
        return this.segments.isEmpty();
    }

    public List<Segment> snapshot() {
        return List.copyOf(this.segments);
    }
}
