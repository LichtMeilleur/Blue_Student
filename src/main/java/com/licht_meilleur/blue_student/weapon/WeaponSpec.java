package com.licht_meilleur.blue_student.weapon;

public class WeaponSpec {

    public enum Type {
        PROJECTILE,
        HITSCAN
    }

    public enum FxType {
        BULLET,     // 普通の弾
        SHOTGUN,    // 散弾
        RAILGUN_HYPER,
        RAILGUN     // 太ビーム
    }

    // ★追加：マズル種別（誤字防止）
    public enum MuzzleLocator {
        MUZZLE("muzzle"),

        SUB_MUZZLE("sub_muzzle"),           // ホシノ用
        LEFT_SUB_MUZZLE("left_sub_muzzle"), // アリス用
        RIGHT_SUB_MUZZLE("right_sub_muzzle");

        private final String boneName;

        MuzzleLocator(String boneName) {
            this.boneName = boneName;
        }

        public String boneName() {
            return boneName;
        }
    }

    public final Type type;

    public final double range;
    public final int cooldownTicks;
    public final float damage;
    public final float projectileSpeed;
    public final float spreadRad;
    public final int pellets;

    public final float knockback;
    public final boolean bypassIFrames;

    public final double preferredMinRange;
    public final double preferredMaxRange;

    public final int magSize;
    public final int reloadTicks;
    public final int reloadStartAmmo;
    public final double panicRange;
    public final boolean infiniteAmmo;

    public final FxType fxType;
    public final float fxWidth;

    public final int animShotHoldTicks;

    // ★追加：マズル指定
    public final MuzzleLocator muzzleLocator;

    public WeaponSpec(
            Type type,
            double range,
            int cooldownTicks,
            float damage,
            float projectileSpeed,
            float spreadRad,
            int pellets,
            float knockback,
            boolean bypassIFrames,
            double preferredMinRange,
            double preferredMaxRange,
            int magSize,
            int reloadTicks,
            int reloadStartAmmo,
            double panicRange,
            boolean infiniteAmmo,
            FxType fxType,
            float fxWidth,
            int animShotHoldTicks,
            MuzzleLocator muzzleLocator
    ) {
        this.type = type;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        this.damage = damage;
        this.projectileSpeed = projectileSpeed;
        this.spreadRad = spreadRad;
        this.pellets = pellets;

        this.knockback = knockback;
        this.bypassIFrames = bypassIFrames;

        this.preferredMinRange = preferredMinRange;
        this.preferredMaxRange = preferredMaxRange;

        this.magSize = magSize;
        this.reloadTicks = reloadTicks;
        this.reloadStartAmmo = reloadStartAmmo;
        this.panicRange = panicRange;
        this.infiniteAmmo = infiniteAmmo;

        this.fxType = fxType;
        this.fxWidth = fxWidth;

        this.animShotHoldTicks = animShotHoldTicks;
        this.muzzleLocator = (muzzleLocator != null) ? muzzleLocator : MuzzleLocator.MUZZLE;
    }

    public static WeaponSpec projectile(
            double range, int cooldownTicks, float damage,
            float projectileSpeed, float spreadRad, int pellets,
            float knockback, boolean bypassIFrames,
            double preferredMinRange, double preferredMaxRange,
            int magSize, int reloadTicks, int reloadStartAmmo,
            double panicRange, boolean infiniteAmmo,
            FxType fxType, float fxWidth,
            int animShotHoldTicks,
            MuzzleLocator muzzleLocator
    ) {
        return new WeaponSpec(
                Type.PROJECTILE, range, cooldownTicks, damage,
                projectileSpeed, spreadRad, pellets,
                knockback, bypassIFrames,
                preferredMinRange, preferredMaxRange,
                magSize, reloadTicks, reloadStartAmmo,
                panicRange, infiniteAmmo,
                fxType, fxWidth,
                animShotHoldTicks,
                muzzleLocator
        );
    }

    public static WeaponSpec hitscan(
            double range, int cooldownTicks, float damage,
            float projectileSpeed, float spreadRad, int pellets,
            float knockback, boolean bypassIFrames,
            double preferredMinRange, double preferredMaxRange,
            int magSize, int reloadTicks, int reloadStartAmmo,
            double panicRange, boolean infiniteAmmo,
            FxType fxType, float fxWidth,
            int animShotHoldTicks,
            MuzzleLocator muzzleLocator
    ) {
        return new WeaponSpec(
                Type.HITSCAN, range, cooldownTicks, damage,
                projectileSpeed, spreadRad, pellets,
                knockback, bypassIFrames,
                preferredMinRange, preferredMaxRange,
                magSize, reloadTicks, reloadStartAmmo,
                panicRange, infiniteAmmo,
                fxType, fxWidth,
                animShotHoldTicks,
                muzzleLocator
        );
    }
}