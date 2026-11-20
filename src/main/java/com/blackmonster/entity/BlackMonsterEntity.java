package com.blackmonster.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Black Monster - boss mob. Bukchaygan, uzun qo'lli, moyli qora terili tubsizlik maxluqi.
 * Hujumlar: Long Claw Sweep (melee yoy), Vault Pounce, Tendril Lash, Frenzy Bite, Ink Eruption.
 * Qorong'ida o'zini davolaydi. 50% HP dan pastda - G'azab fazasi (Abyssal Roar).
 */
public class BlackMonsterEntity extends Monster {
    private static final EntityDataAccessor<Boolean> ENRAGED = SynchedEntityData.defineId(BlackMonsterEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(BlackMonsterEntity.class, EntityDataSerializers.INT);
    public static final int ACT_ATTACK = 1, ACT_POUNCE = 2, ACT_TENDRIL = 3, ACT_FRENZY = 4, ACT_ERUPT = 5, ACT_ROAR = 6;
    private int clientAction, clientActionStart, clientSeq;
    private static final UUID RAGE_SPEED_ID = UUID.fromString("0b1ac4a0-55e1-4d1f-8a3c-b1ac4a0e5e01");
    private static final UUID RAGE_DAMAGE_ID = UUID.fromString("0b1ac4a0-55e1-4d1f-8a3c-b1ac4a0e5e02");
    private static final DustParticleOptions INK = new DustParticleOptions(new Vector3f(0.02F, 0.02F, 0.03F), 1.4F);
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.45F, 0.02F, 0.03F), 1.5F);

    private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true).setCreateWorldFog(true);

    private int pounceCooldown = 100, tendrilCooldown = 80, frenzyCooldown = 140, eruptCooldown = 200;
    private int pounceTimer = -1, tendrilTimer = -1, frenzyTimer = -1, eruptTimer = -1, roarTimer = -1, meleeTimer = -1;
    private boolean pinned;
    private LivingEntity meleeTarget;
    private Vec3 pounceDir = Vec3.ZERO;
    private final List<Vec3> eruptSpots = new ArrayList<>();
    private final Set<UUID> hitOnce = new HashSet<>();

    public BlackMonsterEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 320;
        this.setMaxUpStep(1.5F);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 450.0D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void defineSynchedData() { super.defineSynchedData(); this.entityData.define(ENRAGED, false); this.entityData.define(ACTION, 0); }
    public boolean isEnraged() { return this.entityData.get(ENRAGED); }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    private Vec3 fwd() { float y = this.yBodyRot * Mth.DEG_TO_RAD; return new Vec3(-Mth.sin(y), 0, Mth.cos(y)); }
    private Vec3 right() { float y = this.yBodyRot * Mth.DEG_TO_RAD; return new Vec3(-Mth.cos(y), 0, -Mth.sin(y)); }
    private boolean inFront(Entity e, double maxDist, double cosHalfAngle) {
        Vec3 to = e.position().subtract(this.position()); Vec3 h = new Vec3(to.x, 0, to.z);
        double d = h.length();
        return d <= maxDist && (d < 1.0D || h.scale(1.0D / d).dot(fwd()) >= cosHalfAngle);
    }

    // ===== Long Claw Sweep: uzun qo'l bilan oldingi yoy bo'ylab supurish (animatsiya bilan sinxron) =====
    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof LivingEntity living && meleeTimer < 0 && !isBusy()) {
            meleeTarget = living; meleeTimer = 10;
            this.playAction(ACT_ATTACK);
            this.playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 1.5F, 0.6F);
        }
        return true;
    }

    private void tickMelee() {
        if (meleeTimer < 0) return;
        if (--meleeTimer > 0) return;
        meleeTimer = -1;
        LivingEntity main = meleeTarget; meleeTarget = null;
        ServerLevel sl = (ServerLevel) this.level();
        float dmg = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Vec3 f = fwd();
        for (int i = -3; i <= 3; i++) {
            Vec3 d = f.yRot(i * 0.35F).scale(3.2D);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + d.x, getY() + 1.2D, getZ() + d.z, 1, 0, 0, 0, 0);
            sl.sendParticles(INK, getX() + d.x, getY() + 1.2D, getZ() + d.z, 4, 0.2, 0.2, 0.2, 0.02);
        }
        this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(4.0D, 1.5D, 4.0D), e -> e != this && e.isAlive())) {
            if (!inFront(e, 5.0D, 0.34D)) continue;
            boolean hit = e == main ? super.doHurtTarget(e) : e.hurt(this.damageSources().mobAttack(this), dmg * 0.8F);
            if (!hit) continue;
            Vec3 push = e.position().subtract(this.position()).normalize().scale(1.2D).add(right().scale(0.6D));
            e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.4D, push.z)); e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            sl.sendParticles(BLOOD, e.getX(), e.getY(0.6D), e.getZ(), 15, 0.4, 0.4, 0.4, 0.1);
            this.playSound(SoundEvents.RAVAGER_ATTACK, 1.5F, 0.8F);
        }
    }

    private boolean isBusy() { return pounceTimer >= 0 || tendrilTimer >= 0 || frenzyTimer >= 0 || eruptTimer >= 0 || roarTimer >= 0; }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        LivingEntity target = this.getTarget();
        boolean sprint = target != null && target.isAlive() && this.distanceToSqr(target) > 36.0D && !isBusy();
        if (sprint != this.isSprinting()) this.setSprinting(sprint);
        if (!isEnraged() && this.getHealth() <= this.getMaxHealth() * 0.5F) enterRage();
        if (this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth() && this.level().getMaxLocalRawBrightness(this.blockPosition()) < 7) {
            this.heal(2.0F); // tubsizlik terisi: qorong'ida tiklanadi
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1.5D, getZ(), 3, 0.6, 0.8, 0.6, 0.02);
        }

        tickMelee();
        tickPounce(target); tickTendril(target); tickFrenzy(); tickErupt(); tickRoar();
        if (target == null || !target.isAlive()) return;

        int step = isEnraged() ? 2 : 1;
        pounceCooldown -= step; tendrilCooldown -= step; frenzyCooldown -= step; eruptCooldown -= step;
        if (isBusy() || meleeTimer >= 0) return;

        double dist = this.distanceToSqr(target);
        boolean los = this.hasLineOfSight(target);
        if (frenzyCooldown <= 0 && dist < 16.0D) startFrenzy();
        else if (pounceCooldown <= 0 && dist > 36.0D && dist < 400.0D && los && this.onGround()) startPounce();
        else if (tendrilCooldown <= 0 && dist > 16.0D && dist < 144.0D && los) startTendril();
        else if (eruptCooldown <= 0 && dist < 400.0D) startErupt();
    }

    // ===== 1) Vault Pounce: chuqur cho'kkalab, nishonga gorizontal uchib tashlanadi va uni yerga mixlaydi =====
    private void startPounce() {
        pounceTimer = 0; pinned = false;
        this.playAction(ACT_POUNCE);
        this.playSound(SoundEvents.WARDEN_ANGRY, 2.5F, 0.8F);
    }

    private void tickPounce(@Nullable LivingEntity target) {
        if (pounceTimer < 0) return;
        ServerLevel sl = (ServerLevel) this.level();
        this.getNavigation().stop();
        pounceTimer++;
        if (pounceTimer < 10) {
            if (target != null) this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            sl.sendParticles(INK, getX(), getY() + 0.2D, getZ(), 4, 0.8, 0.1, 0.8, 0.02);
            return;
        }
        if (pounceTimer == 10) {
            Vec3 to = target != null ? target.position().subtract(this.position()) : fwd().scale(10.0D);
            Vec3 h = new Vec3(to.x, 0, to.z);
            pounceDir = h.normalize();
            double v = Math.min(2.4D, h.length() / 7.0D + 0.4D);
            this.setDeltaMovement(pounceDir.x * v, 0.42D, pounceDir.z * v); this.hasImpulse = true;
            this.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 1.6F);
            return;
        }
        float yaw = (float) (Mth.atan2(pounceDir.z, pounceDir.x) * Mth.RAD_TO_DEG) - 90.0F;
        this.setYRot(yaw); this.yBodyRot = yaw; this.setYHeadRot(yaw);
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.2D, getZ(), 3, 0.6, 0.5, 0.6, 0.02);
        if (!pinned) {
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(0.8D), e -> e != this && e.isAlive())) {
                pinned = true;
                e.hurt(this.damageSources().mobAttack(this), isEnraged() ? 21.0F : 16.0F);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4)); // yerga mixlangan
                e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                e.setDeltaMovement(0, -0.3D, 0); e.hurtMarked = true;
                sl.sendParticles(BLOOD, e.getX(), e.getY(0.5D), e.getZ(), 25, 0.5, 0.4, 0.5, 0.1);
                this.playSound(SoundEvents.RAVAGER_ATTACK, 2.0F, 0.6F);
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.2D, 1.0D, 0.2D));
            }
        }
        if (pounceTimer > 14 && this.onGround()) {
            this.playSound(SoundEvents.SLIME_BLOCK_FALL, 2.5F, 0.5F);
            sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.2D, getZ(), 40, 1.5, 0.1, 1.5, 0.1);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(2.5D, 1.0D, 2.5D), e -> e != this && e.isAlive() && e.onGround()))
                e.hurt(this.damageSources().mobAttack(this), 6.0F);
            pounceTimer = -1; pounceCooldown = 180;
            return;
        }
        if (pounceTimer > 50) { pounceTimer = -1; pounceCooldown = 180; }
    }

    // ===== 2) Tendril Lash: tentakllar 6 barobar uzayib nishonni o'ziga tortadi =====
    private void startTendril() {
        tendrilTimer = 0;
        this.playAction(ACT_TENDRIL);
        this.playSound(SoundEvents.WARDEN_TENDRIL_CLICKS, 3.0F, 0.6F);
    }

    private void tickTendril(@Nullable LivingEntity target) {
        if (tendrilTimer < 0) return;
        ServerLevel sl = (ServerLevel) this.level();
        this.getNavigation().stop();
        if (target != null) this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        tendrilTimer++;
        if (tendrilTimer == 9 && target != null && target.isAlive() && this.distanceTo(target) < 13.0F && this.hasLineOfSight(target)) {
            Vec3 end = target.position().add(0, target.getBbHeight() * 0.5D, 0);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 start = this.position().add(fwd().scale(0.5D)).add(right().scale(0.22D * s)).add(0, 2.2D, 0), d = end.subtract(start);
                int n = (int) (d.length() * 3);
                for (int i = 0; i <= n; i++) {
                    Vec3 q = start.add(d.scale(i / (double) n));
                    sl.sendParticles(INK, q.x, q.y, q.z, 2, 0.04, 0.04, 0.04, 0);
                    if (i % 3 == 0) sl.sendParticles(ParticleTypes.SQUID_INK, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
            target.hurt(this.damageSources().mobAttack(this), isEnraged() ? 11.0F : 8.0F);
            Vec3 pull = this.position().subtract(target.position()).normalize().scale(1.6D);
            target.setDeltaMovement(pull.x, 0.4D, pull.z); target.hurtMarked = true;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            this.playSound(SoundEvents.SLIME_ATTACK, 2.5F, 0.5F);
        }
        if (tendrilTimer >= 28) { tendrilTimer = -1; tendrilCooldown = 150; }
    }

    // ===== 3) Frenzy Bite: 5 marta tez tishlaydi, har tishlashda o'zini davolaydi (hayot so'rish) =====
    private void startFrenzy() {
        frenzyTimer = 0;
        this.playAction(ACT_FRENZY);
        this.playSound(SoundEvents.WARDEN_ROAR, 2.0F, 1.5F);
    }

    private void tickFrenzy() {
        if (frenzyTimer < 0) return;
        ServerLevel sl = (ServerLevel) this.level();
        this.getNavigation().stop();
        frenzyTimer++;
        if (frenzyTimer >= 9 && frenzyTimer <= 33 && (frenzyTimer - 9) % 6 == 0) {
            Vec3 m = this.position().add(fwd().scale(1.0D)).add(0, 2.4D, 0);
            this.playSound(SoundEvents.EVOKER_FANGS_ATTACK, 2.0F, 0.6F);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(3.0D, 1.5D, 3.0D), e -> e != this && e.isAlive())) {
                if (!inFront(e, 4.0D, 0.3D)) continue;
                e.invulnerableTime = 0;
                if (e.hurt(this.damageSources().mobAttack(this), isEnraged() ? 7.0F : 5.0F)) {
                    e.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, 1));
                    this.heal(3.0F);
                    sl.sendParticles(BLOOD, e.getX(), e.getY(0.7D), e.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
                    sl.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY(0.7D), e.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
                }
            }
            sl.sendParticles(INK, m.x, m.y, m.z, 8, 0.2, 0.2, 0.2, 0.02);
        }
        if (frenzyTimer >= 40) { frenzyTimer = -1; frenzyCooldown = 200; }
    }

    // ===== 4) Ink Eruption: qo'llarini yerga uradi, o'yinchilar ostidan siyoh fontanlari otiladi (ogohlantirishdan keyin) =====
    private void startErupt() {
        eruptTimer = 0; eruptSpots.clear();
        this.playAction(ACT_ERUPT);
        this.playSound(SoundEvents.WARDEN_EMERGE, 2.5F, 0.8F);
    }

    private void tickErupt() {
        if (eruptTimer < 0) return;
        ServerLevel sl = (ServerLevel) this.level();
        this.getNavigation().stop();
        eruptTimer++;
        if (eruptTimer == 14) {
            this.playSound(SoundEvents.GENERIC_EXPLODE, 2.5F, 0.5F);
            BlockState ground = sl.getBlockState(this.blockPosition().below());
            if (ground.isAir()) ground = Blocks.DEEPSLATE.defaultBlockState();
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.2D, getZ(), 60, 2.0, 0.2, 2.0, 0.2);
            for (Player p : sl.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(20.0D), p -> p.isAlive() && !p.isCreative() && !p.isSpectator()))
                eruptSpots.add(p.position());
            for (int i = 0; i < 4; i++) {
                double a = this.random.nextDouble() * Mth.TWO_PI, r = 4.0D + this.random.nextDouble() * 5.0D;
                eruptSpots.add(this.position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            }
        }
        if (eruptTimer > 14 && eruptTimer < 34) { // ogohlantirish: siyoh halqasi - qochishga ulguring!
            for (Vec3 s : eruptSpots) {
                double a = eruptTimer * 0.6D;
                for (int k = 0; k < 3; k++) {
                    double b = a + k * Mth.TWO_PI / 3;
                    sl.sendParticles(ParticleTypes.SQUID_INK, s.x + Math.cos(b) * 1.8D, s.y + 0.1D, s.z + Math.sin(b) * 1.8D, 1, 0, 0, 0, 0);
                }
                if (eruptTimer % 4 == 0) sl.sendParticles(ParticleTypes.SCULK_SOUL, s.x, s.y + 0.2D, s.z, 2, 0.5, 0.05, 0.5, 0.01);
            }
        }
        if (eruptTimer == 34) {
            this.playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 3.0F, 0.6F);
            for (Vec3 s : eruptSpots) {
                for (int y = 0; y < 8; y++) {
                    sl.sendParticles(ParticleTypes.SQUID_INK, s.x, s.y + y * 0.5D, s.z, 10, 0.4, 0.2, 0.4, 0.05);
                    sl.sendParticles(INK, s.x, s.y + y * 0.5D, s.z, 6, 0.5, 0.2, 0.5, 0.02);
                }
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, s.x, s.y + 1.0D, s.z, 12, 0.6, 1.0, 0.6, 0.03);
                sl.sendParticles(ParticleTypes.SCULK_SOUL, s.x, s.y + 1.5D, s.z, 8, 0.5, 1.2, 0.5, 0.05);
                AABB box = new AABB(s.x - 2.2D, s.y - 0.5D, s.z - 2.2D, s.x + 2.2D, s.y + 3.0D, s.z + 2.2D);
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive())) {
                    e.invulnerableTime = 0;
                    e.hurt(this.damageSources().indirectMagic(this, this), isEnraged() ? 14.0F : 10.0F);
                    e.setDeltaMovement(e.getDeltaMovement().x, 1.1D, e.getDeltaMovement().z); e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                }
            }
        }
        if (eruptTimer >= 50) { eruptTimer = -1; eruptCooldown = 300; eruptSpots.clear(); }
    }

    // ===== G'azab fazasi (HP < 50%): Abyssal Roar =====
    private void enterRage() {
        this.entityData.set(ENRAGED, true);
        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(RAGE_SPEED_ID) == null)
            speed.addPermanentModifier(new AttributeModifier(RAGE_SPEED_ID, "Black Monster rage speed", 0.25D, AttributeModifier.Operation.MULTIPLY_BASE));
        var dmg = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && dmg.getModifier(RAGE_DAMAGE_ID) == null)
            dmg.addPermanentModifier(new AttributeModifier(RAGE_DAMAGE_ID, "Black Monster rage damage", 0.35D, AttributeModifier.Operation.MULTIPLY_BASE));
        if (!isBusy() && meleeTimer < 0) { roarTimer = 0; this.playAction(ACT_ROAR); }
        this.playSound(SoundEvents.ENDER_DRAGON_GROWL, 4.0F, 0.6F);
    }

    private void tickRoar() {
        if (roarTimer < 0) return;
        ServerLevel sl = (ServerLevel) this.level();
        this.getNavigation().stop();
        roarTimer++;
        if (roarTimer == 8) {
            this.playSound(SoundEvents.WARDEN_ROAR, 4.0F, 0.7F);
            for (int i = 0; i < 48; i++) {
                double a = i * Mth.TWO_PI / 48;
                for (double r = 2.0D; r <= 10.0D; r += 2.0D)
                    sl.sendParticles(ParticleTypes.SQUID_INK, getX() + Math.cos(a) * r, getY() + 0.3D, getZ() + Math.sin(a) * r, 1, 0, 0.05, 0, 0.02);
            }
            sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 2.0D, getZ(), 1, 0, 0, 0, 0);
            for (Player p : sl.getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(24.0D), p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));
                if (p.distanceTo(this) < 10.0F) {
                    Vec3 d = p.position().subtract(this.position()).normalize().scale(1.2D);
                    p.setDeltaMovement(d.x, 0.5D, d.z); p.hurtMarked = true;
                }
            }
        }
        if (roarTimer >= 40) roarTimer = -1;
    }

    // ===== Mijoz effektlari: iyak tentakllari va tirnoqlardan siyoh tomchilari, og'izdan so'lak, g'azab aurasi =====
    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide() && !this.isDeadOrDying()) {
            Vec3 f = fwd(), r = right();
            if (this.random.nextInt(isEnraged() ? 2 : 4) == 0) {
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 t = this.position().add(f.scale(0.45D)).add(r.scale(0.22D * s)).add(0, 2.0D, 0);
                    this.level().addParticle(INK, t.x, t.y, t.z, 0, -0.05D, 0);
                    Vec3 h = this.position().add(f.scale(0.95D)).add(r.scale(0.95D * s)).add(0, 0.2D, 0);
                    if (this.random.nextInt(3) == 0) this.level().addParticle(INK, h.x, h.y, h.z, 0, -0.03D, 0);
                }
            }
            if (this.random.nextInt(8) == 0) {
                Vec3 m = this.position().add(f.scale(0.55D)).add(0, 2.35D, 0);
                this.level().addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, m.x, m.y, m.z, 0, 0, 0);
            }
            if (isEnraged()) {
                this.level().addParticle(ParticleTypes.SCULK_SOUL, getRandomX(1.0D), getRandomY(), getRandomZ(1.0D), 0, 0.03D, 0);
                if (this.random.nextInt(3) == 0) this.level().addParticle(ParticleTypes.LARGE_SMOKE, getRandomX(1.0D), getRandomY(), getRandomZ(1.0D), 0, 0.02D, 0);
            }
            if (this.isSprinting() && this.onGround() && this.random.nextInt(2) == 0)
                this.level().addParticle(ParticleTypes.SQUID_INK, getRandomX(0.8D), getY() + 0.1D, getRandomZ(0.8D), 0, 0.02D, 0);
        }
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (!this.level().isClientSide() && this.deathTime % 4 == 0) {
            ServerLevel sl = (ServerLevel) this.level();
            sl.sendParticles(ParticleTypes.SQUID_INK, getRandomX(1.2D), getRandomY(), getRandomZ(1.2D), 6, 0.3, 0.3, 0.3, 0.03);
            sl.sendParticles(ParticleTypes.SCULK_SOUL, getRandomX(1.2D), getRandomY(), getRandomZ(1.2D), 2, 0.3, 0.3, 0.3, 0.03);
        }
        if (this.deathTime >= 45 && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    // ===== Boss bar va saqlash =====
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); this.bossEvent.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); this.bossEvent.removePlayer(player); }
    @Override public void setCustomName(@Nullable Component name) { super.setCustomName(name); this.bossEvent.setName(this.getDisplayName()); }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("Enraged", isEnraged()); }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(ENRAGED, tag.getBoolean("Enraged"));
        if (this.hasCustomName()) this.bossEvent.setName(this.getDisplayName());
        if (isEnraged()) this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
    }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canBeAffected(MobEffectInstance effect) {
        var t = effect.getEffect();
        return t != MobEffects.WITHER && t != MobEffects.POISON && t != MobEffects.DARKNESS && t != MobEffects.BLINDNESS && super.canBeAffected(effect);
    }

    // ===== Ovozlar (faqat vanilla) =====
    @Override protected SoundEvent getAmbientSound() { return SoundEvents.WARDEN_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WARDEN_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.WARDEN_DEATH; }
    @Override protected void playStepSound(BlockPos pos, BlockState state) { this.playSound(SoundEvents.SLIME_BLOCK_STEP, 1.2F, 0.6F); }
    @Override protected float getSoundVolume() { return 2.0F; }
    @Override public float getVoicePitch() { return 0.6F + this.random.nextFloat() * 0.1F; }

    // ===== Animatsiya sinxronizatsiyasi (server -> mijoz) =====
    private void playAction(int id) {
        int seq = (this.entityData.get(ACTION) >> 4) + 1;
        this.entityData.set(ACTION, (seq << 4) | id);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ACTION.equals(key) && this.level().isClientSide()) {
            int v = this.entityData.get(ACTION);
            if (v != 0) { this.clientAction = v & 15; this.clientSeq = v >> 4; this.clientActionStart = this.tickCount; }
        }
    }

    public int getClientAction() { return this.clientAction; }
    public int getClientActionStart() { return this.clientActionStart; }
    public int getClientSeq() { return this.clientSeq; }

    @Override
    public AABB getBoundingBoxForCulling() { return this.getBoundingBox().inflate(2.0D, 1.0D, 2.0D); }
}
