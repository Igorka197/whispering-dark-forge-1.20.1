package com.example.whisperingdark;
package com.example.whisperingdark;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Mod(WhisperingDarkMod.MODID)
public class WhisperingDarkMod {
    public static final String MODID = "whisperingdark";

    private static long nextAllowedSpawnGameTime = -1L;

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<EntityType<WhispererEntity>> WHISPERER =
            ENTITIES.register("whisperer", () -> EntityType.Builder.of(WhispererEntity::new, MobCategory.MONSTER)
                    .sized(0.65F, 2.85F)
                    .clientTrackingRange(64)
                    .build("whisperer"));

    public static final RegistryObject<Item> WHISPERER_SPAWN_EGG =
            ITEMS.register("whisperer_spawn_egg", () ->
                    new ForgeSpawnEggItem(WHISPERER, 0x000000, 0x141414, new Item.Properties()));

    public static final RegistryObject<SoundEvent> WHISPER_AMBIENT =
            SOUNDS.register("whisper_ambient", () ->
                    SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "whisper_ambient")));
    public static final RegistryObject<SoundEvent> WHISPER_SCREAM =
            SOUNDS.register("whisper_scream", () ->
                    SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "whisper_scream")));
    public static final RegistryObject<SoundEvent> WHISPER_DISAPPEAR =
            SOUNDS.register("whisper_disappear", () ->
                    SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "whisper_disappear")));

    public WhisperingDarkMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreative);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::registerSpawnPlacements);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(WHISPERER_SPAWN_EGG);
        }
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WHISPERER.get(), WhispererEntity.createAttributes().build());
    }

    private void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
                WHISPERER.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WhispererEntity::checkWhispererSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
    }

    public static void scheduleNextSpawnCooldown(Level level) {
        nextAllowedSpawnGameTime = level.getGameTime() + 72000L + level.random.nextInt(24001);
    }

    public static boolean hasAnyWhispererInWorld(ServerLevel serverLevel, WhispererEntity exclude) {
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof WhispererEntity w && w != exclude && w.isAlive()) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof Player deadPlayer && deadPlayer.level() instanceof ServerLevel serverLevel) {
            List<WhispererEntity> toVanish = new ArrayList<>();
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof WhispererEntity w && w.isAlive() && w.isHuntingPlayer(deadPlayer)) {
                    toVanish.add(w);
                }
            }
            for (WhispererEntity w : toVanish) {
                w.vanishWithSound();
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player.tickCount % 200 != 0) return;

        Player player = event.player;
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        long gameTime = serverLevel.getGameTime();
        if (nextAllowedSpawnGameTime < 0L) {
            scheduleNextSpawnCooldown(serverLevel);
            return;
        }
        if (gameTime < nextAllowedSpawnGameTime) {
            return;
        }

        if (hasAnyWhispererInWorld(serverLevel, null)) {
            return;
        }

        if (serverLevel.getSkyDarken() < 7 && !serverLevel.isThundering() && player.getY() > 52.0D) return;

        BlockPos ppos = player.blockPosition();
        if (serverLevel.getBrightness(LightLayer.BLOCK, ppos) > 3) return;

        Vec3 look = player.getLookAngle();
        double dist = 16.0D + serverLevel.random.nextDouble() * 16.0D;
        double angle = (serverLevel.random.nextDouble() - 0.5D) * 1.5D;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rx = look.x * cos - look.z * sin;
        double rz = look.x * sin + look.z * cos;

        BlockPos spawnPos = BlockPos.containing(player.getX() - rx * dist, player.getY(), player.getZ() - rz * dist);
        BlockPos valid = findGround(serverLevel, spawnPos);
        if (valid != null && serverLevel.getBrightness(LightLayer.BLOCK, valid) <= 3) {
            WhispererEntity entity = WHISPERER.get().create(serverLevel);
            if (entity != null) {
                entity.moveTo(valid.getX() + 0.5D, valid.getY(), valid.getZ() + 0.5D,
                        serverLevel.random.nextFloat() * 360.0F, 0.0F);
                entity.lockHuntedPlayer(player);
                serverLevel.addFreshEntity(entity);
                scheduleNextSpawnCooldown(serverLevel);
            }
        }
    }

    private static BlockPos findGround(Level level, BlockPos pos) {
        for (int dy = -4; dy <= 4; dy++) {
            BlockPos p = pos.above(dy);
            if (level.getBlockState(p).isAir()
                    && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.above(2)).isAir()
                    && level.getBlockState(p.below()).isSolidRender(level, p.below())) {
                return p;
            }
        }
        return null;
    }

    public static class WhispererEntity extends Monster {
        private static final EntityDataAccessor<Boolean> RAGING =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> CONTORTING =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> CONVULSING =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Integer> POSE_DIR =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.INT);
        private static final EntityDataAccessor<Integer> HUNTED_PLAYER_ID =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.INT);

        private UUID huntedPlayerUUID = null;
        private int contortTicks = 0;
        private int convulseTicks = 0;
        private boolean hasUsedHitFreeze = false;
        private int unseenStalkTicks = 0;
        private int shadowStepCooldown = 0;

        public WhispererEntity(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.setMaxUpStep(1.25F);
            this.setInvulnerable(true);
            this.setPersistenceRequired();
            this.xpReward = 0;
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(RAGING, false);
            this.entityData.define(CONTORTING, false);
            this.entityData.define(CONVULSING, false);
            this.entityData.define(POSE_DIR, 1);
            this.entityData.define(HUNTED_PLAYER_ID, -1);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 1000.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.25D)
                    .add(Attributes.ATTACK_DAMAGE, 10.0D)
                    .add(Attributes.FOLLOW_RANGE, 64.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
        }

        public boolean isRaging() { return this.entityData.get(RAGING); }
        public void setRaging(boolean v) { this.entityData.set(RAGING, v); }
        public boolean isContorting() { return this.entityData.get(CONTORTING); }
        public void setContorting(boolean v) { this.entityData.set(CONTORTING, v); }
        public boolean isConvulsing() { return this.entityData.get(CONVULSING); }
        public void setConvulsing(boolean v) { this.entityData.set(CONVULSING, v); }
        public int getPoseDir() { return this.entityData.get(POSE_DIR); }
        public void setPoseDir(int dir) { this.entityData.set(POSE_DIR, dir); }
        public int getHuntedPlayerId() { return this.entityData.get(HUNTED_PLAYER_ID); }
        public void setHuntedPlayerId(int id) { this.entityData.set(HUNTED_PLAYER_ID, id); }

        public void lockHuntedPlayer(Player player) {
            this.huntedPlayerUUID = player.getUUID();
            this.setHuntedPlayerId(player.getId());
            this.setTarget(player);
        }

        public boolean isHuntingPlayer(Player player) {
            if (this.huntedPlayerUUID != null) {
                return this.huntedPlayerUUID.equals(player.getUUID());
            }
            return false;
        }

        @Override
        public boolean removeWhenFarAway(double distanceToClosestPlayer) {
            return false;
        }

        @Override
        public void knockback(double strength, double x, double z) {
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        protected void doPush(Entity entity) {
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            if (source.is(DamageTypes.GENERIC_KILL)) {
                return false;
            }
            return true;
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (source.is(DamageTypes.GENERIC_KILL)) {
                return super.hurt(source, amount);
            }
            if (!this.level().isClientSide) {
                this.playBoneCrunchSound(1.35F);

                if (source.getEntity() instanceof Player player && !player.isCreative() && !player.isSpectator()) {
                    if (this.huntedPlayerUUID == null) {
                        this.lockHuntedPlayer(player);
                    }
                    if (this.isHuntingPlayer(player)) {
                        if (!this.hasUsedHitFreeze && !this.isContorting() && !this.isConvulsing()) {
                            this.hasUsedHitFreeze = true;
                            this.startPostAttackConvulsion(35);
                        } else if (!this.isRaging() && !this.isContorting() && !this.isConvulsing()) {
                            this.triggerRage(player);
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            if (this.isContorting() || this.isConvulsing()) {
                return false;
            }
            boolean hit = super.doHurtTarget(target);
            if (hit && !this.level().isClientSide && target instanceof Player player) {
                if (!player.isAlive() || player.isDeadOrDying()) {
                    this.vanishWithSound();
                    return true;
                }
                if (!this.hasUsedHitFreeze) {
                    this.hasUsedHitFreeze = true;
                    this.startPostAttackConvulsion(35);
                }
            }
            return hit;
        }

        private void startFiveSecondContortion(Player player) {
            this.lockHuntedPlayer(player);
            this.setPoseDir(this.random.nextBoolean() ? 1 : -1);
            this.setContorting(true);
            this.contortTicks = 100;
            this.getNavigation().stop();
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            this.playBoneCrunchSound(1.6F);
            this.playLowAgonyGroan(1.3F);
        }

        private void startPostAttackConvulsion(int ticks) {
            this.setPoseDir(this.random.nextBoolean() ? 1 : -1);
            this.setConvulsing(true);
            this.convulseTicks = ticks;
            this.getNavigation().stop();
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            this.playBoneCrunchSound(1.55F);
        }

        public void playBoneCrunchSound(float volume) {
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.TURTLE_EGG_BREAK, SoundSource.HOSTILE, volume, 0.72F + this.random.nextFloat() * 0.12F);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, volume, 0.82F + this.random.nextFloat() * 0.12F);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.SKELETON_CONVERTED_TO_STRAY, SoundSource.HOSTILE, volume * 0.9F, 0.65F + this.random.nextFloat() * 0.10F);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, volume * 0.75F, 0.55F);
        }

        public void playLowAgonyGroan(float volume) {
            this.level().playSound(null, this.getX(), this.getY() + 1.5D, this.getZ(),
                    SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, volume * 1.4F, 0.50F + this.random.nextFloat() * 0.06F);
            this.level().playSound(null, this.getX(), this.getY() + 1.5D, this.getZ(),
                    SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, volume * 1.1F, 0.50F + this.random.nextFloat() * 0.05F);
            this.level().playSound(null, this.getX(), this.getY() + 1.5D, this.getZ(),
                    SoundEvents.WARDEN_AMBIENT, SoundSource.HOSTILE, volume * 1.2F, 0.52F + this.random.nextFloat() * 0.06F);
        }

        private void lockBodyAndGazeToPlayer(Entity target) {
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();

            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;

            this.setYRot(targetYaw);
            this.yRotO = targetYaw;
            this.yBodyRot = targetYaw;
            this.yBodyRotO = targetYaw;
            this.yHeadRot = targetYaw;
            this.yHeadRotO = targetYaw;
            this.setXRot(0.0F);
            this.xRotO = 0.0F;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(1, new WhispererChaseGoal(this, 1.32D, true));
            this.goalSelector.addGoal(2, new DirectStalkGoal(this));
        }

        @Override
        public void tick() {
            super.tick();

            boolean shouldLockGaze = !this.isRaging() || this.isContorting() || this.isConvulsing();

            if (this.level().isClientSide) {
                if (shouldLockGaze) {
                    int targetId = this.getHuntedPlayerId();
                    if (targetId != -1) {
                        Entity clientTarget = this.level().getEntity(targetId);
                        if (clientTarget != null) {
                            this.lockBodyAndGazeToPlayer(clientTarget);
                        }
                    }
                }
                float particleChance = (this.isRaging() || this.isContorting() || this.isConvulsing()) ? 0.45F : 0.15F;
                if (this.random.nextFloat() < particleChance) {
                    double rx = this.getX() + (this.random.nextDouble() - 0.5D) * 0.7D;
                    double ry = this.getY() + this.random.nextDouble() * 2.7D;
                    double rz = this.getZ() + (this.random.nextDouble() - 0.5D) * 0.7D;
                    this.level().addParticle(ParticleTypes.SMOKE, rx, ry, rz, 0.0D, 0.01D, 0.0D);
                }
                return;
            }

            if (this.tickCount == 1 && this.level() instanceof ServerLevel serverLevel) {
                List<WhispererEntity> others = new ArrayList<>();
                for (Entity e : serverLevel.getAllEntities()) {
                    if (e instanceof WhispererEntity w && w != this && w.isAlive()) {
                        others.add(w);
                    }
                }
                for (WhispererEntity old : others) {
                    old.discard();
                }
            }

            if (this.shadowStepCooldown > 0) this.shadowStepCooldown--;

            Player player = null;
            if (this.huntedPlayerUUID != null) {
                player = this.level().getPlayerByUUID(this.huntedPlayerUUID);
                if (player == null || !player.isAlive() || player.isDeadOrDying()) {
                    this.vanishWithSound();
                    return;
                }
                if (this.getHuntedPlayerId() != player.getId()) {
                    this.setHuntedPlayerId(player.getId());
                }
                this.setTarget(player);
            } else {
                Player nearest = this.level().getNearestPlayer(this, 48.0D);
                if (nearest != null && nearest.isAlive() && !nearest.isCreative() && !nearest.isSpectator()) {
                    this.lockHuntedPlayer(nearest);
                    player = nearest;
                } else {
                    Player anyPlayer = this.level().getNearestPlayer(this, 64.0D);
                    if (anyPlayer != null && shouldLockGaze) {
                        this.lockBodyAndGazeToPlayer(anyPlayer);
                    }
                    return;
                }
            }

            if (shouldLockGaze) {
                this.getLookControl().setLookAt(player, 180.0F, 180.0F);
                this.lockBodyAndGazeToPlayer(player);
            }

            if (player.isCreative() || player.isSpectator()) {
                return;
            }

            double dist = this.distanceTo(player);
            if (dist > 64.0D) {
                this.vanishWithSound();
                return;
            }

            int hbInterval = Mth.clamp((int) (dist * 1.5D), 12, 40);
            if (dist < 32.0D && this.tickCount % hbInterval == 0) {
                float vol = (float) Mth.clamp(1.25D - (dist / 34.0D), 0.25D, 1.0D);
                this.level().playSound(null, player.blockPosition(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, vol, 0.52F);
            }
            if (dist < 28.0D && this.tickCount % 65 == 0 && !this.isRaging()) {
                this.level().playSound(null, this.blockPosition(),
                        SoundEvents.WARDEN_AMBIENT, SoundSource.AMBIENT, 0.85F, 0.50F);
                this.level().playSound(null, this.blockPosition(),
                        SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.AMBIENT, 0.80F, 0.50F);
            }

            if (this.isConvulsing()) {
                this.getNavigation().stop();
                this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
                this.lockBodyAndGazeToPlayer(player);
                if (--this.convulseTicks <= 0) {
                    this.setConvulsing(false);
                    if (!this.isRaging()) {
                        this.triggerRage(player);
                    }
                }
                return;
            }

            if (this.isContorting()) {
                this.getNavigation().stop();
                this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
                this.lockBodyAndGazeToPlayer(player);

                if (this.contortTicks == 80 || this.contortTicks == 25) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0, false, false));
                }
                if (this.contortTicks == 50) {
                    this.playLowAgonyGroan(1.4F);
                }

                if (--this.contortTicks <= 0) {
                    this.setContorting(false);
                    this.triggerRage(player);
                }
                return;
            }

            if (this.isRaging()) {
                if (this.tickCount % 5 == 0 && this.getDeltaMovement().horizontalDistanceSqr() > 0.002D) {
                    this.level().playSound(null, this.blockPosition(),
                            SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 1.25F, 0.80F + this.random.nextFloat() * 0.15F);
                    this.level().playSound(null, this.blockPosition(),
                            SoundEvents.DEEPSLATE_STEP, SoundSource.HOSTILE, 1.0F, 0.70F + this.random.nextFloat() * 0.15F);
                }

                if (this.tickCount % 34 == 0) {
                    this.playLowAgonyGroan(1.85F);
                }

                if (dist < 15.0D && this.tickCount % 55 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0, false, false));
                }

                if (this.tickCount % 10 == 0) {
                    extinguishTorchesAndBreakDoors();
                }
                return;
            }

            if (isPlayerLookingAtMe(player, 0.30D)) {
                this.startFiveSecondContortion(player);
                return;
            }

            this.unseenStalkTicks++;

            if (this.tickCount % 14 == 0 && this.getDeltaMovement().horizontalDistanceSqr() > 0.001D) {
                this.level().playSound(null, this.blockPosition(),
                        SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 0.75F, 0.70F);
            }

            if (dist < 24.0D && this.unseenStalkTicks % 75 == 0) {
                Vec3 look = player.getLookAngle();
                BlockPos phantomPos = BlockPos.containing(
                        player.getX() - look.x * 2.5D,
                        player.getY() + 1.2D,
                        player.getZ() - look.z * 2.5D
                );
                this.level().playSound(null, phantomPos,
                        SoundEvents.DEEPSLATE_STEP, SoundSource.AMBIENT, 0.85F, 0.65F);
            }

            if (dist > 26.0D && dist <= 32.0D && this.shadowStepCooldown == 0 && this.unseenStalkTicks > 80) {
                tryShadowStepBehind(player, 16.0D);
            }
        }

        private void extinguishTorchesAndBreakDoors() {
            BlockPos center = this.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 2, 3))) {
                BlockState state = this.level().getBlockState(pos);
                if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                        || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
                    this.level().destroyBlock(pos, true);
                    break;
                }
                if (state.getBlock() instanceof DoorBlock door && !door.isOpen(state) && pos.distSqr(center) <= 5.0D) {
                    door.setOpen(this, this.level(), state, pos, true);
                    this.level().playSound(null, pos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.2F, 0.65F);
                    break;
                }
            }
        }

        private void tryShadowStepBehind(Player player, double targetDist) {
            Vec3 look = player.getLookAngle();
            double angle = (this.random.nextDouble() - 0.5D) * 1.1D;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double rx = look.x * cos - look.z * sin;
            double rz = look.x * sin + look.z * cos;

            BlockPos candidate = BlockPos.containing(
                    player.getX() - rx * targetDist,
                    player.getY(),
                    player.getZ() - rz * targetDist
            );
            BlockPos ground = findGround(this.level(), candidate);
            if (ground != null && this.level().getBrightness(LightLayer.BLOCK, ground) <= 5) {
                this.teleportTo(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D);
                this.lockBodyAndGazeToPlayer(player);
                this.shadowStepCooldown = 140;
            }
        }

        private boolean isPlayerLookingAtMe(Player player, double threshold) {
            Vec3 viewVec = player.getViewVector(1.0F).normalize();
            Vec3 toEntity = this.getEyePosition().subtract(player.getEyePosition()).normalize();
            double dot = viewVec.dot(toEntity);
            if (dot < threshold) return false;
            return player.hasLineOfSight(this);
        }

        public void triggerRage(Player player) {
            if (this.isRaging()) return;
            this.setRaging(true);
            this.setContorting(false);
            this.setConvulsing(false);

            AttributeInstance speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(0.345D);
            }

            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
            this.playBoneCrunchSound(1.6F);
            this.playLowAgonyGroan(2.0F);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.5F, 0.52F);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 1.4F, 0.55F);

            if (player instanceof ServerPlayer sp) {
                sp.playNotifySound(SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 1.2F, 0.50F);
            }
        }

        public void vanishWithSound() {
            if (!this.level().isClientSide) {
                scheduleNextSpawnCooldown(this.level());
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 0.9F, 0.50F);
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.4D, this.getZ(),
                        40, 0.35D, 0.9D, 0.35D, 0.03D);
            }
            this.discard();
        }

        @Override
        public float getVoicePitch() {
            return 0.50F + this.random.nextFloat() * 0.08F;
        }

        @Override
        protected SoundEvent getAmbientSound() {
            return SoundEvents.WARDEN_AMBIENT;
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource ds) {
            return SoundEvents.BONE_BLOCK_BREAK;
        }

        @Override
        protected SoundEvent getDeathSound() {
            return SoundEvents.WARDEN_DEATH;
        }

        public static boolean checkWhispererSpawnRules(EntityType<WhispererEntity> type, ServerLevelAccessor level,
                                                      MobSpawnType spawnType, BlockPos pos, RandomSource random) {
            return spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND;
        }
    }

    private static class DirectStalkGoal extends Goal {
        private final WhispererEntity mob;
        private int repathCooldown = 0;

        public DirectStalkGoal(WhispererEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = this.mob.getTarget();
            return t != null && t.isAlive() && !this.mob.isRaging() && !this.mob.isContorting() && !this.mob.isConvulsing();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.repathCooldown = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) return;

            this.mob.lockBodyAndGazeToPlayer(target);

            if (--this.repathCooldown > 0) return;
            this.repathCooldown = 10;

            double dist = this.mob.distanceTo(target);
            if (dist > 6.5D) {
                this.mob.getNavigation().moveTo(target, 0.92D);
            } else {
                this.mob.getNavigation().stop();
            }
        }
    }

    private static class WhispererChaseGoal extends MeleeAttackGoal {
        private final WhispererEntity whisperer;

        public WhispererChaseGoal(WhispererEntity mob, double speedModifier, boolean followUnseen) {
            super(mob, speedModifier, followUnseen);
            this.whisperer = mob;
        }

        @Override
        public boolean canUse() {
            return this.whisperer.isRaging()
                    && !this.whisperer.isContorting()
                    && !this.whisperer.isConvulsing()
                    && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.whisperer.isRaging()
                    && !this.whisperer.isContorting()
                    && !this.whisperer.isConvulsing()
                    && super.canContinueToUse();
        }

        @Override
        protected double getAttackReachSqr(LivingEntity attackTarget) {
            return 4.5D + attackTarget.getBbWidth();
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientSetup {
        public static final ModelLayerLocation WHISPERER_LAYER =
                new ModelLayerLocation(new ResourceLocation(MODID, "whisperer"), "main");

        @SubscribeEvent
        public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(WHISPERER_LAYER, WhispererModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(WHISPERER.get(), WhispererRenderer::new);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class WhispererModel extends HierarchicalModel<WhispererEntity> {
        private final ModelPart root;
        private final ModelPart pelvis;
        private final ModelPart abdomen;
        private final ModelPart thorax;
        private final ModelPart neckLower;
        private final ModelPart neckUpper;
        private final ModelPart headHood;
        private final ModelPart leftArmUpper;
        private final ModelPart leftArmLower;
        private final ModelPart rightArmUpper;
        private final ModelPart rightArmLower;
        private final ModelPart leftLegUpper;
        private final ModelPart leftLegLower;
        private final ModelPart rightLegUpper;
        private final ModelPart rightLegLower;

        public WhispererModel(ModelPart root) {
            this.root = root;
            this.pelvis = root.getChild("pelvis");
            this.abdomen = this.pelvis.getChild("abdomen");
            this.thorax = this.abdomen.getChild("thorax");
            this.neckLower = this.thorax.getChild("neck_lower");
            this.neckUpper = this.neckLower.getChild("neck_upper");
            this.headHood = this.neckUpper.getChild("head_hood");
            this.leftArmUpper = this.thorax.getChild("left_arm_upper");
            this.leftArmLower = this.leftArmUpper.getChild("left_arm_lower");
            this.rightArmUpper = this.thorax.getChild("right_arm_upper");
            this.rightArmLower = this.rightArmUpper.getChild("right_arm_lower");
            this.leftLegUpper = this.pelvis.getChild("left_leg_upper");
            this.leftLegLower = this.leftLegUpper.getChild("left_leg_lower");
            this.rightLegUpper = this.pelvis.getChild("right_leg_upper");
            this.rightLegLower = this.rightLegUpper.getChild("right_leg_lower");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();

            PartDefinition pelvis = root.addOrReplaceChild("pelvis",
                    CubeListBuilder.create().texOffs(0, 36).addBox(-3.5F, -2.5F, -2.0F, 7.0F, 5.0F, 4.0F),
                    PartPose.offset(0.0F, -2.0F, 0.0F));

            PartDefinition abdomen = pelvis.addOrReplaceChild("abdomen",
                    CubeListBuilder.create().texOffs(22, 36).addBox(-3.0F, -7.5F, -1.75F, 6.0F, 9.5F, 3.5F),
                    PartPose.offset(0.0F, -2.0F, 0.0F));

            PartDefinition thorax = abdomen.addOrReplaceChild("thorax",
                    CubeListBuilder.create()
                            .texOffs(0, 20).addBox(-4.5F, -10.5F, -2.5F, 9.0F, 12.5F, 5.0F)
                            .texOffs(28, 20).addBox(-4.0F, -9.5F, -2.8F, 8.0F, 9.0F, 1.0F),
                    PartPose.offset(0.0F, -7.0F, 0.0F));

            PartDefinition neckLower = thorax.addOrReplaceChild("neck_lower",
                    CubeListBuilder.create().texOffs(48, 20).addBox(-2.1F, -4.5F, -1.75F, 4.2F, 6.5F, 3.5F),
                    PartPose.offset(0.0F, -10.0F, 0.0F));

            PartDefinition neckUpper = neckLower.addOrReplaceChild("neck_upper",
                    CubeListBuilder.create().texOffs(48, 30).addBox(-1.9F, -4.5F, -1.6F, 3.8F, 6.5F, 3.2F),
                    PartPose.offset(0.0F, -4.0F, 0.0F));

            neckUpper.addOrReplaceChild("head_hood",
                    CubeListBuilder.create()
                            .texOffs(0, 0).addBox(-4.5F, -9.5F, -4.0F, 9.0F, 11.5F, 8.0F, new CubeDeformation(0.15F))
                            .texOffs(34, 0).addBox(-4.0F, -9.0F, -3.7F, 8.0F, 10.5F, 7.0F),
                    PartPose.offset(0.0F, -3.8F, 0.0F));

            PartDefinition leftArmUpper = thorax.addOrReplaceChild("left_arm_upper",
                    CubeListBuilder.create().texOffs(0, 46).addBox(-1.25F, -2.0F, -1.25F, 2.5F, 14.5F, 2.5F),
                    PartPose.offset(4.8F, -9.0F, 0.0F));

            leftArmUpper.addOrReplaceChild("left_arm_lower",
                    CubeListBuilder.create()
                            .texOffs(12, 46).addBox(-1.2F, -2.0F, -1.2F, 2.4F, 15.0F, 2.4F)
                            .texOffs(24, 46).addBox(-1.0F, 12.0F, -1.0F, 2.0F, 6.5F, 2.0F),
                    PartPose.offset(0.0F, 11.5F, 0.0F));

            PartDefinition rightArmUpper = thorax.addOrReplaceChild("right_arm_upper",
                    CubeListBuilder.create().texOffs(34, 46).addBox(-1.25F, -2.0F, -1.25F, 2.5F, 14.5F, 2.5F),
                    PartPose.offset(-4.8F, -9.0F, 0.0F));

            rightArmUpper.addOrReplaceChild("right_arm_lower",
                    CubeListBuilder.create()
                            .texOffs(46, 46).addBox(-1.2F, -2.0F, -1.2F, 2.4F, 15.0F, 2.4F)
                            .texOffs(58, 46).addBox(-1.0F, 12.0F, -1.0F, 2.0F, 6.5F, 2.0F),
                    PartPose.offset(0.0F, 11.5F, 0.0F));

            PartDefinition leftLegUpper = pelvis.addOrReplaceChild("left_leg_upper",
                    CubeListBuilder.create().texOffs(0, 66).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 14.5F, 3.0F),
                    PartPose.offset(2.2F, 1.5F, 0.0F));

            leftLegUpper.addOrReplaceChild("left_leg_lower",
                    CubeListBuilder.create().texOffs(14, 66).addBox(-1.3F, -2.0F, -1.3F, 2.6F, 14.5F, 2.6F),
                    PartPose.offset(0.0F, 12.0F, 0.0F));

            PartDefinition rightLegUpper = pelvis.addOrReplaceChild("right_leg_upper",
                    CubeListBuilder.create().texOffs(28, 66).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 14.5F, 3.0F),
                    PartPose.offset(-2.2F, 1.5F, 0.0F));

            rightLegUpper.addOrReplaceChild("right_leg_lower",
                    CubeListBuilder.create().texOffs(42, 66).addBox(-1.3F, -2.0F, -1.3F, 2.6F, 14.5F, 2.6F),
                    PartPose.offset(-0.0F, 12.0F, 0.0F));

            return LayerDefinition.create(mesh, 128, 128);
        }

        @Override
        public ModelPart root() { return this.root; }

        @Override
        public void setupAnim(WhispererEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            this.root().getAllParts().forEach(ModelPart::resetPose);

            float yawRad = Mth.clamp(netHeadYaw * ((float) Math.PI / 180.0F), -0.85F, 0.85F);
            float pitchRad = Mth.clamp(headPitch * ((float) Math.PI / 180.0F), -0.60F, 0.60F);

            if (entity.isContorting() || entity.isConvulsing()) {
                float dir = (float) entity.getPoseDir();
                float microTremor = Mth.sin(ageInTicks * 4.2F) * 0.05F;

                this.pelvis.y = -0.6F;
                this.pelvis.zRot = 0.05F * dir;

                this.abdomen.xRot = -0.12F;
                this.abdomen.zRot = -0.08F * dir;

                this.thorax.xRot = -0.18F;
                this.thorax.zRot = 0.22F * dir;

                this.neckLower.xRot = -0.05F;
                this.neckLower.zRot = 0.35F * dir;
                this.neckUpper.xRot = -0.12F;
                this.neckUpper.zRot = 0.45F * dir;
                this.headHood.xRot = -0.10F + pitchRad * 0.5F;
                this.headHood.zRot = 1.30F * dir;

                if (dir > 0) {
                    this.leftArmUpper.xRot = -1.42F;
                    this.leftArmUpper.yRot = 0.08F;
                    this.leftArmUpper.zRot = -0.10F + microTremor;
                    this.leftArmLower.xRot = -0.12F;
                    this.leftArmLower.yRot = 0.05F;

                    this.rightArmUpper.xRot = 0.48F;
                    this.rightArmUpper.yRot = -0.15F;
                    this.rightArmUpper.zRot = 0.42F - microTremor;
                    this.rightArmLower.xRot = 0.22F;
                } else {
                    this.rightArmUpper.xRot = -1.42F;
                    this.rightArmUpper.yRot = -0.08F;
                    this.rightArmUpper.zRot = 0.10F + microTremor;
                    this.rightArmLower.xRot = -0.12F;
                    this.rightArmLower.yRot = -0.05F;

                    this.leftArmUpper.xRot = 0.48F;
                    this.leftArmUpper.yRot = 0.15F;
                    this.leftArmUpper.zRot = -0.42F - microTremor;
                    this.leftArmLower.xRot = 0.22F;
                }

                this.leftLegUpper.xRot = -0.22F;
                this.leftLegLower.xRot = 0.45F;
                this.rightLegUpper.xRot = 0.10F;
                this.rightLegLower.xRot = 0.12F;
                return;
            }

            if (entity.isRaging()) {
                float runCycle = limbSwing * 0.85F;
                float runStride = Math.max(Mth.clamp(limbSwingAmount * 1.25F, 0.0F, 1.0F), 0.70F);
                float twitch = Mth.sin(ageInTicks * 2.8F) * 0.05F;

                this.pelvis.y = -0.8F + Mth.sin(runCycle * 2.0F) * 0.38F * runStride;
                this.pelvis.yRot = Mth.cos(runCycle) * 0.10F * runStride;

                this.abdomen.xRot = 0.40F;
                this.thorax.xRot = 0.48F;
                this.thorax.yRot = Mth.cos(runCycle) * 0.18F * runStride;

                this.neckLower.xRot = -0.25F;
                this.neckLower.yRot = yawRad * 0.35F;
                this.neckUpper.xRot = -0.38F;
                this.neckUpper.yRot = yawRad * 0.35F;
                this.headHood.xRot = -0.16F + pitchRad * 0.60F;
                this.headHood.yRot = yawRad * 0.30F;
                this.headHood.zRot = 0.18F * entity.getPoseDir() + twitch;

                this.leftArmUpper.xRot = -0.42F + Mth.cos(runCycle + (float) Math.PI) * 0.65F * runStride;
                this.leftArmUpper.zRot = -0.28F - twitch;
                this.leftArmLower.xRot = -0.22F + Mth.sin(runCycle + (float) Math.PI) * 0.20F * runStride;
                this.leftArmLower.yRot = 0.10F;

                this.rightArmUpper.xRot = -0.42F + Mth.cos(runCycle) * 0.65F * runStride;
                this.rightArmUpper.zRot = 0.28F + twitch;
                this.rightArmLower.xRot = -0.22F + Mth.sin(runCycle) * 0.20F * runStride;
                this.rightArmLower.yRot = -0.10F;

                this.leftLegUpper.xRot = -0.20F + Mth.cos(runCycle) * 1.15F * runStride;
                this.rightLegUpper.xRot = -0.20F + Mth.cos(runCycle + (float) Math.PI) * 1.15F * runStride;
                this.leftLegLower.xRot = 0.35F + Math.max(0.0F, Mth.sin(runCycle)) * 0.95F * runStride;
                this.rightLegLower.xRot = 0.35F + Math.max(0.0F, Mth.sin(runCycle + (float) Math.PI)) * 0.95F * runStride;
            } else {
                float walkCycle = limbSwing * 0.6662F;
                float stride = Mth.clamp(limbSwingAmount * 1.15F, 0.0F, 1.0F);
                float breath = Mth.sin(ageInTicks * 0.08F);

                this.pelvis.y = -1.8F + Mth.abs(Mth.sin(walkCycle)) * 0.35F * stride;
                this.abdomen.xRot = 0.10F + breath * 0.02F;
                this.thorax.xRot = 0.16F + breath * 0.03F;

                this.neckLower.xRot = -0.06F;
                this.neckUpper.xRot = -0.12F;
                this.headHood.xRot = -0.06F + pitchRad * 0.65F;
                this.headHood.zRot = 0.14F;

                this.leftLegUpper.xRot = -0.06F + Mth.cos(walkCycle) * 0.62F * stride;
                this.rightLegUpper.xRot = -0.06F + Mth.cos(walkCycle + (float) Math.PI) * 0.62F * stride;
                this.leftLegLower.xRot = 0.14F + Math.max(0.0F, Mth.sin(walkCycle)) * 0.45F * stride;
                this.rightLegLower.xRot = 0.14F + Math.max(0.0F, Mth.sin(walkCycle + (float) Math.PI)) * 0.45F * stride;

                this.leftArmUpper.xRot = -0.15F + Mth.cos(walkCycle + (float) Math.PI) * 0.28F * stride;
                this.leftArmUpper.zRot = -0.10F;
                this.leftArmLower.xRot = -0.18F;

                this.rightArmUpper.xRot = -0.15F + Mth.cos(walkCycle) * 0.28F * stride;
                this.rightArmUpper.zRot = 0.10F;
                this.rightArmLower.xRot = -0.18F;
            }

            if (this.attackTime > 0.0F) {
                float swing = Mth.sin(this.attackTime * (float) Math.PI);
                this.thorax.xRot += swing * 0.30F;
                this.leftArmUpper.xRot -= swing * 0.95F;
                this.rightArmUpper.xRot -= swing * 0.95F;
                this.leftArmLower.xRot -= swing * 0.35F;
                this.rightArmLower.xRot -= swing * 0.35F;
            }
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                                   int packedOverlay, float red, float green, float blue, float alpha) {
            super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, 0.0F, 0.0F, 0.0F, 1.0F);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class WhispererRenderer extends MobRenderer<WhispererEntity, WhispererModel> {
        private static ResourceLocation PITCH_BLACK_TEX;

        public WhispererRenderer(EntityRendererProvider.Context context) {
            super(context, new WhispererModel(context.bakeLayer(ClientSetup.WHISPERER_LAYER)), 0.5F);
            ensurePitchBlackTexture();
        }

        private static void ensurePitchBlackTexture() {
            if (PITCH_BLACK_TEX != null) return;
            NativeImage blackImg = new NativeImage(128, 128, false);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    blackImg.setPixelRGBA(x, y, 0xFF000000);
                }
            }
            PITCH_BLACK_TEX = Minecraft.getInstance().getTextureManager()
                    .register("whisperer_pitch_black", new DynamicTexture(blackImg));
        }

        @Override
        public ResourceLocation getTextureLocation(WhispererEntity entity) {
            ensurePitchBlackTexture();
            return PITCH_BLACK_TEX;
        }
    }
}
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Mod(WhisperingDarkMod.MODID)
public class WhisperingDarkMod {
    public static final String MODID = "whisperingdark";

    private static long nextAllowedSpawnGameTime = -1L;

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<EntityType<WhispererEntity>> WHISPERER =
            ENTITIES.register("whisperer", () -> EntityType.Builder.of(WhispererEntity::new, MobCategory.MONSTER)
                    .sized(0.65F, 2.85F)
                    .clientTrackingRange(64)
                    .build("whisperer"));

    public static final RegistryObject<Item> WHISPERER_SPAWN_EGG =
            ITEMS.register("whisperer_spawn_egg", () ->
                    new ForgeSpawnEggItem(WHISPERER, 0x000000, 0x141414, new Item.Properties()));

    public static final RegistryObject<SoundEvent> WHISPER_AMBIENT =
            SOUNDS.register("whisper_ambient", () ->
                    SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "whisper_ambient")));
    public static final RegistryObject<SoundEvent> WHISPER_SCREAM =
            SOUNDS.register("whisper_scream", () ->
                    SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "whisper_scream")));
    public static final RegistryObject<SoundEvent> WHISPER_DISAPPEAR =
            SOUNDS.register("whisper_disappear", () ->
                    SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "whisper_disappear")));

    public WhisperingDarkMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addCreative);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::registerSpawnPlacements);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(WHISPERER_SPAWN_EGG);
        }
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WHISPERER.get(), WhispererEntity.createAttributes().build());
    }

    private void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(
                WHISPERER.get(),
                SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WhispererEntity::checkWhispererSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE
        );
    }

    public static void scheduleNextSpawnCooldown(Level level) {
        nextAllowedSpawnGameTime = level.getGameTime() + 72000L + level.random.nextInt(24001);
    }

    public static boolean hasAnyWhispererInWorld(ServerLevel serverLevel, WhispererEntity exclude) {
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof WhispererEntity w && w != exclude && w.isAlive()) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof Player deadPlayer && deadPlayer.level() instanceof ServerLevel serverLevel) {
            List<WhispererEntity> toVanish = new ArrayList<>();
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof WhispererEntity w && w.isAlive() && w.isHuntingPlayer(deadPlayer)) {
                    toVanish.add(w);
                }
            }
            for (WhispererEntity w : toVanish) {
                w.vanishWithSound();
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player.tickCount % 200 != 0) return;

        Player player = event.player;
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        long gameTime = serverLevel.getGameTime();
        if (nextAllowedSpawnGameTime < 0L) {
            scheduleNextSpawnCooldown(serverLevel);
            return;
        }
        if (gameTime < nextAllowedSpawnGameTime) {
            return;
        }

        if (hasAnyWhispererInWorld(serverLevel, null)) {
            return;
        }

        if (serverLevel.getSkyDarken() < 7 && !serverLevel.isThundering() && player.getY() > 52.0D) return;

        BlockPos ppos = player.blockPosition();
        if (serverLevel.getBrightness(LightLayer.BLOCK, ppos) > 3) return;

        Vec3 look = player.getLookAngle();
        double dist = 16.0D + serverLevel.random.nextDouble() * 16.0D;
        double angle = (serverLevel.random.nextDouble() - 0.5D) * 1.5D;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double rx = look.x * cos - look.z * sin;
        double rz = look.x * sin + look.z * cos;

        BlockPos spawnPos = BlockPos.containing(player.getX() - rx * dist, player.getY(), player.getZ() - rz * dist);
        BlockPos valid = findGround(serverLevel, spawnPos);
        if (valid != null && serverLevel.getBrightness(LightLayer.BLOCK, valid) <= 3) {
            WhispererEntity entity = WHISPERER.get().create(serverLevel);
            if (entity != null) {
                entity.moveTo(valid.getX() + 0.5D, valid.getY(), valid.getZ() + 0.5D,
                        serverLevel.random.nextFloat() * 360.0F, 0.0F);
                entity.lockHuntedPlayer(player);
                serverLevel.addFreshEntity(entity);
                scheduleNextSpawnCooldown(serverLevel);
            }
        }
    }

    private static BlockPos findGround(Level level, BlockPos pos) {
        for (int dy = -4; dy <= 4; dy++) {
            BlockPos p = pos.above(dy);
            if (level.getBlockState(p).isAir()
                    && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.above(2)).isAir()
                    && level.getBlockState(p.below()).isSolidRender(level, p.below())) {
                return p;
            }
        }
        return null;
    }

    public static class WhispererEntity extends Monster {
        private static final EntityDataAccessor<Boolean> RAGING =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> CONTORTING =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> CONVULSING =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Integer> POSE_DIR =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.INT);
        private static final EntityDataAccessor<Integer> HUNTED_PLAYER_ID =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.INT);

        private UUID huntedPlayerUUID = null;
        private int contortTicks = 0;
        private int convulseTicks = 0;
        private boolean hasUsedHitFreeze = false;
        private int unseenStalkTicks = 0;
        private int shadowStepCooldown = 0;

        public WhispererEntity(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.setMaxUpStep(1.25F);
            this.setInvulnerable(true);
            this.setPersistenceRequired();
            this.xpReward = 0;
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(RAGING, false);
            this.entityData.define(CONTORTING, false);
            this.entityData.define(CONVULSING, false);
            this.entityData.define(POSE_DIR, 1);
            this.entityData.define(HUNTED_PLAYER_ID, -1);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 1000.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.25D)
                    .add(Attributes.ATTACK_DAMAGE, 10.0D)
                    .add(Attributes.FOLLOW_RANGE, 64.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
        }

        public boolean isRaging() { return this.entityData.get(RAGING); }
        public void setRaging(boolean v) { this.entityData.set(RAGING, v); }
        public boolean isContorting() { return this.entityData.get(CONTORTING); }
        public void setContorting(boolean v) { this.entityData.set(CONTORTING, v); }
        public boolean isConvulsing() { return this.entityData.get(CONVULSING); }
        public void setConvulsing(boolean v) { this.entityData.set(CONVULSING, v); }
        public int getPoseDir() { return this.entityData.get(POSE_DIR); }
        public void setPoseDir(int dir) { this.entityData.set(POSE_DIR, dir); }
        public int getHuntedPlayerId() { return this.entityData.get(HUNTED_PLAYER_ID); }
        public void setHuntedPlayerId(int id) { this.entityData.set(HUNTED_PLAYER_ID, id); }

        public void lockHuntedPlayer(Player player) {
            this.huntedPlayerUUID = player.getUUID();
            this.setHuntedPlayerId(player.getId());
            this.setTarget(player);
        }

        public boolean isHuntingPlayer(Player player) {
            if (this.huntedPlayerUUID != null) {
                return this.huntedPlayerUUID.equals(player.getUUID());
            }
            return false;
        }

        @Override
        public boolean removeWhenFarAway(double distanceToClosestPlayer) {
            return false;
        }

        @Override
        public void knockback(double strength, double x, double z) {
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        protected void doPush(Entity entity) {
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            if (source.is(DamageTypes.GENERIC_KILL)) {
                return false;
            }
            return true;
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            if (source.is(DamageTypes.GENERIC_KILL)) {
                return super.hurt(source, amount);
            }
            if (!this.level().isClientSide) {
                this.playBoneCrunchSound(1.35F);

                if (source.getEntity() instanceof Player player && !player.isCreative() && !player.isSpectator()) {
                    if (this.huntedPlayerUUID == null) {
                        this.lockHuntedPlayer(player);
                    }
                    if (this.isHuntingPlayer(player)) {
                        if (!this.hasUsedHitFreeze && !this.isContorting() && !this.isConvulsing()) {
                            this.hasUsedHitFreeze = true;
                            this.startPostAttackConvulsion(35);
                        } else if (!this.isRaging() && !this.isContorting() && !this.isConvulsing()) {
                            this.triggerRage(player);
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public boolean doHurtTarget(Entity target) {
            if (this.isContorting() || this.isConvulsing()) {
                return false;
            }
            boolean hit = super.doHurtTarget(target);
            if (hit && !this.level().isClientSide && target instanceof Player player) {
                if (!player.isAlive() || player.isDeadOrDying()) {
                    this.vanishWithSound();
                    return true;
                }
                if (!this.hasUsedHitFreeze) {
                    this.hasUsedHitFreeze = true;
                    this.startPostAttackConvulsion(35);
                }
            }
            return hit;
        }

        private void startFiveSecondContortion(Player player) {
            this.lockHuntedPlayer(player);
            this.setPoseDir(this.random.nextBoolean() ? 1 : -1);
            this.setContorting(true);
            this.contortTicks = 100;
            this.getNavigation().stop();
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            this.playBoneCrunchSound(1.6F);
            this.playLowAgonyGroan(1.3F);
        }

        private void startPostAttackConvulsion(int ticks) {
            this.setPoseDir(this.random.nextBoolean() ? 1 : -1);
            this.setConvulsing(true);
            this.convulseTicks = ticks;
            this.getNavigation().stop();
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
            this.playBoneCrunchSound(1.55F);
        }

        public void playBoneCrunchSound(float volume) {
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.TURTLE_EGG_BREAK, SoundSource.HOSTILE, volume, 0.72F + this.random.nextFloat() * 0.12F);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, volume, 0.82F + this.random.nextFloat() * 0.12F);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.SKELETON_CONVERTED_TO_STRAY, SoundSource.HOSTILE, volume * 0.9F, 0.65F + this.random.nextFloat() * 0.10F);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.SKELETON_HURT, SoundSource.HOSTILE, volume * 0.75F, 0.55F);
        }

        public void playLowAgonyGroan(float volume) {
            this.level().playSound(null, this.getX(), this.getY() + 1.5D, this.getZ(),
                    SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, volume * 1.4F, 0.50F + this.random.nextFloat() * 0.06F);
            this.level().playSound(null, this.getX(), this.getY() + 1.5D, this.getZ(),
                    SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, volume * 1.1F, 0.50F + this.random.nextFloat() * 0.05F);
            this.level().playSound(null, this.getX(), this.getY() + 1.5D, this.getZ(),
                    SoundEvents.WARDEN_AMBIENT, SoundSource.HOSTILE, volume * 1.2F, 0.52F + this.random.nextFloat() * 0.06F);
        }

        private void lockBodyAndGazeToPlayer(Entity target) {
            double dx = target.getX() - this.getX();
            double dz = target.getZ() - this.getZ();

            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;

            this.setYRot(targetYaw);
            this.yRotO = targetYaw;
            this.yBodyRot = targetYaw;
            this.yBodyRotO = targetYaw;
            this.yHeadRot = targetYaw;
            this.yHeadRotO = targetYaw;
            this.setXRot(0.0F);
            this.xRotO = 0.0F;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(1, new WhispererChaseGoal(this, 1.32D, true));
            this.goalSelector.addGoal(2, new DirectStalkGoal(this));
        }

        @Override
        public void tick() {
            super.tick();

            boolean shouldLockGaze = !this.isRaging() || this.isContorting() || this.isConvulsing();

            if (this.level().isClientSide) {
                if (shouldLockGaze) {
                    int targetId = this.getHuntedPlayerId();
                    if (targetId != -1) {
                        Entity clientTarget = this.level().getEntity(targetId);
                        if (clientTarget != null) {
                            this.lockBodyAndGazeToPlayer(clientTarget);
                        }
                    }
                }
                float particleChance = (this.isRaging() || this.isContorting() || this.isConvulsing()) ? 0.45F : 0.15F;
                if (this.random.nextFloat() < particleChance) {
                    double rx = this.getX() + (this.random.nextDouble() - 0.5D) * 0.7D;
                    double ry = this.getY() + this.random.nextDouble() * 2.7D;
                    double rz = this.getZ() + (this.random.nextDouble() - 0.5D) * 0.7D;
                    this.level().addParticle(ParticleTypes.SMOKE, rx, ry, rz, 0.0D, 0.01D, 0.0D);
                }
                return;
            }

            if (this.tickCount == 1 && this.level() instanceof ServerLevel serverLevel) {
                List<WhispererEntity> others = new ArrayList<>();
                for (Entity e : serverLevel.getAllEntities()) {
                    if (e instanceof WhispererEntity w && w != this && w.isAlive()) {
                        others.add(w);
                    }
                }
                for (WhispererEntity old : others) {
                    old.discard();
                }
            }

            if (this.shadowStepCooldown > 0) this.shadowStepCooldown--;

            Player player = null;
            if (this.huntedPlayerUUID != null) {
                player = this.level().getPlayerByUUID(this.huntedPlayerUUID);
                if (player == null || !player.isAlive() || player.isDeadOrDying()) {
                    this.vanishWithSound();
                    return;
                }
                if (this.getHuntedPlayerId() != player.getId()) {
                    this.setHuntedPlayerId(player.getId());
                }
                this.setTarget(player);
            } else {
                Player nearest = this.level().getNearestPlayer(this, 48.0D);
                if (nearest != null && nearest.isAlive() && !nearest.isCreative() && !nearest.isSpectator()) {
                    this.lockHuntedPlayer(nearest);
                    player = nearest;
                } else {
                    Player anyPlayer = this.level().getNearestPlayer(this, 64.0D);
                    if (anyPlayer != null && shouldLockGaze) {
                        this.lockBodyAndGazeToPlayer(anyPlayer);
                    }
                    return;
                }
            }

            if (shouldLockGaze) {
                this.getLookControl().setLookAt(player, 180.0F, 180.0F);
                this.lockBodyAndGazeToPlayer(player);
            }

            if (player.isCreative() || player.isSpectator()) {
                return;
            }

            double dist = this.distanceTo(player);
            if (dist > 64.0D) {
                this.vanishWithSound();
                return;
            }

            int hbInterval = Mth.clamp((int) (dist * 1.5D), 12, 40);
            if (dist < 32.0D && this.tickCount % hbInterval == 0) {
                float vol = (float) Mth.clamp(1.25D - (dist / 34.0D), 0.25D, 1.0D);
                this.level().playSound(null, player.blockPosition(),
                        SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, vol, 0.52F);
            }
            if (dist < 28.0D && this.tickCount % 65 == 0 && !this.isRaging()) {
                this.level().playSound(null, this.blockPosition(),
                        SoundEvents.WARDEN_AMBIENT, SoundSource.AMBIENT, 0.85F, 0.50F);
                this.level().playSound(null, this.blockPosition(),
                        SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.AMBIENT, 0.80F, 0.50F);
            }

            if (this.isConvulsing()) {
                this.getNavigation().stop();
                this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
                this.lockBodyAndGazeToPlayer(player);
                if (--this.convulseTicks <= 0) {
                    this.setConvulsing(false);
                    if (!this.isRaging()) {
                        this.triggerRage(player);
                    }
                }
                return;
            }

            if (this.isContorting()) {
                this.getNavigation().stop();
                this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
                this.lockBodyAndGazeToPlayer(player);

                if (this.contortTicks == 80 || this.contortTicks == 25) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0, false, false));
                }
                if (this.contortTicks == 50) {
                    this.playLowAgonyGroan(1.4F);
                }

                if (--this.contortTicks <= 0) {
                    this.setContorting(false);
                    this.triggerRage(player);
                }
                return;
            }

            if (this.isRaging()) {
                if (this.tickCount % 5 == 0 && this.getDeltaMovement().horizontalDistanceSqr() > 0.002D) {
                    this.level().playSound(null, this.blockPosition(),
                            SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 1.25F, 0.80F + this.random.nextFloat() * 0.15F);
                    this.level().playSound(null, this.blockPosition(),
                            SoundEvents.DEEPSLATE_STEP, SoundSource.HOSTILE, 1.0F, 0.70F + this.random.nextFloat() * 0.15F);
                }

                if (this.tickCount % 34 == 0) {
                    this.playLowAgonyGroan(1.85F);
                }

                if (dist < 15.0D && this.tickCount % 55 == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0, false, false));
                }

                if (this.tickCount % 10 == 0) {
                    extinguishTorchesAndBreakDoors();
                }
                return;
            }

            if (isPlayerLookingAtMe(player, 0.30D)) {
                this.startFiveSecondContortion(player);
                return;
            }

            this.unseenStalkTicks++;

            if (this.tickCount % 14 == 0 && this.getDeltaMovement().horizontalDistanceSqr() > 0.001D) {
                this.level().playSound(null, this.blockPosition(),
                        SoundEvents.WARDEN_STEP, SoundSource.HOSTILE, 0.75F, 0.70F);
            }

            if (dist < 24.0D && this.unseenStalkTicks % 75 == 0) {
                Vec3 look = player.getLookAngle();
                BlockPos phantomPos = BlockPos.containing(
                        player.getX() - look.x * 2.5D,
                        player.getY() + 1.2D,
                        player.getZ() - look.z * 2.5D
                );
                this.level().playSound(null, phantomPos,
                        SoundEvents.DEEPSLATE_STEP, SoundSource.AMBIENT, 0.85F, 0.65F);
            }

            if (dist > 26.0D && dist <= 32.0D && this.shadowStepCooldown == 0 && this.unseenStalkTicks > 80) {
                tryShadowStepBehind(player, 16.0D);
            }
        }

        private void extinguishTorchesAndBreakDoors() {
            BlockPos center = this.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 2, 3))) {
                BlockState state = this.level().getBlockState(pos);
                if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                        || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
                    this.level().destroyBlock(pos, true);
                    break;
                }
                if (state.getBlock() instanceof DoorBlock door && !door.isOpen(state) && pos.distSqr(center) <= 5.0D) {
                    door.setOpen(this, this.level(), state, pos, true);
                    this.level().playSound(null, pos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.2F, 0.65F);
                    break;
                }
            }
        }

        private void tryShadowStepBehind(Player player, double targetDist) {
            Vec3 look = player.getLookAngle();
            double angle = (this.random.nextDouble() - 0.5D) * 1.1D;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double rx = look.x * cos - look.z * sin;
            double rz = look.x * sin + look.z * cos;

            BlockPos candidate = BlockPos.containing(
                    player.getX() - rx * targetDist,
                    player.getY(),
                    player.getZ() - rz * targetDist
            );
            BlockPos ground = findGround(this.level(), candidate);
            if (ground != null && this.level().getBrightness(LightLayer.BLOCK, ground) <= 5) {
                this.teleportTo(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D);
                this.lockBodyAndGazeToPlayer(player);
                this.shadowStepCooldown = 140;
            }
        }

        private boolean isPlayerLookingAtMe(Player player, double threshold) {
            Vec3 viewVec = player.getViewVector(1.0F).normalize();
            Vec3 toEntity = this.getEyePosition().subtract(player.getEyePosition()).normalize();
            double dot = viewVec.dot(toEntity);
            if (dot < threshold) return false;
            return player.hasLineOfSight(this);
        }

        public void triggerRage(Player player) {
            if (this.isRaging()) return;
            this.setRaging(true);
            this.setContorting(false);
            this.setConvulsing(false);

            AttributeInstance speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(0.345D);
            }

            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
            this.playBoneCrunchSound(1.6F);
            this.playLowAgonyGroan(2.0F);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.5F, 0.52F);
            this.level().playSound(null, this.blockPosition(),
                    SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 1.4F, 0.55F);

            if (player instanceof ServerPlayer sp) {
                sp.playNotifySound(SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 1.2F, 0.50F);
            }
        }

        public void vanishWithSound() {
            if (!this.level().isClientSide) {
                scheduleNextSpawnCooldown(this.level());
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 0.9F, 0.50F);
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.4D, this.getZ(),
                        40, 0.35D, 0.9D, 0.35D, 0.03D);
            }
            this.discard();
        }

        @Override
        public float getVoicePitch() {
            return 0.50F + this.random.nextFloat() * 0.08F;
        }

        @Override
        protected SoundEvent getAmbientSound() {
            return SoundEvents.WARDEN_AMBIENT;
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource ds) {
            return SoundEvents.BONE_BLOCK_BREAK;
        }

        @Override
        protected SoundEvent getDeathSound() {
            return SoundEvents.WARDEN_DEATH;
        }

        public static boolean checkWhispererSpawnRules(EntityType<WhispererEntity> type, ServerLevelAccessor level,
                                                      MobSpawnType spawnType, BlockPos pos, RandomSource random) {
            return spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND;
        }
    }

    private static class DirectStalkGoal extends Goal {
        private final WhispererEntity mob;
        private int repathCooldown = 0;

        public DirectStalkGoal(WhispererEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = this.mob.getTarget();
            return t != null && t.isAlive() && !this.mob.isRaging() && !this.mob.isContorting() && !this.mob.isConvulsing();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.repathCooldown = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) return;

            this.mob.lockBodyAndGazeToPlayer(target);

            if (--this.repathCooldown > 0) return;
            this.repathCooldown = 10;

            double dist = this.mob.distanceTo(target);
            if (dist > 6.5D) {
                this.mob.getNavigation().moveTo(target, 0.92D);
            } else {
                this.mob.getNavigation().stop();
            }
        }
    }

    private static class WhispererChaseGoal extends MeleeAttackGoal {
        private final WhispererEntity whisperer;

        public WhispererChaseGoal(WhispererEntity mob, double speedModifier, boolean followUnseen) {
            super(mob, speedModifier, followUnseen);
            this.whisperer = mob;
        }

        @Override
        public boolean canUse() {
            return this.whisperer.isRaging()
                    && !this.whisperer.isContorting()
                    && !this.whisperer.isConvulsing()
                    && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.whisperer.isRaging()
                    && !this.whisperer.isContorting()
                    && !this.whisperer.isConvulsing()
                    && super.canContinueToUse();
        }

        @Override
        protected double getAttackReachSqr(LivingEntity attackTarget) {
            return 4.5D + attackTarget.getBbWidth();
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientSetup {
        public static final ModelLayerLocation WHISPERER_LAYER =
                new ModelLayerLocation(new ResourceLocation(MODID, "whisperer"), "main");

        @SubscribeEvent
        public static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(WHISPERER_LAYER, WhispererModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(WHISPERER.get(), WhispererRenderer::new);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class WhispererModel extends HierarchicalModel<WhispererEntity> {
        private final ModelPart root;
        private final ModelPart pelvis;
        private final ModelPart abdomen;
        private final ModelPart thorax;
        private final ModelPart neckLower;
        private final ModelPart neckUpper;
        private final ModelPart headHood;
        private final ModelPart leftArmUpper;
        private final ModelPart leftArmLower;
        private final ModelPart rightArmUpper;
        private final ModelPart rightArmLower;
        private final ModelPart leftLegUpper;
        private final ModelPart leftLegLower;
        private final ModelPart rightLegUpper;
        private final ModelPart rightLegLower;

        public WhispererModel(ModelPart root) {
            this.root = root;
            this.pelvis = root.getChild("pelvis");
            this.abdomen = this.pelvis.getChild("abdomen");
            this.thorax = this.abdomen.getChild("thorax");
            this.neckLower = this.thorax.getChild("neck_lower");
            this.neckUpper = this.neckLower.getChild("neck_upper");
            this.headHood = this.neckUpper.getChild("head_hood");
            this.leftArmUpper = this.thorax.getChild("left_arm_upper");
            this.leftArmLower = this.leftArmUpper.getChild("left_arm_lower");
            this.rightArmUpper = this.thorax.getChild("right_arm_upper");
            this.rightArmLower = this.rightArmUpper.getChild("right_arm_lower");
            this.leftLegUpper = this.pelvis.getChild("left_leg_upper");
            this.leftLegLower = this.leftLegUpper.getChild("left_leg_lower");
            this.rightLegUpper = this.pelvis.getChild("right_leg_upper");
            this.rightLegLower = this.rightLegUpper.getChild("right_leg_lower");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();

            PartDefinition pelvis = root.addOrReplaceChild("pelvis",
                    CubeListBuilder.create().texOffs(0, 36).addBox(-3.5F, -2.5F, -2.0F, 7.0F, 5.0F, 4.0F),
                    PartPose.offset(0.0F, -2.0F, 0.0F));

            PartDefinition abdomen = pelvis.addOrReplaceChild("abdomen",
                    CubeListBuilder.create().texOffs(22, 36).addBox(-3.0F, -7.5F, -1.75F, 6.0F, 9.5F, 3.5F),
                    PartPose.offset(0.0F, -2.0F, 0.0F));

            PartDefinition thorax = abdomen.addOrReplaceChild("thorax",
                    CubeListBuilder.create()
                            .texOffs(0, 20).addBox(-4.5F, -10.5F, -2.5F, 9.0F, 12.5F, 5.0F)
                            .texOffs(28, 20).addBox(-4.0F, -9.5F, -2.8F, 8.0F, 9.0F, 1.0F),
                    PartPose.offset(0.0F, -7.0F, 0.0F));

            PartDefinition neckLower = thorax.addOrReplaceChild("neck_lower",
                    CubeListBuilder.create().texOffs(48, 20).addBox(-2.1F, -4.5F, -1.75F, 4.2F, 6.5F, 3.5F),
                    PartPose.offset(0.0F, -10.0F, 0.0F));

            PartDefinition neckUpper = neckLower.addOrReplaceChild("neck_upper",
                    CubeListBuilder.create().texOffs(48, 30).addBox(-1.9F, -4.5F, -1.6F, 3.8F, 6.5F, 3.2F),
                    PartPose.offset(0.0F, -4.0F, 0.0F));

            neckUpper.addOrReplaceChild("head_hood",
                    CubeListBuilder.create()
                            .texOffs(0, 0).addBox(-4.5F, -9.5F, -4.0F, 9.0F, 11.5F, 8.0F, new CubeDeformation(0.15F))
                            .texOffs(34, 0).addBox(-4.0F, -9.0F, -3.7F, 8.0F, 10.5F, 7.0F),
                    PartPose.offset(0.0F, -3.8F, 0.0F));

            PartDefinition leftArmUpper = thorax.addOrReplaceChild("left_arm_upper",
                    CubeListBuilder.create().texOffs(0, 46).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 14.5F, 3.0F),
                    PartPose.offset(4.0F, -9.0F, 0.0F));

            leftArmUpper.addOrReplaceChild("left_arm_lower",
                    CubeListBuilder.create()
                            .texOffs(12, 46).addBox(-1.3F, -2.0F, -1.3F, 2.6F, 15.0F, 2.6F)
                            .texOffs(24, 46).addBox(-1.1F, 12.0F, -1.1F, 2.2F, 6.5F, 2.2F),
                    PartPose.offset(0.0F, 11.5F, 0.0F));

            PartDefinition rightArmUpper = thorax.addOrReplaceChild("right_arm_upper",
                    CubeListBuilder.create().texOffs(34, 46).addBox(-1.5F, -2.0F, -1.5F, 3.0F, 14.5F, 3.0F),
                    PartPose.offset(-4.0F, -9.0F, 0.0F));

            rightArmUpper.addOrReplaceChild("right_arm_lower",
                    CubeListBuilder.create()
                            .texOffs(46, 46).addBox(-1.3F, -2.0F, -1.3F, 2.6F, 15.0F, 2.6F)
                            .texOffs(58, 46).addBox(-1.1F, 12.0F, -1.1F, 2.2F, 6.5F, 2.2F),
                    PartPose.offset(0.0F, 11.5F, 0.0F));

            PartDefinition leftLegUpper = pelvis.addOrReplaceChild("left_leg_upper",
                    CubeListBuilder.create().texOffs(0, 66).addBox(-1.55F, -2.0F, -1.55F, 3.1F, 14.5F, 3.1F),
                    PartPose.offset(2.1F, 1.5F, 0.0F));

            leftLegUpper.addOrReplaceChild("left_leg_lower",
                    CubeListBuilder.create().texOffs(14, 66).addBox(-1.35F, -2.0F, -1.35F, 2.7F, 14.5F, 2.7F),
                    PartPose.offset(0.0F, 12.0F, 0.0F));

            PartDefinition rightLegUpper = pelvis.addOrReplaceChild("right_leg_upper",
                    CubeListBuilder.create().texOffs(28, 66).addBox(-1.55F, -2.0F, -1.55F, 3.1F, 14.5F, 3.1F),
                    PartPose.offset(-2.1F, 1.5F, 0.0F));

            rightLegUpper.addOrReplaceChild("right_leg_lower",
                    CubeListBuilder.create().texOffs(42, 66).addBox(-1.35F, -2.0F, -1.35F, 2.7F, 14.5F, 2.7F),
                    PartPose.offset(0.0F, 12.0F, 0.0F));

            return LayerDefinition.create(mesh, 128, 128);
        }

        @Override
        public ModelPart root() { return this.root; }

        @Override
        public void setupAnim(WhispererEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            this.root().getAllParts().forEach(ModelPart::resetPose);

            float yawRad = Mth.clamp(netHeadYaw * ((float) Math.PI / 180.0F), -0.85F, 0.85F);
            float pitchRad = Mth.clamp(headPitch * ((float) Math.PI / 180.0F), -0.60F, 0.60F);

            if (entity.isContorting() || entity.isConvulsing()) {
                float dir = (float) entity.getPoseDir();

                this.pelvis.y = -0.6F;
                this.pelvis.zRot = 0.04F * dir;

                this.abdomen.xRot = 0.16F;
                this.abdomen.zRot = 0.12F * dir;

                this.thorax.xRot = 0.24F;
                this.thorax.zRot = 0.15F * dir;

                this.neckLower.xRot = 0.10F;
                this.neckLower.zRot = 0.16F * dir;
                this.neckUpper.xRot = -0.22F;
                this.neckUpper.zRot = 0.20F * dir;
                this.headHood.xRot = -0.16F + pitchRad * 0.5F;
                this.headHood.zRot = 0.34F * dir;

                this.leftArmUpper.xRot = -0.92F;
                this.leftArmUpper.yRot = 0.25F;
                this.leftArmUpper.zRot = -0.20F;
                this.leftArmLower.xRot = -1.22F;
                this.leftArmLower.yRot = 0.32F;
                this.leftArmLower.zRot = 0.15F;

                this.rightArmUpper.xRot = -0.78F;
                this.rightArmUpper.yRot = -0.22F;
                this.rightArmUpper.zRot = 0.22F;
                this.rightArmLower.xRot = -1.12F;
                this.rightArmLower.yRot = -0.28F;
                this.rightArmLower.zRot = -0.15F;

                this.leftLegUpper.xRot = -0.28F;
                this.leftLegUpper.zRot = -0.05F;
                this.leftLegLower.xRot = 0.52F;

                this.rightLegUpper.xRot = -0.16F;
                this.rightLegUpper.zRot = 0.05F;
                this.rightLegLower.xRot = 0.34F;
                return;
            }

            if (entity.isRaging()) {
                float runCycle = limbSwing * 0.80F;
                float runStride = Math.max(Mth.clamp(limbSwingAmount * 1.25F, 0.0F, 1.0F), 0.70F);

                this.pelvis.y = -1.1F + Mth.sin(runCycle * 2.0F) * 0.42F * runStride;
                this.pelvis.yRot = Mth.cos(runCycle) * 0.06F * runStride;

                this.abdomen.xRot = 0.26F + Mth.sin(runCycle * 2.0F) * 0.04F * runStride;
                this.thorax.xRot = 0.34F + Mth.sin(runCycle * 2.0F) * 0.06F * runStride;
                this.thorax.yRot = Mth.cos(runCycle) * 0.15F * runStride;

                this.neckLower.xRot = -0.20F;
                this.neckLower.yRot = yawRad * 0.30F;
                this.neckUpper.xRot = -0.28F;
                this.neckUpper.yRot = yawRad * 0.35F;
                this.headHood.xRot = -0.10F + pitchRad * 0.70F;
                this.headHood.yRot = yawRad * 0.35F;
                this.headHood.zRot = 0.20F * entity.getPoseDir() + Mth.sin(runCycle) * 0.05F;

                this.leftLegUpper.xRot = -0.18F + Mth.cos(runCycle) * 1.02F * runStride;
                this.rightLegUpper.xRot = -0.18F + Mth.cos(runCycle + (float) Math.PI) * 1.02F * runStride;
                this.leftLegLower.xRot = 0.28F + Math.max(0.0F, Mth.sin(runCycle)) * 0.82F * runStride;
                this.rightLegLower.xRot = 0.28F + Math.max(0.0F, Mth.sin(runCycle + (float) Math.PI)) * 0.82F * runStride;

                this.leftArmUpper.xRot = -0.76F + Mth.cos(runCycle + (float) Math.PI) * 0.52F * runStride;
                this.rightArmUpper.xRot = -0.76F + Mth.cos(runCycle) * 0.52F * runStride;
                this.leftArmUpper.zRot = -0.14F;
                this.rightArmUpper.zRot = 0.14F;

                this.leftArmLower.xRot = -0.72F + Mth.sin(runCycle + (float) Math.PI) * 0.28F * runStride;
                this.rightArmLower.xRot = -0.72F + Mth.sin(runCycle) * 0.28F * runStride;
                this.leftArmLower.yRot = 0.18F;
                this.rightArmLower.yRot = -0.18F;
            } else {
                float walkCycle = limbSwing * 0.6662F;
                float stride = Mth.clamp(limbSwingAmount * 1.15F, 0.0F, 1.0F);
                float breath = Mth.sin(ageInTicks * 0.08F);

                this.pelvis.y = -1.8F + Mth.abs(Mth.sin(walkCycle)) * 0.40F * stride;
                this.abdomen.xRot = 0.12F + breath * 0.03F;
                this.thorax.xRot = 0.18F + breath * 0.04F;

                this.neckLower.xRot = -0.08F;
                this.neckUpper.xRot = -0.14F;
                this.headHood.xRot = -0.06F + pitchRad * 0.65F;
                this.headHood.zRot = 0.14F;

                this.leftLegUpper.xRot = -0.08F + Mth.cos(walkCycle) * 0.65F * stride;
                this.rightLegUpper.xRot = -0.08F + Mth.cos(walkCycle + (float) Math.PI) * 0.65F * stride;
                this.leftLegLower.xRot = 0.16F + Math.max(0.0F, Mth.sin(walkCycle)) * 0.52F * stride;
                this.rightLegLower.xRot = 0.16F + Math.max(0.0F, Mth.sin(walkCycle + (float) Math.PI)) * 0.52F * stride;

                this.leftArmUpper.xRot = -0.22F + Mth.cos(walkCycle + (float) Math.PI) * 0.30F * stride;
                this.rightArmUpper.xRot = -0.22F + Mth.cos(walkCycle) * 0.30F * stride;
                this.leftArmUpper.zRot = -0.12F;
                this.rightArmUpper.zRot = 0.12F;

                this.leftArmLower.xRot = -0.42F;
                this.rightArmLower.xRot = -0.42F;
            }

            if (this.attackTime > 0.0F) {
                float swing = Mth.sin(this.attackTime * (float) Math.PI);
                this.thorax.xRot += swing * 0.28F;
                this.leftArmUpper.xRot -= swing * 0.85F;
                this.rightArmUpper.xRot -= swing * 0.85F;
                this.leftArmLower.xRot -= swing * 0.35F;
                this.rightArmLower.xRot -= swing * 0.35F;
            }
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                                   int packedOverlay, float red, float green, float blue, float alpha) {
            super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, 0.0F, 0.0F, 0.0F, 1.0F);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class WhispererRenderer extends MobRenderer<WhispererEntity, WhispererModel> {
        private static ResourceLocation PITCH_BLACK_TEX;

        public WhispererRenderer(EntityRendererProvider.Context context) {
            super(context, new WhispererModel(context.bakeLayer(ClientSetup.WHISPERER_LAYER)), 0.5F);
            ensurePitchBlackTexture();
        }

        private static void ensurePitchBlackTexture() {
            if (PITCH_BLACK_TEX != null) return;
            NativeImage blackImg = new NativeImage(128, 128, false);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    blackImg.setPixelRGBA(x, y, 0xFF000000);
                }
            }
            PITCH_BLACK_TEX = Minecraft.getInstance().getTextureManager()
                    .register("whisperer_pitch_black", new DynamicTexture(blackImg));
        }

        @Override
        public ResourceLocation getTextureLocation(WhispererEntity entity) {
            ensurePitchBlackTexture();
            return PITCH_BLACK_TEX;
        }
    }
}
