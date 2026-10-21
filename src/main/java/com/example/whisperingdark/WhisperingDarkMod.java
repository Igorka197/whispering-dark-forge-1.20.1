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
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
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

    public static final RegistryObject<EntityType<WhispererEntity>> WHISPERER = ENTITIES.register("whisperer",
            () -> EntityType.Builder.of(WhispererEntity::new, MobCategory.MONSTER)
                    .sized(0.85F, 3.55F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build(new ResourceLocation(MODID, "whisperer").toString()));

    public static final RegistryObject<Item> WHISPERER_SPAWN_EGG = ITEMS.register("whisperer_spawn_egg",
            () -> new ForgeSpawnEggItem(WHISPERER, 0x050507, 0x7A161C, new Item.Properties()));

    public WhisperingDarkMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::addCreativeTab);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(modEventBus);
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WHISPERER.get(), WhispererEntity.createAttributes().build());
    }

    private void addCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(WHISPERER_SPAWN_EGG);
        }
    }

    public static class WhispererEntity extends PathfinderMob {
        private static final EntityDataAccessor<Boolean> DATA_STALKING =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> DATA_RAGING =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> DATA_FROZEN =
                SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);

        private int stareTicks = 0;
        private int ambianceCooldown = 80;

        public WhispererEntity(EntityType<? extends PathfinderMob> type, Level level) {
            super(type, level);
            this.setMaxUpStep(1.5F);
            this.setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
            this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 0.0F);
            this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, 0.0F);
            this.noCulling = true;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return PathfinderMob.createMobAttributes()
                    .add(Attributes.MAX_HEALTH, 1024.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.28D)
                    .add(Attributes.ATTACK_DAMAGE, 12.0D)
                    .add(Attributes.FOLLOW_RANGE, 64.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                    .add(Attributes.ARMOR, 30.0D);
        }

        @Override
        protected void defineSynchedData() {
            super.defineSynchedData();
            this.entityData.define(DATA_STALKING, true);
            this.entityData.define(DATA_RAGING, false);
            this.entityData.define(DATA_FROZEN, false);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) { return false; }
        @Override
        public boolean isInvulnerableTo(DamageSource source) { return true; }
        @Override
        public boolean isInvulnerable() { return true; }
        @Override
        public void setHealth(float health) { super.setHealth(this.getMaxHealth()); }
        @Override
        public void die(DamageSource source) {}
        @Override
        protected void tickDeath() {}
        @Override
        public boolean fireImmune() { return true; }
        @Override
        public boolean canBreatheUnderwater() { return true; }
        @Override
        public boolean causeFallDamage(float d, float m, DamageSource s) { return false; }
        @Override
        protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) { this.resetFallDistance(); }
        @Override
        public boolean canBeAffected(MobEffectInstance effect) { return false; }
        @Override
        public void knockback(double s, double x, double z) {}
        @Override
        public void push(Entity entity) {}
        @Override
        public boolean isPushable() { return false; }
        @Override
        public boolean ignoreExplosion() { return true; }
        @Override
        public boolean removeWhenFarAway(double dist) { return false; }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(1, new FreezeWhenLookedAtGoal(this));
            this.goalSelector.addGoal(2, new WhispererChaseGoal(this, 1.45D, true));
            this.goalSelector.addGoal(3, new SilentStalkGoal(this, 0.85D, 14.0F, 36.0F));
            this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 48.0F, 1.0F));
            this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D));
            this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
        }

        @Override
        public void tick() {
            super.tick();
            if (!this.level().isClientSide) {
                LivingEntity target = this.getTarget();
                if (target instanceof Player player && !player.isCreative() && !player.isSpectator()) {
                    boolean lookedAt = isPlayerLookingAtMe(player);
                    this.setFrozenByLook(lookedAt && !this.isRaging());
                    if (lookedAt) {
                        stareTicks++;
                        if (stareTicks > 60 || this.distanceTo(player) < 8.0F) {
                            triggerRage(player);
                        }
                    } else {
                        stareTicks = Math.max(0, stareTicks - 1);
                    }
                    if (--ambianceCooldown <= 0) {
                        ambianceCooldown = 120 + this.random.nextInt(100);
                        this.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_NEARBY_CLOSEST, SoundSource.HOSTILE, 0.65F, 0.5F);
                        if (this.distanceTo(player) < 20.0F) {
                            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                        }
                    }
                } else {
                    this.setFrozenByLook(false);
                    this.setRaging(false);
                    this.setStalking(true);
                }
            }
        }

        private void triggerRage(Player player) {
            if (!this.isRaging()) {
                this.setRaging(true);
                this.setStalking(false);
                this.setFrozenByLook(false);
                this.level().playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.2F, 0.4F);
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0, false, false));
            }
        }

        public boolean isPlayerLookingAtMe(Player player) {
            Vec3 viewVec = player.getViewVector(1.0F).normalize();
            Vec3 toEntity = new Vec3(this.getX() - player.getX(), this.getEyeY() - player.getEyeY(), this.getZ() - player.getZ());
            double dist = toEntity.length();
            if (dist > 48.0D || dist < 0.0001D) return false;
            return viewVec.dot(toEntity.normalize()) > 1.0D - 0.035D / dist && player.hasLineOfSight(this);
        }

        public boolean isStalking() { return this.entityData.get(DATA_STALKING); }
        public void setStalking(boolean val) { this.entityData.set(DATA_STALKING, val); }
        public boolean isRaging() { return this.entityData.get(DATA_RAGING); }
        public void setRaging(boolean val) { this.entityData.set(DATA_RAGING, val); }
        public boolean isFrozenByLook() { return this.entityData.get(DATA_FROZEN); }
        public void setFrozenByLook(boolean val) { this.entityData.set(DATA_FROZEN, val); }

        @Override
        public void addAdditionalSaveData(CompoundTag tag) {
            super.addAdditionalSaveData(tag);
            tag.putBoolean("IsStalking", this.isStalking());
            tag.putBoolean("IsRaging", this.isRaging());
        }

        @Override
        public void readAdditionalSaveData(CompoundTag tag) {
            super.readAdditionalSaveData(tag);
            if (tag.contains("IsStalking")) this.setStalking(tag.getBoolean("IsStalking"));
            if (tag.contains("IsRaging")) this.setRaging(tag.getBoolean("IsRaging"));
        }
    }

    private static class FreezeWhenLookedAtGoal extends Goal {
        private final WhispererEntity mob;
        public FreezeWhenLookedAtGoal(WhispererEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }
        @Override
        public boolean canUse() { return this.mob.isFrozenByLook() && !this.mob.isRaging() && this.mob.getTarget() != null; }
        @Override
        public void start() { this.mob.getNavigation().stop(); }
        @Override
        public void tick() {
            LivingEntity t = this.mob.getTarget();
            if (t != null) this.mob.getLookControl().setLookAt(t.getX(), t.getEyeY(), t.getZ());
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

    @OnlyIn(Dist.CLIENT)
    public static class ClientSetup {
        public static final ModelLayerLocation WHISPERER_LAYER =
                new ModelLayerLocation(new ResourceLocation(MODID, "whisperer"), "main");
        public static void init(IEventBus bus) {
            bus.addListener(ClientSetup::registerLayer);
            bus.addListener(ClientSetup::registerRenderer);
        }
        private static void registerLayer(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(WHISPERER_LAYER, WhispererModel::createBodyLayer);
        }
        private static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
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
                    CubeListBuilder.create().texOffs(0, 36).addBox(-3.5F, -1.5F, -2.0F, 7.0F, 4.0F, 4.0F),
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
            this.headHood.yRot = netHeadYaw * ((float) Math.PI / 180F) * 0.85F;
            this.headHood.xRot = headPitch * ((float) Math.PI / 180F) * 0.75F;
            if (entity.isFrozenByLook()) {
                this.headHood.zRot = 0.28F + Mth.sin(ageInTicks * 0.7F) * 0.05F;
            } else if (entity.isRaging()) {
                this.headHood.zRot = Mth.sin(ageInTicks * 1.4F) * 0.14F;
                this.thorax.xRot = 0.38F;
            } else {
                this.headHood.zRot = Mth.sin(ageInTicks * 0.05F) * 0.06F;
            }
            float speed = entity.isRaging() ? 1.35F : 0.65F;
            this.leftLegUpper.xRot = Mth.cos(limbSwing * 0.6662F) * 0.9F * limbSwingAmount * speed;
            this.rightLegUpper.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.9F * limbSwingAmount * speed;
            if (entity.isRaging()) {
                this.leftArmUpper.xRot = -1.15F + Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.7F * limbSwingAmount;
                this.rightArmUpper.xRot = -1.15F + Mth.cos(limbSwing * 0.6662F) * 0.7F * limbSwingAmount;
                this.leftArmLower.xRot = -0.45F;
                this.rightArmLower.xRot = -0.45F;
            } else {
                this.leftArmUpper.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.45F * limbSwingAmount;
                this.rightArmUpper.xRot = Mth.cos(limbSwing * 0.6662F) * 0.45F * limbSwingAmount;
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class WhispererRenderer extends MobRenderer<WhispererEntity, WhispererModel> {
        private static ResourceLocation baseTexture;
        private static ResourceLocation eyesTexture;

        public WhispererRenderer(EntityRendererProvider.Context context) {
            super(context, new WhispererModel(context.bakeLayer(ClientSetup.WHISPERER_LAYER)), 0.45F);
            ensureTextures();
            this.addLayer(new RenderLayer<WhispererEntity, WhispererModel>(this) {
                @Override
                public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, WhispererEntity entity,
                                   float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
                    VertexConsumer consumer = buffer.getBuffer(RenderType.eyes(eyesTexture));
                    this.getParentModel().renderToBuffer(poseStack, consumer, 15728640, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
                }
            });
        }

        private static void ensureTextures() {
            if (baseTexture != null) return;
            NativeImage baseImg = new NativeImage(128, 128, true);
            NativeImage eyesImg = new NativeImage(128, 128, true);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    baseImg.setPixelRGBA(x, y, 0xFF070505);
                    eyesImg.setPixelRGBA(x, y, 0x00000000);
                }
            }
            for (int y = 7; y < 17; y++) {
                for (int x = 41; x < 49; x++) baseImg.setPixelRGBA(x, y, 0xFF858E9E);
            }
            for (int x = 42; x <= 43; x++) {
                baseImg.setPixelRGBA(x, 10, 0xFF0A0709);
                eyesImg.setPixelRGBA(x, 10, 0xFFC9D2D8);
            }
            for (int x = 46; x <= 47; x++) {
                baseImg.setPixelRGBA(x, 11, 0xFF0A0709);
                eyesImg.setPixelRGBA(x, 11, 0xFF1C167A);
            }
            for (int row = 22; row <= 28; row += 2) {
                for (int x = 29; x < 36; x++) baseImg.setPixelRGBA(x, row, 0xFF241D1F);
            }
            for (int y = 53; y < 57; y++) {
                for (int x = 22; x < 30; x++) baseImg.setPixelRGBA(x, y, 0xFF1C167A);
                for (int x = 52; x < 60; x++) baseImg.setPixelRGBA(x, y, 0xFF1C167A);
            }
            baseTexture = Minecraft.getInstance().getTextureManager().register("whisperer_base", new DynamicTexture(baseImg));
            eyesTexture = Minecraft.getInstance().getTextureManager().register("whisperer_eyes", new DynamicTexture(eyesImg));
        }

        @Override
        public ResourceLocation getTextureLocation(WhispererEntity entity) {
            ensureTextures();
            return baseTexture;
        }
    }
}
