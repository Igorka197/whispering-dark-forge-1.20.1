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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumSet;

@Mod(WhisperingDarkMod.MODID)
public class WhisperingDarkMod {
    public static final String MODID = "whisperingdark";

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);

    public static final RegistryObject<EntityType<WhispererEntity>> WHISPERER =
            ENTITIES.register("whisperer", () -> EntityType.Builder.of(WhispererEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 2.9F)
                    .clientTrackingRange(64)
                    .build("whisperer"));

    public static final RegistryObject<Item> WHISPERER_SPAWN_EGG =
            ITEMS.register("whisperer_spawn_egg", () ->
                    new ForgeSpawnEggItem(WHISPERER, 0x0A0A0E, 0x3E0A0A, new Item.Properties()));

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

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player.tickCount % 200 != 0) return;

        Player player = event.player;
        Level level = player.level();
        if (level.getSkyDarken() < 8 && !level.isThundering()) return;

        BlockPos ppos = player.blockPosition();
        if (level.getBrightness(LightLayer.BLOCK, ppos) > 0) return;

        if (level.random.nextFloat() < 0.15F) {
            Vec3 look = player.getLookAngle();
            double dist = 16.0 + level.random.nextDouble() * 12.0;
            double angle = (level.random.nextDouble() - 0.5) * 1.2;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double rx = look.x * cos - look.z * sin;
            double rz = look.x * sin + look.z * cos;

            BlockPos spawnPos = BlockPos.containing(player.getX() - rx * dist, player.getY(), player.getZ() - rz * dist);
            BlockPos valid = findGround(level, spawnPos);
            if (valid != null && level.getBrightness(LightLayer.BLOCK, valid) == 0) {
                WhispererEntity entity = WHISPERER.get().create(level);
                if (entity != null) {
                    entity.moveTo(valid.getX() + 0.5, valid.getY(), valid.getZ() + 0.5,
                            level.random.nextFloat() * 360.0F, 0.0F);
                    level.addFreshEntity(entity);
                }
            }
        }
    }

    private BlockPos findGround(Level level, BlockPos pos) {
        for (int dy = -3; dy <= 3; dy++) {
            BlockPos p = pos.above(dy);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.below()).isSolidRender(level, p.below())) {
                return p;
            }
        }
        return null;
    }

    public static class WhispererEntity extends Monster {
        private static final EntityDataAccessor<Boolean> RAGING =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> FROZEN =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);

        private int stalkTimer = 0;
        private int lookAwayTimer = 0;

        public WhispererEntity(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.setMaxUpStep(1.0F);
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(RAGING, false);
            this.entityData.define(FROZEN, false);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 60.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.23D)
                    .add(Attributes.ATTACK_DAMAGE, 12.0D)
                    .add(Attributes.FOLLOW_RANGE, 40.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
        }

        public boolean isRaging() { return this.entityData.get(RAGING); }
        public void setRaging(boolean v) { this.entityData.set(RAGING, v); }
        public boolean isFrozenByLook() { return this.entityData.get(FROZEN); }
        public void setFrozenByLook(boolean v) { this.entityData.set(FROZEN, v); }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(1, new WhispererChaseGoal(this, 1.8D, true));
            this.goalSelector.addGoal(2, new SilentStalkGoal(this, 1.0D, 3.0F, 32.0F));
            this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 32.0F));
            this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

            this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }

        @Override
        public void tick() {
            super.tick();

            if (this.level().isClientSide) {
                if (this.random.nextFloat() < (isRaging() ? 0.6F : 0.15F)) {
                    double rx = this.getX() + (this.random.nextDouble() - 0.5) * 0.8;
                    double ry = this.getY() + this.random.nextDouble() * 2.8;
                    double rz = this.getZ() + (this.random.nextDouble() - 0.5) * 0.8;
                    this.level().addParticle(ParticleTypes.SMOKE, rx, ry, rz, 0.0D, 0.01D, 0.0D);
                }
                return;
            }

            BlockPos currentPos = this.blockPosition();
            if (this.level().getBrightness(LightLayer.BLOCK, currentPos) > 7) {
                vanishWithSound();
                return;
            }

            LivingEntity target = this.getTarget();
            if (target instanceof Player player) {
                boolean targetLooking = isPlayerLookingAtMe(player);

                if (targetLooking) {
                    this.lookAwayTimer = 0;
                    this.stalkTimer++;
                    this.setFrozenByLook(true);
                    this.getNavigation().stop();

                    if (this.stalkTimer > 60 && !isRaging()) {
                        triggerRage(player);
                    }
                } else {
                    this.setFrozenByLook(false);
                    if (!isRaging()) {
                        this.lookAwayTimer++;
                        if (this.lookAwayTimer > 100) {
                            vanishWithSound();
                            return;
                        }
                    }
                }
            } else {
                this.setFrozenByLook(false);
            }
        }

        private boolean isPlayerLookingAtMe(Player player) {
            Vec3 viewVec = player.getViewVector(1.0F).normalize();
            Vec3 toEntity = this.getEyePosition().subtract(player.getEyePosition()).normalize();
            double dot = viewVec.dot(toEntity);
            if (dot < 0.75D) return false;
            return player.hasLineOfSight(this);
        }

        private void triggerRage(Player player) {
            this.setRaging(true);
            AttributeInstance speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(0.42D);
            }
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    WHISPER_SCREAM.get(), SoundSource.HOSTILE, 2.0F, 0.8F + this.random.nextFloat() * 0.3F);

            if (player instanceof ServerPlayer sp) {
                sp.playNotifySound(SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.0F, 0.5F);
            }
        }

        private void vanishWithSound() {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    WHISPER_DISAPPEAR.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.5, this.getZ(),
                        30, 0.4, 1.0, 0.4, 0.05);
            }
            this.discard();
        }

        @Override
        protected SoundEvent getAmbientSound() {
            return WHISPER_AMBIENT.get();
        }

        @Override
        protected SoundEvent getHurtSound(DamageSource ds) {
            return SoundEvents.ENDERMAN_HURT;
        }

        @Override
        protected SoundEvent getDeathSound() {
            return WHISPER_DISAPPEAR.get();
        }

        public static boolean checkWhispererSpawnRules(EntityType<WhispererEntity> type, ServerLevelAccessor level,
                                                      MobSpawnType spawnType, BlockPos pos, RandomSource random) {
            if (level.getBrightness(LightLayer.BLOCK, pos) > 0) return false;
            return Monster.checkMonsterSpawnRules(type, level, spawnType, pos, random);
        }
    }

    private static class SilentStalkGoal extends Goal {
        private final WhispererEntity mob;
        private final double speed;
        private final float minDist;
        private final float maxDist;

        public SilentStalkGoal(WhispererEntity mob, double speed, float minDist, float maxDist) {
            this.mob = mob;
            this.speed = speed;
            this.minDist = minDist;
            this.maxDist = maxDist;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = this.mob.getTarget();
            if (t == null || !t.isAlive() || this.mob.isRaging() || this.mob.isFrozenByLook()) return false;
            double d = this.mob.distanceTo(t);
            return d > this.minDist && d <= this.maxDist * 1.5D;
        }

        @Override
        public void tick() {
            LivingEntity t = this.mob.getTarget();
            if (t == null) return;
            this.mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (this.mob.distanceTo(t) > this.minDist) this.mob.getNavigation().moveTo(t, this.speed);
            else this.mob.getNavigation().stop();
        }
    }

    private static class WhispererChaseGoal extends MeleeAttackGoal {
        private final WhispererEntity whisperer;

        public WhispererChaseGoal(WhispererEntity mob, double speed, boolean followUnseen) {
            super(mob, speed, followUnseen);
            this.whisperer = mob;
        }

        @Override
        public boolean canUse() { return this.whisperer.isRaging() && super.canUse(); }

        @Override
        public boolean canContinueToUse() { return this.whisperer.isRaging() && super.canContinueToUse(); }
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
        private final ModelPart thorax;
        private final ModelPart headHood;
        private final ModelPart leftArmUpper;
        private final ModelPart leftArmLower;
        private final ModelPart rightArmUpper;
        private final ModelPart rightArmLower;
        private final ModelPart leftLegUpper;
        private final ModelPart rightLegUpper;

        public WhispererModel(ModelPart root) {
            this.root = root;
            ModelPart pelvis = root.getChild("pelvis");
            ModelPart abdomen = pelvis.getChild("abdomen");
            this.thorax = abdomen.getChild("thorax");
            ModelPart neckLower = this.thorax.getChild("neck_lower");
            ModelPart neckUpper = neckLower.getChild("neck_upper");
            this.headHood = neckUpper.getChild("head_hood");
            this.leftArmUpper = this.thorax.getChild("left_arm_upper");
            this.leftArmLower = this.leftArmUpper.getChild("left_arm_lower");
            this.rightArmUpper = this.thorax.getChild("right_arm_upper");
            this.rightArmLower = this.rightArmUpper.getChild("right_arm_lower");
            this.leftLegUpper = pelvis.getChild("left_leg_upper");
            this.rightLegUpper = pelvis.getChild("right_leg_upper");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();

            PartDefinition pelvis = root.addOrReplaceChild("pelvis",
                    CubeListBuilder.create().texOffs(0, 36).addBox(-3.0F, -1.5F, -2.0F, 7.0F, 4.0F, 4.0F),
                    PartPose.offset(0.0F, -2.0F, 0.0F));

            PartDefinition abdomen = pelvis.addOrReplaceChild("abdomen",
                    CubeListBuilder.create().texOffs(22, 36).addBox(-3.0F, -8.0F, -1.75F, 6.0F, 8.0F, 3.5F, new CubeDeformation(-0.15F)),
                    PartPose.offsetAndRotation(0.0F, -1.5F, 0.0F, 0.087F, 0.0F, 0.0F));

            PartDefinition thorax = abdomen.addOrReplaceChild("thorax",
                    CubeListBuilder.create()
                            .texOffs(0, 20).addBox(-4.5F, -11.0F, -2.5F, 9.0F, 11.0F, 5.0F)
                            .texOffs(28, 20).addBox(-4.0F, -10.0F, -2.75F, 8.0F, 9.0F, 1.0F, new CubeDeformation(0.1F)),
                    PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, 0.218F, 0.0F, 0.0F));

            PartDefinition neckLower = thorax.addOrReplaceChild("neck_lower",
                    CubeListBuilder.create().texOffs(48, 20).addBox(-2.0F, -4.0F, -1.5F, 4.0F, 4.0F, 3.5F, new CubeDeformation(-0.2F)),
                    PartPose.offsetAndRotation(0.0F, -11.0F, -0.5F, 0.26F, 0.0F, 0.0F));

            PartDefinition neckUpper = neckLower.addOrReplaceChild("neck_upper",
                    CubeListBuilder.create().texOffs(48, 28).addBox(-1.75F, -4.0F, -1.5F, 3.5F, 4.0F, 3.25F, new CubeDeformation(-0.25F)),
                    PartPose.offsetAndRotation(0.0F, -3.5F, 0.0F, -0.13F, 0.0F, -0.087F));

            neckUpper.addOrReplaceChild("head_hood",
                    CubeListBuilder.create()
                            .texOffs(0, 0).addBox(-4.5F, -10.5F, -4.0F, 9.0F, 11.0F, 8.0F, new CubeDeformation(0.25F))
                            .texOffs(34, 0).addBox(-4.0F, -10.0F, -3.7F, 8.0F, 10.0F, 7.0F, new CubeDeformation(-0.15F)),
                    PartPose.offset(0.0F, -3.5F, 0.0F));

            PartDefinition leftArmUpper = thorax.addOrReplaceChild("left_arm_upper",
                    CubeListBuilder.create().texOffs(0, 44).addBox(-0.5F, 0.0F, -1.5F, 3.0F, 14.0F, 3.0F, new CubeDeformation(-0.15F)),
                    PartPose.offsetAndRotation(5.0F, -10.0F, 0.0F, 0.0F, 0.0F, -0.07F));

            leftArmUpper.addOrReplaceChild("left_arm_lower",
                    CubeListBuilder.create()
                            .texOffs(12, 44).addBox(-1.25F, 0.0F, -1.25F, 2.5F, 15.0F, 2.5F, new CubeDeformation(-0.2F))
                            .texOffs(22, 48).addBox(-1.0F, 14.5F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(-0.25F)),
                    PartPose.offsetAndRotation(1.0F, 13.5F, 0.0F, -0.14F, 0.0F, 0.0F));

            PartDefinition rightArmUpper = thorax.addOrReplaceChild("right_arm_upper",
                    CubeListBuilder.create().texOffs(30, 44).addBox(-2.5F, 0.0F, -1.5F, 3.0F, 14.0F, 3.0F, new CubeDeformation(-0.15F)),
                    PartPose.offsetAndRotation(-5.0F, -10.0F, 0.0F, 0.0F, 0.0F, 0.07F));

            rightArmUpper.addOrReplaceChild("right_arm_lower",
                    CubeListBuilder.create()
                            .texOffs(42, 44).addBox(-1.25F, 0.0F, -1.25F, 2.5F, 15.0F, 2.5F, new CubeDeformation(-0.2F))
                            .texOffs(52, 48).addBox(-1.0F, 14.5F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(-0.25F)),
                    PartPose.offsetAndRotation(-1.0F, 13.5F, 0.0F, -0.14F, 0.0F, 0.0F));

            pelvis.addOrReplaceChild("left_leg_upper",
                    CubeListBuilder.create().texOffs(0, 62).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 26.0F, 3.0F, new CubeDeformation(-0.1F)),
                    PartPose.offset(2.2F, 2.0F, 0.0F));

            pelvis.addOrReplaceChild("right_leg_upper",
                    CubeListBuilder.create().texOffs(22, 62).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 26.0F, 3.0F, new CubeDeformation(-0.1F)),
                    PartPose.offset(-2.2F, 2.0F, 0.0F));

            return LayerDefinition.create(mesh, 128, 128);
        }

        @Override
        public ModelPart root() { return this.root; }

        @Override
        public void setupAnim(WhispererEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            this.root().getAllParts().forEach(ModelPart::resetPose);

            float qT = (float) Math.floor(ageInTicks * 0.6F) / 0.6F;
            float sn = Mth.sin(ageInTicks * 3.1F) * Mth.cos(ageInTicks * 5.3F);

            if (sn > 0.6F) {
                float jX = (Mth.sin(qT * 17.0F) * 0.12F);
                float jZ = (Mth.cos(qT * 23.0F) * 0.12F);
                this.headHood.x += jX;
                this.headHood.z += jZ;
            }

            if (entity.isRaging()) {
                float shake = Mth.sin(ageInTicks * 1.8F) * 0.08F;
                this.thorax.xRot += 0.3F + shake;
                this.leftArmUpper.xRot = -1.2F + Mth.sin(limbSwing * 0.6662F) * 0.4F;
                this.rightArmUpper.xRot = -1.2F - Mth.sin(limbSwing * 0.6662F) * 0.4F;
            } else if (entity.isFrozenByLook()) {
                this.thorax.xRot = 0.1F;
                this.leftArmUpper.zRot = -0.05F;
                this.rightArmUpper.zRot = 0.05F;
            } else {
                this.leftArmUpper.xRot = Mth.cos(limbSwing * 0.6662F) * 0.2F * limbSwingAmount;
                this.rightArmUpper.xRot = Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * 0.2F * limbSwingAmount;
                this.leftLegUpper.xRot = Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * 0.4F * limbSwingAmount;
                this.rightLegUpper.xRot = Mth.cos(limbSwing * 0.6662F) * 0.4F * limbSwingAmount;
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class WhispererRenderer extends MobRenderer<WhispererEntity, WhispererModel> {
        private static ResourceLocation PROCEDURAL_BASE_TEX;
        private static ResourceLocation PROCEDURAL_EYES_TEX;

        public WhispererRenderer(EntityRendererProvider.Context context) {
            super(context, new WhispererModel(context.bakeLayer(ClientSetup.WHISPERER_LAYER)), 0.5F);
            ensureProceduralTextures();
            this.addLayer(new WhispererEyesLayer(this));
        }

        static void ensureProceduralTextures() {
            if (PROCEDURAL_BASE_TEX != null) return;
            NativeImage baseImg = new NativeImage(128, 128, false);
            NativeImage eyesImg = new NativeImage(128, 128, true);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int v = 14 + ((x * 37 + y * 53) & 15);
                    int abgr = (255 << 24) | ((v + 4) << 16) | (v << 8) | v;
                    baseImg.setPixelRGBA(x, y, abgr);
                    if (y >= 11 && y <= 13 && ((x >= 10 && x <= 12) || (x >= 18 && x <= 20))) {
                        eyesImg.setPixelRGBA(x, y, (230 << 24) | (200 << 16) | (210 << 8) | 255);
                    } else {
                        eyesImg.setPixelRGBA(x, y, 0);
                    }
                }
            }
            PROCEDURAL_BASE_TEX = Minecraft.getInstance().getTextureManager()
                    .register("whisperer_base", new DynamicTexture(baseImg));
            PROCEDURAL_EYES_TEX = Minecraft.getInstance().getTextureManager()
                    .register("whisperer_eyes", new DynamicTexture(eyesImg));
        }

        @Override
        public ResourceLocation getTextureLocation(WhispererEntity entity) {
            ensureProceduralTextures();
            return PROCEDURAL_BASE_TEX;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class WhispererEyesLayer extends RenderLayer<WhispererEntity, WhispererModel> {
        public WhispererEyesLayer(RenderLayerParent<WhispererEntity, WhispererModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           WhispererEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            WhispererRenderer.ensureProceduralTextures();
            VertexConsumer vc = buffer.getBuffer(RenderType.eyes(WhispererRenderer.PROCEDURAL_EYES_TEX));
            this.getParentModel().renderToBuffer(poseStack, vc, 15728880,
                    OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
