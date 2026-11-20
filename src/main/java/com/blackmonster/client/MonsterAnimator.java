package com.blackmonster.client;

import com.blackmonster.entity.BlackMonsterEntity;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Protsedural animatsiyalar (GeckoLib yo'q): idle, walk (bukchaygan yurish), run (to'rt oyoqlab chopish), fight (jang holati),
 * attack (uzun tirnoq supurishi), pounce, tendril lash, frenzy bite, ink eruption, roar, death. Gradus / piksel.
 */
public final class MonsterAnimator {
    public static final class Pose {
        public final Map<String, float[]> rot = new HashMap<>(), pos = new HashMap<>(), scale = new HashMap<>();
        void r(String b, float x, float y, float z) { float[] a = rot.computeIfAbsent(b, k -> new float[3]); a[0] += x; a[1] += y; a[2] += z; }
        void p(String b, float x, float y, float z) { float[] a = pos.computeIfAbsent(b, k -> new float[3]); a[0] += x / 16F; a[1] += y / 16F; a[2] += z / 16F; }
        void s(String b, float x, float y, float z) { float[] a = scale.computeIfAbsent(b, k -> new float[]{1F, 1F, 1F}); a[0] *= x; a[1] *= y; a[2] *= z; }
        /** Yon tomondagi ikkala tentakl (a - yuqori, b - pastki) uchun. */
        void tr(String sd, float x, float y, float z) { r("tendril_a" + sd, x, y, z); r("tendril_b" + sd, x * 0.8F, y, z * 0.8F); }
        void ts(String sd, float x, float y, float z) { s("tendril_a" + sd, x, y, z); s("tendril_b" + sd, x, y, z * 0.85F); }
    }
    private static final float TAU = (float) (Math.PI * 2);
    private static final String[] SIDES = {"_l", "_r"};
    private static float sin(double v) { return (float) Math.sin(v); }
    private static float cos(double v) { return (float) Math.cos(v); }
    private static float hash(int n) { int h = n * 0x27d4eb2d; h ^= h >>> 15; h *= 0x85ebca6b; h ^= h >>> 13; return (h & 0xFFFF) / 32767.5F - 1F; }
    private static float smooth(float x) { x = Mth.clamp(x, 0F, 1F); return x * x * (3 - 2 * x); }

    static float pw(float t, float... kv) {
        if (t <= kv[0]) return kv[1];
        for (int i = 0; i + 3 < kv.length; i += 2) {
            float t0 = kv[i], v0 = kv[i + 1], t1 = kv[i + 2], v1 = kv[i + 3];
            if (t <= t1) { float s = t1 > t0 ? (t - t0) / (t1 - t0) : 1F; s = s * s * (3 - 2 * s); return v0 + (v1 - v0) * s; }
        }
        return kv[kv.length - 1];
    }
    private static float win(float t, float a, float b, float v) { return t > a && t < b ? v : 0F; }

    public static Pose compute(BlackMonsterEntity e, float partial) {
        Pose p = new Pose();
        float age = e.tickCount + partial;
        if (e.deathTime > 0) { death(p, (e.deathTime + partial) / 20F); return p; }
        float limb = e.walkAnimation.position(partial);
        float amount = Mth.clamp(e.walkAnimation.speed(partial) * 1.6F, 0F, 1F);
        int act = e.getClientAction();
        float at = (age - e.getClientActionStart()) / 20F;
        idle(p, age / 20F, Math.max(0.3F, 1F - amount));
        if (e.isAggressive()) fight(p, age / 20F, 1F - amount * 0.7F);
        if (amount > 0.01F) { if (e.isSprinting()) gallop(p, limb, amount); else walk(p, limb, amount); }
        if (act != 0) action(p, act, at, (e.getClientSeq() & 1) == 0 ? 1F : -1F);
        if (e.isEnraged()) { p.r("head", sin(age * 2.1F) * 2F, 0, sin(age * 2.9F) * 2.5F); p.r("jaw", 4F + 3F * Math.abs(sin(age * 1.3F)), 0, 0); }
        return p;
    }

    public static Pose idlePose(float t) { Pose p = new Pose(); idle(p, t, 1F); return p; }

    /** Nafas olish, boshni sekin aylantirib atrofni kuzatish, keskin bosh qiyshayishi, barmoq va tentakllar qimirlashi. */
    static void idle(Pose p, float t, float w) {
        float br = sin(TAU * t / 3F) * w;
        p.r("torso", 2F * br, 0, 0); p.p("hips", 0, 0.4F * br, 0);
        int k = Mth.floor(t / 2.6F); float tilt = Mth.lerp(smooth((t / 2.6F - k) * 6F), hash(k - 1), hash(k));
        p.r("head", 2F * br, 10F * sin(TAU * t / 7F) * w, 8F * tilt * w);
        p.r("jaw", (2F + 2F * sin(TAU * t / 1.6F)) * w, 0, 0);
        for (int i = 0; i < 2; i++) {
            String sd = SIDES[i]; float dir = i == 0 ? 1F : -1F;
            p.r("arm_upper" + sd, 3F * sin(TAU * t / 3F + i) * w, 0, 2F * dir * sin(TAU * t / 4F) * w);
            p.r("hand" + sd, 5F * sin(TAU * t / 2.2F + i * 1.3F) * w, 0, 0);
            p.tr(sd, 8F * sin(TAU * t / 1.7F + i) * w, 0, 6F * sin(TAU * t / 2.3F + i) * w);
        }
        p.r("cloth_f", 3F * sin(TAU * t / 2.5F) * w, 0, 0); p.r("cloth_b", -3F * sin(TAU * t / 2.5F + 1) * w, 0, 0);
    }

    /** Bukchaygan yurish: oyoqlar navbatma-navbat, uzun qo'llar qarama-qarshi tebranadi, tirnoqlar yerga sudraladi. */
    static void walk(Pose p, float limb, float k) {
        float ph = limb * 0.5F, s = sin(ph), c = cos(ph);
        for (int i = 0; i < 2; i++) {
            String sd = SIDES[i]; float dir = i == 0 ? 1F : -1F;
            float ang = 26F * s * dir, lift = Math.max(0F, -c * dir), shin = 35F * lift;
            p.r("thigh" + sd, ang * k, 0, 0); p.r("shin" + sd, shin * k, 0, 0); p.r("foot" + sd, (-ang - shin + 15F * lift) * k, 0, 0);
            p.p("thigh" + sd, 0, 1.5F * lift * k, 0);
            float arm = -22F * s * dir;
            p.r("arm_upper" + sd, -arm * k, 0, 0); p.r("arm_lower" + sd, -12F * Math.max(0F, c * dir) * k, 0, 0);
            p.r("hand" + sd, 10F * s * dir * k, 0, 0);
            p.tr(sd, 12F * sin(2 * ph - 1 + i) * k, 0, 0);
        }
        p.p("hips", 0, 1.0F * cos(2 * ph) * k, 0);
        p.r("torso", 3F * sin(2 * ph) * k, 6F * s * k, 4F * s * k);
        p.r("head", 0, -5F * s * k, -2F * s * k);
        p.r("cloth_f", 6F * s * k, 0, 0); p.r("cloth_b", -6F * s * k, 0, 0);
    }

    /** Yugurish: to'rt oyoqlab "galop" - uzun qo'llar oldinga tashlanib yerga tiraladi, oyoqlar birga itaradi. */
    static void gallop(Pose p, float limb, float k) {
        float ph = limb * 0.42F, front = sin(ph), back = sin(ph + 2.5F);
        p.r("torso", (24F + 6F * sin(2 * ph)) * k, 0, 0);
        p.r("head", -20F * k, 0, 0);
        p.p("hips", 0, (-1.5F + 2.5F * Math.abs(back)) * k, 0);
        for (int i = 0; i < 2; i++) {
            String sd = SIDES[i]; float off = i == 0 ? 0F : 0.35F;
            float f = sin(ph + off), b = sin(ph + 2.5F + off);
            p.r("arm_upper" + sd, (-40F + 38F * f) * k, 0, 0); p.r("arm_lower" + sd, (-15F + 15F * cos(ph + off)) * k, 0, 0);
            p.r("hand" + sd, (-20F * f) * k, 0, 0);
            float lift = Math.max(0F, cos(ph + 2.5F + off));
            p.r("thigh" + sd, 38F * b * k, 0, 0); p.r("shin" + sd, 40F * lift * k, 0, 0); p.r("foot" + sd, (-38F * b - 40F * lift + 20F * lift) * k, 0, 0);
            p.tr(sd, (30F + 10F * sin(2 * ph)) * k, 0, 0);
        }
        p.r("cloth_f", -25F * k, 0, 0); p.r("cloth_b", 30F * k + 8F * sin(2 * ph) * k, 0, 0);
    }

    /** Jang holati: pastroq cho'kkalaydi, qo'llarini keng yoyib tirnoqlarini ko'taradi, og'zini ochib tebranadi. */
    static void fight(Pose p, float t, float w) {
        float sway = sin(TAU * t * 0.5F);
        p.p("hips", 0, -2F * w, 0);
        p.r("torso", 8F * w, 8F * sway * w, 3F * sway * w);
        p.r("head", -10F * w, -6F * sway * w, 0);
        p.r("jaw", (12F + 3F * sin(TAU * t * 3F)) * w, 0, 0);
        for (int i = 0; i < 2; i++) {
            String sd = SIDES[i]; float dir = i == 0 ? 1F : -1F;
            p.r("arm_upper" + sd, (-35F + 5F * sin(TAU * t + i)) * w, 0, 20F * dir * w);
            p.r("arm_lower" + sd, -25F * w, 0, 0); p.r("hand" + sd, (-20F + 8F * sin(TAU * t * 1.5F + i)) * w, 0, 0);
            p.r("thigh" + sd, -12F * w, 0, 4F * dir * w); p.r("shin" + sd, 18F * w, 0, 0); p.r("foot" + sd, -6F * w, 0, 0);
        }
    }

    static void action(Pose p, int id, float t, float side) {
        String a = side > 0 ? "_r" : "_l";
        switch (id) {
            case BlackMonsterEntity.ACT_ATTACK -> { // Uzun tirnoq supurishi: qo'lni yon tomonga ko'tarib, old tomondan keng yoy bilan supuradi
                if (t > 1.0F) return;
                p.r("arm_upper" + a, pw(t, 0, 0, .35F, -60, .5F, -80, 1, 0), side * pw(t, 0, 0, .35F, 30, .5F, -50, 1, 0), side * pw(t, 0, 0, .35F, -75, .5F, 10, .7F, 15, 1, 0));
                p.r("arm_lower" + a, pw(t, 0, 0, .35F, -30, .5F, -5, 1, 0), 0, 0);
                p.r("hand" + a, pw(t, 0, 0, .35F, -30, .5F, 20, 1, 0), 0, 0);
                p.r("torso", pw(t, 0, 0, .35F, -6, .5F, 10, 1, 0), side * pw(t, 0, 0, .35F, 25, .5F, -25, 1, 0), 0);
                p.r("head", 0, side * pw(t, 0, 0, .35F, -15, .5F, 15, 1, 0), 0);
                p.r("jaw", pw(t, 0, 0, .4F, 18, .6F, 18, 1, 0), 0, 0);
            }
            case BlackMonsterEntity.ACT_POUNCE -> { // Sakrab tashlanish: chuqur cho'kkalab, gorizontal uchib, ikki qo'li bilan yerga bosadi
                if (t > 1.6F) return;
                float crouch = pw(t, 0, 0, .45F, 1, .55F, 0, 1.1F, 0, 1.25F, 0.7F, 1.6F, 0);
                float fly = pw(t, .45F, 0, .6F, 1, 1.05F, 1, 1.2F, 0);
                p.p("hips", 0, -6F * crouch, 0);
                p.r("torso", 15F * crouch + 30F * fly, 0, 0); p.r("head", -10F * crouch - 25F * fly, 0, 0);
                p.r("jaw", pw(t, 0, 0, .5F, 30, 1.1F, 30, 1.6F, 0), 0, 0);
                for (int i = 0; i < 2; i++) {
                    String sd = SIDES[i]; float dir = i == 0 ? 1F : -1F;
                    p.r("arm_upper" + sd, 30F * crouch - 110F * fly + pw(t, 1.05F, 0, 1.2F, 60, 1.6F, 0), 0, 10F * dir * fly);
                    p.r("arm_lower" + sd, -10F * fly, 0, 0); p.r("hand" + sd, -30F * fly, 0, 0);
                    p.r("thigh" + sd, -30F * crouch + 45F * fly, 0, 0); p.r("shin" + sd, 45F * crouch - 10F * fly, 0, 0); p.r("foot" + sd, -15F * crouch + 30F * fly, 0, 0);
                }
            }
            case BlackMonsterEntity.ACT_TENDRIL -> { // Tentakl qamchisi: qo'llarni oldinga cho'zadi, tentakllar 6 barobar uzayib qamchilaydi
                if (t > 1.4F) return;
                float reach = pw(t, 0, 0, .3F, 1, 1.0F, 1, 1.4F, 0);
                float len = pw(t, 0, 1, .3F, 1, .45F, 9F, .85F, 9F, 1.1F, 1);
                p.r("torso", 10F * reach, 0, 0); p.r("jaw", 25F * reach, 0, 0);
                for (int i = 0; i < 2; i++) {
                    String sd = SIDES[i]; float dir = i == 0 ? 1F : -1F;
                    p.r("arm_upper" + sd, -85F * reach, 0, 8F * dir * reach); p.r("arm_lower" + sd, -10F * reach, 0, 0); p.r("hand" + sd, -20F * reach, 0, 0);
                    p.ts(sd, 1.3F, len, 1.3F);
                    p.tr(sd, -70F * reach + win(t, .45F, .85F, 8F * sin(40 * t + i)), 0, win(t, .45F, .85F, 10F * sin(33 * t + i)));
                }
            }
            case BlackMonsterEntity.ACT_FRENZY -> { // Tishlash jazavasi: boshini 5 marta oldinga otib tishlaydi, qo'llar bilan ushlab turadi
                if (t > 2.0F) return;
                float env = pw(t, 0, 0, .3F, 1, 1.8F, 1, 2.0F, 0);
                float b = t > .3F && t < 1.8F ? sin(((t - .3F) / .3F) * Mth.PI) : 0F; b *= b;
                p.r("torso", 12F * env, 0, 0);
                p.p("head", 0, -1F * b, -4F * b); p.r("head", 10F * b, 8F * sin(40 * t) * env, 0);
                p.r("jaw", 35F * (1F - b) * env, 0, 0);
                for (int i = 0; i < 2; i++) {
                    String sd = SIDES[i]; float dir = i == 0 ? 1F : -1F;
                    p.r("arm_upper" + sd, -50F * env, 0, 25F * dir * env); p.r("arm_lower" + sd, -30F * env, 0, 0); p.r("hand" + sd, -25F * env, 0, 0);
                }
            }
            case BlackMonsterEntity.ACT_ERUPT -> { // Siyoh otilishi: tik turib, ikki qo'lini yerga uradi, siyoh yer ostidan otiladi
                if (t > 2.0F) return;
                float rise = pw(t, 0, 0, .55F, 1, .7F, 0);
                float slam = pw(t, .55F, 0, .7F, 1, 1.4F, 1, 2.0F, 0);
                p.p("hips", 0, 3F * rise - 4F * slam, 0);
                p.r("torso", -25F * rise + 28F * slam, 0, 0); p.r("head", -15F * rise, 0, 0);
                p.r("jaw", 40F * rise + 25F * slam, 0, 0);
                for (int i = 0; i < 2; i++) {
                    String sd = SIDES[i]; float dir = i == 0 ? 1F : -1F;
                    p.r("arm_upper" + sd, -150F * rise - 45F * slam, 0, 15F * dir * rise); p.r("arm_lower" + sd, -20F * rise, 0, 0); p.r("hand" + sd, 30F * slam, 0, 0);
                    p.r("thigh" + sd, -15F * slam, 0, 0); p.r("shin" + sd, 25F * slam, 0, 0);
                }
            }
            case BlackMonsterEntity.ACT_ROAR -> { // Bo'kirish: to'liq bo'yiga ko'tarilib, qo'llarini keng yoyadi, titraydi
                if (t > 2.0F) return;
                float env = pw(t, 0, 0, .4F, 1, 1.6F, 1, 2.0F, 0);
                float tr = win(t, .4F, 1.6F, 1F);
                p.p("hips", 0, 5F * env, 0);
                p.r("torso", -35F * env, 0, 3F * tr * sin(50 * t)); p.r("head", -25F * env, 0, 4F * tr * sin(37 * t));
                p.r("jaw", 40F * env, 0, 0);
                for (int i = 0; i < 2; i++) {
                    String sd = SIDES[i]; float dir = i == 0 ? 1F : -1F;
                    p.r("arm_upper" + sd, -30F * env, 0, 60F * dir * env); p.r("arm_lower" + sd, -20F * env, 0, 0); p.r("hand" + sd, -35F * env, 0, 0);
                    p.r("thigh" + sd, 10F * env, 0, 0); p.r("shin" + sd, -10F * env, 0, 0);
                    p.tr(sd, -20F * env, 0, 20F * dir * env + 8F * tr * sin(30 * t + i));
                }
            }
            default -> { }
        }
    }

    /** O'lim: tizzalari bukilib oldinga qulaydi, qo'llari shalvirab, jag'i osilib qoladi. */
    static void death(Pose p, float t) {
        for (String sd : SIDES) {
            p.r("thigh" + sd, pw(t, 0, 0, 1, -40), 0, 0); p.r("shin" + sd, pw(t, 0, 0, 1, 80), 0, 0);
            p.r("arm_upper" + sd, pw(t, 0, 0, 1.2F, -40), 0, 0); p.tr(sd, pw(t, 0, 0, 1.2F, 30), 0, 0);
        }
        p.p("hips", 0, pw(t, 0, 0, 1, -10), 0);
        p.r("torso", pw(t, 0, 0, 1.2F, 35), 0, 0); p.r("head", pw(t, 0, 0, 1.2F, 30), 0, win(t, 0, 1.0F, 5F * sin(40 * t)));
        p.r("jaw", pw(t, 0, 0, 1.2F, 30), 0, 0);
    }

    private MonsterAnimator() {}
}
