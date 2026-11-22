package za.co.neroland.neropower.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
//? if >=1.21.9 {
import net.minecraft.client.particle.SingleQuadParticle;
//?} else {
/*import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
*///?}
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

import za.co.neroland.nerotech.api.MachineSettings;

/**
 * NeroPower's five sprite-set particles, one class tuned per {@link Kind}: the sprite follows the age, the
 * motion is a slow rise with a little spread, and alpha fades out over the life. Nothing is created while
 * NeroTech's {@code renderAnimationsEnabled} is off.
 */
//? if >=1.21.9 {
public class PowerParticle extends SingleQuadParticle {
//?} else {
/*public class PowerParticle extends TextureSheetParticle {
*///?}

    /** Lifetime (ticks), size, rise per tick, spread, alpha, and whether it is full-bright. */
    public enum Kind {
        STEAM_PUFF(16, 26, 0.18F, 0.30F, 0.035F, 0.010F, 0.80F, false),
        STEAM_PLUME(30, 48, 0.28F, 0.45F, 0.060F, 0.012F, 0.75F, false),
        QUENCH_VAPOUR(24, 36, 0.22F, 0.34F, 0.030F, 0.020F, 0.85F, false),
        POOL_BUBBLE(8, 14, 0.06F, 0.10F, 0.050F, 0.004F, 0.90F, false),
        RAD_MOTE(26, 34, 0.05F, 0.08F, 0.008F, 0.004F, 1.00F, true);

        final int minLife;
        final int maxLife;
        final float minSize;
        final float maxSize;
        final float rise;
        final float spread;
        final float alpha;
        final boolean emissive;

        Kind(int minLife, int maxLife, float minSize, float maxSize, float rise, float spread, float alpha,
                boolean emissive) {
            this.minLife = minLife;
            this.maxLife = maxLife;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.rise = rise;
            this.spread = spread;
            this.alpha = alpha;
            this.emissive = emissive;
        }
    }

    private final SpriteSet sprites;
    private final Kind kind;

    protected PowerParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            SpriteSet sprites, Kind kind) {
        //? if >=1.21.9 {
        super(level, x, y, z, sprites.first());
        //?} else {
        /*super(level, x, y, z);
        *///?}
        this.sprites = sprites;
        this.kind = kind;
        this.xd = xd + (this.random.nextFloat() - 0.5F) * kind.spread;
        this.yd = yd + kind.rise;
        this.zd = zd + (this.random.nextFloat() - 0.5F) * kind.spread;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 0.96F;
        this.lifetime = kind.minLife + this.random.nextInt(kind.maxLife - kind.minLife + 1);
        this.quadSize = kind.minSize + this.random.nextFloat() * (kind.maxSize - kind.minSize);
        this.setAlpha(kind.alpha);
        this.setSpriteFromAge(sprites);
    }

    //? if >=1.21.9 {
    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }
    //?} else {
    /*@Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
    *///?}

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        float t = this.age / (float) Math.max(1, this.lifetime);
        this.setAlpha(Math.max(0.0F, this.kind.alpha * (1.0F - t * t)));
        this.setSpriteFromAge(this.sprites);
    }

    //? if >=26.1 {
    @Override
    protected int getLightCoords(float partialTick) {
        return this.kind.emissive ? 0xF000F0 : super.getLightCoords(partialTick);
    }
    //?} else {
    /*@Override
    public int getLightColor(float partialTick) {
        return this.kind.emissive ? 0xF000F0 : super.getLightColor(partialTick);
    }
    *///?}

    /** One provider per type, created by each loader with the type's sprite set. */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Kind kind;

        public Provider(SpriteSet sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        @org.jetbrains.annotations.Nullable
        //? if >=1.21.9 {
        public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd, RandomSource random) {
        //?} else {
        /*public Particle createParticle(SimpleParticleType options, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd) {
        *///?}
            if (!MachineSettings.renderAnimationsEnabled()) {
                return null;
            }
            return new PowerParticle(level, x, y, z, xd, yd, zd, this.sprites, this.kind);
        }
    }
}
