package com.licht_meilleur.blue_student.weapon;

public class WeaponSpec {

    public enum Type {
        PROJECTILE,
        HITSCAN
    }

    public final Type type;

    public final double range;
    public final int cooldownTicks;
    public final float damage;
    public final float projectileSpeed;
    public final float spreadRad;
    public final int pellets;

    // ★追加
    public final float knockback;
    public final boolean bypassIFrames;

    /** ★距離管理（ここがショットガン等に重要） */
    public final double preferredMinRange; // これより遠いなら詰める
    public final double preferredMaxRange; // これより近いなら下がる

    // WeaponSpec に追加
    public final int magSize;              // 例: 30, 100, 50, 5
    public final int reloadTicks;          // リロードモーション継続(=撃てない時間)
    public final int reloadStartAmmo;      // これ以下なら“安全なら”リロード開始（例: 0 or 5）
    public final double panicRange;
    public final boolean infiniteAmmo;

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
            boolean infiniteAmmo

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

    }

    public static WeaponSpec projectile(
            double range, int cooldownTicks, float damage,
            float projectileSpeed, float spreadRad, int pellets,
            float knockback, boolean bypassIFrames,
            double preferredMinRange, double preferredMaxRange,
            int magSize,
            int reloadTicks,
            int reloadStartAmmo,
            double panicRange,
            boolean infiniteAmmo
    ) {
        return new WeaponSpec(Type.PROJECTILE, range, cooldownTicks, damage,
                projectileSpeed, spreadRad, pellets, knockback, bypassIFrames,preferredMinRange,preferredMaxRange, magSize, reloadTicks, reloadStartAmmo, panicRange,infiniteAmmo);
    }
    public static WeaponSpec hitscan(
            double range, int cooldownTicks, float damage,
            float projectileSpeed, float spreadRad, int pellets,
            float knockback, boolean bypassIFrames,
            double preferredMinRange, double preferredMaxRange,
            int magSize,
            int reloadTicks,
            int reloadStartAmmo,
            double panicRange,
            boolean infiniteAmmo
    ) {
        return new WeaponSpec(Type.HITSCAN, range, cooldownTicks, damage,
                projectileSpeed, spreadRad, pellets, knockback, bypassIFrames,preferredMinRange,preferredMaxRange, magSize, reloadTicks, reloadStartAmmo, panicRange,infiniteAmmo);
    }
}
