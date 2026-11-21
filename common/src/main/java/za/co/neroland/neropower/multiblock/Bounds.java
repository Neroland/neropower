package za.co.neroland.neropower.multiblock;

/** An inclusive axis-aligned box of block positions. */
public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** The box spanning two corners in any order. */
    public static Bounds of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Bounds(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY
                && z >= this.minZ && z <= this.maxZ;
    }

    /** Positions in the box. */
    public long volume() {
        return (long) (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);
    }

    /** How many of the three coordinates sit on a face of the box (0 interior, 1 face, 2 edge, 3 corner). */
    public int faces(int x, int y, int z) {
        int n = 0;
        if (x == this.minX || x == this.maxX) {
            n++;
        }
        if (y == this.minY || y == this.maxY) {
            n++;
        }
        if (z == this.minZ || z == this.maxZ) {
            n++;
        }
        return n;
    }
}
