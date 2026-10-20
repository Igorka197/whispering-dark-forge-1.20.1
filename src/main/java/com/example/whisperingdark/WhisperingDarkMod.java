package com.example.whisperingdark;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;package com.example.whisperingdark;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(WhisperingDarkMod.MODID)
public class WhisperingDarkMod {
    public static final String MODID = "whisperingdark";
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<EntityType<WhispererEntity>> WHISPERER = ENTITIES.register("whisperer",
            () -> EntityType.Builder.of(WhispererEntity::new, MobCategory.MONSTER).sized(0.7F, 3.1F).clientTrackingRange(12).build(new ResourceLocation(MODID, "whisperer").toString()));
    public static final RegistryObject<Item> WHISPERER_SPAWN_EGG = ITEMS.register("whisperer_spawn_egg",
            () -> new ForgeSpawnEggItem(WHISPERER, 0x050507, 0x9E9690, new Item.Properties()));
    public static final RegistryObject<Item> SANITY_TALISMAN = ITEMS.register("sanity_talisman",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)) {
                @Override public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
                    if (!l.isClientSide()) {
                        p.removeEffect(MobEffects.DARKNESS); p.removeEffect(MobEffects.BLINDNESS);
                        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0));
                        p.getCooldowns().addCooldown(this, 200);
                    }
                    return InteractionResultHolder.sidedSuccess(p.getItemInHand(h), l.isClientSide());
                }
            });

    public WhisperingDarkMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus); ITEMS.register(bus);
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(WHISPERER.get(), WhispererEntity.createAttributes().build()));
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) e.accept(WHISPERER_SPAWN_EGG);
            if (e.getTabKey() == CreativeModeTabs.COMBAT || e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(SANITY_TALISMAN);
        });
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(WHISPERER.get(), WhispererRenderer::new); }
        @SubscribeEvent public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions e) { e.registerLayerDefinition(WhispererModel.LAYER_LOCATION, WhispererModel::createBodyLayer); }
    }

    public static class WhispererEntity extends Monster {
        private static final EntityDataAccessor<Boolean> STARING = SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> RAGING = SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        public WhispererEntity(EntityType<? extends Monster> t, Level l) { super(t, l); this.xpReward = 0; }
        @Override protected void defineSynchedData() { super.defineSynchedData(); this.entityData.define(STARING, false); this.entityData.define(RAGING, false); }
        public boolean isStaring() { return this.entityData.get(STARING); }
        public boolean isRaging() { return this.entityData.get(RAGING); }
        @Override public boolean isAttackable() { return false; }
        @Override public boolean isPickable() { return false; }
        @Override public boolean isPushable() { return false; }
        @Override public boolean isInvulnerableTo(DamageSource s) { return !s.is(DamageTypeTags.BYPASSES_INVULNERABILITY); }
        @Override public boolean hurt(DamageSource s, float a) { return s.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(s, a); }
        @Override protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4D, false));
            this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
            this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 32.0F));
            this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 200.0D).add(Attributes.MOVEMENT_SPEED, 0.32D)
                    .add(Attributes.ATTACK_DAMAGE, 10.0D).add(Attributes.FOLLOW_RANGE, 48.0D).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
        }
        private boolean isPlayerLooking(Player p) {
            Vec3 v = p.getViewVector(1.0F).normalize();
            Vec3 d = new Vec3(this.getX() - p.getX(), this.getEyeY() - p.getEyeY(), this.getZ() - p.getZ());
            return v.dot(d.normalize()) > 1.0D - 0.045D / Math.max(d.length(), 1.0D) && p.hasLineOfSight(this);
        }
        @Override public void tick() {
            super.tick();
            if (!this.level().isClientSide()) {
                Player p = this.level().getNearestPlayer(this, 34.0D);
                if (p != null && !p.isCreative() && !p.isSpectator()) {
                    if (isPlayerLooking(p)) {
                        this.entityData.set(STARING, true); this.entityData.set(RAGING, false);
                        this.getNavigation().stop(); this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
                        this.getLookControl().setLookAt(p, 100.0F, 100.0F);
                        if (this.tickCount % 45 == 0) {
                            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 90, 0, false, false));
                            this.playSound(SoundEvents.AMBIENT_CAVE.value(), 0.7F, 0.5F);
                        }
                    } else {
                        boolean ch = this.getTarget() != null || this.distanceTo(p) < 18.0F;
                        this.entityData.set(STARING, false); this.entityData.set(RAGING, ch); this.setSprinting(ch);
                        if (this.distanceTo(p) < 12.0F && this.tickCount % 55 == 0) {
                            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 65, 0, false, false));
                            this.playSound(SoundEvents.WARDEN_NEARBY_CLOSER, 0.85F, 0.5F);
                        }
                    }
                } else {
                    if (p != null) this.getLookControl().setLookAt(p, 60.0F, 60.0F);
                    this.entityData.set(STARING, false); this.entityData.set(RAGING, false);
                }
            }
        }
        @Override protected SoundEvent getAmbientSound() { return SoundEvents.WARDEN_AMBIENT; }
        @Override protected void playStepSound(BlockPos p, BlockState s) { this.playSound(SoundEvents.SCULK_BLOCK_STEP, 0.25F, 0.45F); }
    }

    public static class WhispererModel extends EntityModel<WhispererEntity> {
        public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(MODID, "whisperer"), "main");
        private final ModelPart pelvis, upperTorso, neck, head, rightUpperArm, rightForearm, rightHand, leftUpperArm, leftForearm, leftHand, rightThigh, rightShin, leftThigh, leftShin;
        public WhispererModel(ModelPart r) {
            this.pelvis = r.getChild("pelvis"); this.upperTorso = pelvis.getChild("upper_torso");
            this.neck = upperTorso.getChild("neck"); this.head = neck.getChild("head");
            this.rightUpperArm = upperTorso.getChild("right_upper_arm"); this.rightForearm = rightUpperArm.getChild("right_forearm"); this.rightHand = rightForearm.getChild("right_hand");
            this.leftUpperArm = upperTorso.getChild("left_upper_arm"); this.leftForearm = leftUpperArm.getChild("left_forearm"); this.leftHand = leftForearm.getChild("left_hand");
            this.rightThigh = r.getChild("right_thigh"); this.rightShin = rightThigh.getChild("right_shin");
            this.leftThigh = r.getChild("left_thigh"); this.leftShin = leftThigh.getChild("left_shin");
        }
        public static LayerDefinition createBodyLayer() {
            MeshDefinition m = new MeshDefinition(); PartDefinition r = m.getRoot();
            PartDefinition pelvis = r.addOrReplaceChild("pelvis", CubeListBuilder.create()
                    .texOffs(0, 140).addBox(-3.5F, -7.0F, -2.0F, 7.0F, 5.0F, 4.0F)
                    .texOffs(0, 140).addBox(-3.0F, -2.5F, -1.5F, 6.0F, 3.0F, 3.0F)
                    .texOffs(0, 140).addBox(-2.5F, -7.5F, -2.5F, 5.0F, 6.0F, 5.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
            PartDefinition torso = pelvis.addOrReplaceChild("upper_torso", CubeListBuilder.create()
                    .texOffs(0, 140).addBox(-4.5F, -12.0F, -2.5F, 9.0F, 12.0F, 5.0F)
                    .texOffs(0, 140).addBox(-3.5F, -11.5F, -3.2F, 7.0F, 10.0F, 6.0F)
                    .texOffs(0, 140).addBox(-5.5F, -11.5F, -2.0F, 11.0F, 6.0F, 4.0F)
                    .texOffs(0, 140).addBox(-6.2F, -10.5F, -1.5F, 12.0F, 4.0F, 3.0F)
                    .texOffs(0, 140).addBox(-4.0F, -13.0F, -2.0F, 8.0F, 2.0F, 4.0F)
                    .texOffs(0, 140).addBox(-3.0F, -13.8F, -1.5F, 6.0F, 2.0F, 3.0F), PartPose.offset(0.0F, -7.0F, 0.0F));
            PartDefinition neck = torso.addOrReplaceChild("neck", CubeListBuilder.create()
                    .texOffs(128, 0).addBox(-2.0F, -4.5F, -2.0F, 4.0F, 5.0F, 4.0F)
                    .texOffs(0, 140).addBox(-2.5F, -4.0F, -1.5F, 5.0F, 4.0F, 4.0F), PartPose.offset(0.0F, -12.5F, 0.0F));
            PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-32.0F, -72.0F, -30.0F, 64.0F, 80.0F, 16.0F)
                    .texOffs(0, 140).addBox(-34.0F, -74.0F, -22.0F, 68.0F, 74.0F, 44.0F)
                    .texOffs(0, 140).addBox(-30.0F, -80.0F, -20.0F, 60.0F, 8.0F, 40.0F)
                    .texOffs(0, 140).addBox(-22.0F, -84.0F, -16.0F, 44.0F, 5.0F, 32.0F)
                    .texOffs(0, 140).addBox(-28.0F, -68.0F, 22.0F, 56.0F, 62.0F, 8.0F)
                    .texOffs(0, 140).addBox(-38.0F, -68.0F, -28.0F, 8.0F, 84.0F, 48.0F)
                    .texOffs(0, 140).addBox(30.0F, -68.0F, -28.0F, 8.0F, 84.0F, 48.0F)
                    .texOffs(0, 140).addBox(-41.0F, -36.0F, -24.0F, 6.0F, 54.0F, 40.0F)
                    .texOffs(0, 140).addBox(35.0F, -36.0F, -24.0F, 6.0F, 54.0F, 40.0F), PartPose.offset(0.0F, -3.5F, -0.3F));
            PartDefinition rArm = torso.addOrReplaceChild("right_upper_arm", CubeListBuilder.create()
                    .texOffs(0, 140).addBox(-2.5F, -1.5F, -2.0F, 3.0F, 8.0F, 4.0F)
                    .texOffs(0, 140).addBox(-2.0F, -1.0F, -1.5F, 2.0F, 15.0F, 3.0F), PartPose.offset(-5.2F, -10.2F, 0.0F));
            PartDefinition rFore = rArm.addOrReplaceChild("right_forearm", CubeListBuilder.create()
                    .texOffs(0, 140).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 7.0F, 3.0F)
                    .texOffs(0, 140).addBox(-1.0F, 5.0F, -1.0F, 2.0F, 9.0F, 2.0F), PartPose.offset(-1.0F, 14.0F, 0.0F));
            rFore.addOrReplaceChild("right_hand", CubeListBuilder.create()
                    .texOffs(160, 0).addBox(-1.0F, 0.0F, -1.5F, 1.0F, 5.0F, 1.0F)
                    .texOffs(160, 0).addBox(-1.0F, 0.0F, -0.3F, 1.0F, 6.0F, 1.0F)
                    .texOffs(160, 0).addBox(-1.0F, 0.0F, 0.9F, 1.0F, 5.0F, 1.0F)
                    .texOffs(160, 0).addBox(0.2F, 0.0F, -1.0F, 1.0F, 4.0F, 1.0F), PartPose.offset(0.0F, 14.0F, 0.0F));
            PartDefinition lArm = torso.addOrReplaceChild("left_upper_arm", CubeListBuilder.create()
                    .texOffs(0, 140).addBox(-0.5F, -1.5F, -2.0F, 3.0F, 8.0F, 4.0F)
                    .texOffs(0, 140).addBox(0.0F, -1.0F, -1.5F, 2.0F, 15.0F, 3.0F), PartPose.offset(5.2F, -10.2F, 0.0F));
            PartDefinition lFore = lArm.addOrReplaceChild("left_forearm", CubeListBuilder.create()
                    .texOffs(0, 140).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 7.0F, 3.0F)
                    .texOffs(0, 140).addBox(-1.0F, 5.0F, -1.0F, 2.0F, 9.0F, 2.0F), PartPose.offset(1.0F, 14.0F, 0.0F));
            lFore.addOrReplaceChild("left_hand", CubeListBuilder.create()
                    .texOffs(160, 0).addBox(0.0F, 0.0F, -1.5F, 1.0F, 5.0F, 1.0F)
                    .texOffs(160, 0).addBox(0.0F, 0.0F, -0.3F, 1.0F, 6.0F, 1.0F)
                    .texOffs(160, 0).addBox(0.0F, 0.0F, 0.9F, 1.0F, 5.0F, 1.0F)
                    .texOffs(160, 0).addBox(-1.2F, 0.0F, -1.0F, 1.0F, 4.0F, 1.0F), PartPose.offset(0.0F, 14.0F, 0.0F));
            PartDefinition rThigh = r.addOrReplaceChild("right_thigh", CubeListBuilder.create()
                    .texOffs(0, 140).addBox(-1.8F, -1.0F, -1.8F, 3.0F, 7.0F, 3.0F)
                    .texOffs(0, 140).addBox(-1.3F, 5.0F, -1.3F, 2.0F, 7.0F, 2.0F), PartPose.offset(-2.1F, 0.0F, 0.0F));
            rThigh.addOrReplaceChild("right_shin", CubeListBuilder.create()
                    .texOffs(0, 140).addBox(-1.3F, -0.5F, -1.3F, 2.0F, 6.0F, 2.0F)
                    .texOffs(0, 140).addBox(-1.0F, 5.0F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
            PartDefinition lThigh = r.addOrReplaceChild("left_thigh", CubeListBuilder.create()
                    .texOffs(0, 140).addBox(-1.2F, -1.0F, -1.8F, 3.0F, 7.0F, 3.0F)
                    .texOffs(0, 140).addBox(-0.7F, 5.0F, -1.3F, 2.0F, 7.0F, 2.0F), PartPose.offset(2.1F, 0.0F, 0.0F));
            lThigh.addOrReplaceChild("left_shin", CubeListBuilder.create()
                    .texOffs(0, 140).addBox(-0.7F, -0.5F, -1.3F, 2.0F, 6.0F, 2.0F)
                    .texOffs(0, 140).addBox(-1.0F, 5.0F, -1.0F, 2.0F, 7.0F, 2.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
            return LayerDefinition.create(m, 256, 256);
        }
        @Override public void setupAnim(WhispererEntity e, float s, float sa, float t, float yaw, float pitch) {
            this.head.xScale = 0.125F; this.head.yScale = 0.125F; this.head.zScale = 0.125F;
            boolean rag = e.isRaging() || sa > 0.45F, star = e.isStaring(); float sp = 0.6662F;
            this.pelvis.y = Mth.abs(Mth.cos(s * sp)) * 1.3F * sa; this.pelvis.zRot = Mth.sin(s * sp) * 0.04F * sa;
            float hunch = (rag ? 0.28F : 0.08F) + sa * 0.22F + Mth.sin(t * 0.08F) * 0.03F;
            this.upperTorso.xRot = hunch; this.upperTorso.yRot = Mth.cos(s * sp) * 0.16F * sa; this.upperTorso.zRot = Mth.sin(t * 0.05F) * 0.025F;
            this.neck.xRot = -hunch * 0.45F + Mth.sin(t * 0.12F) * 0.03F;
            float glitch = (Mth.sin(t * 0.93F) > 0.92F) ? Mth.cos(t * 3.7F) * 0.12F : 0.0F;
            this.head.yRot = yaw * ((float) Math.PI / 180F) + glitch * 0.5F;
            this.head.xRot = pitch * ((float) Math.PI / 180F) - hunch * 0.55F + Mth.sin(t * 0.35F) * 0.05F + glitch;
            this.head.zRot = Mth.cos(t * 0.22F) * 0.15F + (star ? 0.18F : 0.0F) + glitch;
            float rLeg = Mth.cos(s * sp) * 1.1F * sa, lLeg = Mth.cos(s * sp + (float) Math.PI) * 1.1F * sa;
            this.rightThigh.xRot = rLeg - 0.08F; this.leftThigh.xRot = lLeg - 0.08F;
            this.rightShin.xRot = 0.16F + Math.max(0.0F, Mth.sin(s * sp) * 1.3F * sa);
            this.leftShin.xRot = 0.16F + Math.max(0.0F, Mth.sin(s * sp + (float) Math.PI) * 1.3F * sa);
            float rArmS = Mth.cos(s * sp + (float) Math.PI) * 0.9F * sa, lArmS = Mth.cos(s * sp) * 0.9F * sa, reach = rag ? -0.55F : -0.12F;
            this.rightUpperArm.xRot = reach + rArmS * 0.8F; this.leftUpperArm.xRot = reach + lArmS * 0.8F;
            this.rightUpperArm.zRot = 0.07F + Mth.cos(t * 0.08F) * 0.03F; this.leftUpperArm.zRot = -0.07F - Mth.cos(t * 0.08F) * 0.03F;
            this.rightForearm.xRot = -0.42F - Math.max(0.0F, -rArmS) * 0.6F - (rag ? 0.28F : 0.0F);
            this.leftForearm.xRot = -0.42F - Math.max(0.0F, -lArmS) * 0.6F - (rag ? 0.28F : 0.0F);
            this.rightHand.zRot = 0.18F + Mth.sin(t * 0.25F) * 0.12F; this.leftHand.zRot = -0.18F - Mth.sin(t * 0.25F) * 0.12F;
            if (this.attackTime > 0.0F) {
                float a = Mth.sin(this.attackTime * (float) Math.PI);
                this.rightUpperArm.xRot = -1.5F + a * 1.35F; this.leftUpperArm.xRot = -1.5F + a * 1.35F;
                this.rightForearm.xRot = -0.85F + a * 0.6F; this.leftForearm.xRot = -0.85F + a * 0.6F;
            }
        }
        @Override public void renderToBuffer(PoseStack ps, VertexConsumer b, int pl, int po, float r, float g, float bl, float a) {
            pelvis.render(ps, b, pl, po, r, g, bl, a); rightThigh.render(ps, b, pl, po, r, g, bl, a); leftThigh.render(ps, b, pl, po, r, g, bl, a);
        }
    }

    public static class WhispererRenderer extends MobRenderer<WhispererEntity, WhispererModel> {
        private static ResourceLocation BASE_TEX, GLOW_TEX;
        public WhispererRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new WhispererModel(ctx.bakeLayer(WhispererModel.LAYER_LOCATION)), 0.4F);
            this.addLayer(new EyesLayer<WhispererEntity, WhispererModel>(this) {
                @Override public RenderType renderType() { return RenderType.eyes(getGlow()); }
            });
        }
        private static int col(int r, int g, int b, int a) { return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF); }
        private static ResourceLocation getGlow() {
            if (GLOW_TEX == null) {
                NativeImage g = new NativeImage(256, 256, false);
                for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) g.setPixelRGBA(x, y, 0);
                for (int fy = 31; fy <= 38; fy++) for (int fx = 14; fx <= 49; fx++) {
                    for (double exC : new double[]{20.5, 42.5}) {
                        double edx = (fx - exC) / 5.4, edy = (fy - 34.5) / 2.9, ed = edx * edx + edy * edy;
                        double pd = Math.pow((fx - exC) / 2.9, 2) + Math.pow((fy - 34.3) / 2.7, 2);
                        if (ed <= 1.0 && pd > 1.0) g.setPixelRGBA(16 + fx, 16 + fy, col(55, 53, 52, 255));
                        else if (Math.abs(fx - (exC - 0.8)) < 0.9 && Math.abs(fy - 33.2) < 0.9) g.setPixelRGBA(16 + fx, 16 + fy, col(90, 92, 95, 255));
                    }
                }
                GLOW_TEX = Minecraft.getInstance().getTextureManager().register("whisperer_uncanny_glow", new DynamicTexture(g));
            }
            return GLOW_TEX;
        }
        @Override public ResourceLocation getTextureLocation(WhispererEntity e) {
            if (BASE_TEX == null) {
                NativeImage img = new NativeImage(256, 256, false);
                for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
                    int v = 3 + ((x * 17 + y * 31) % 3);
                    img.setPixelRGBA(x, y, col(v, v, v + 1, 255));
                }
                for (int y = 4; y < 12; y++) for (int x = 132; x < 140; x++) {
                    int s = ((x + y * 2) % 3 == 0) ? 78 : 18;
                    img.setPixelRGBA(x, y, col(s, s, s + 3, 255));
                }
                for (int y = 4; y < 8; y++) for (int x = 160; x < 168; x++) img.setPixelRGBA(x, y, col(68, 65, 64, 255));
                for (int fy = 0; fy < 80; fy++) for (int fx = 0; fx < 64; fx++) {
                    double nx = (fx - 31.5) / 23.0;
                    double wf = 1.0 + 0.35 * Math.pow(Math.max(0.0, (fy - 38.0) / 34.0), 1.4);
                    double nxA = nx * wf, ny = (fy - 40.0) / 31.0;
                    double dist = Math.sqrt(nxA * nxA + ny * ny);
                    if (dist < 1.0 && fy >= 10 && fy <= 72) {
                        double sh = Math.max(0.0, Math.cos(dist * 1.38)) * 0.88 + 0.12;
                        double cx = Math.abs(fx - 31.5);
                        if (fy > 40 && cx > 9 && cx < 21 && fy > 42 && fy < 62) {
                            sh -= Math.sin((cx - 9.0) / 12.0 * Math.PI) * Math.sin((fy - 42.0) / 20.0 * Math.PI) * 0.32;
                        }
                        double el = Math.sqrt(Math.pow((fx - 20.5) / 6.8, 2) + Math.pow((fy - 34.5) / 5.2, 2));
                        double er = Math.sqrt(Math.pow((fx - 42.5) / 6.8, 2) + Math.pow((fy - 34.5) / 5.2, 2));
                        if (el < 1.25) sh -= (1.25 - el) * 0.48;
                        if (er < 1.25) sh -= (1.25 - er) * 0.48;
                        if (fy >= 31 && fy <= 49) {
                            if (cx < 3.2) sh += (1.0 - cx / 3.2) * 0.18;
                            else if (cx < 5.5) sh -= (5.5 - cx) / 2.3 * 0.15;
                        }
                        if (fy >= 49 && fy <= 51 && cx >= 1.5 && cx <= 3.3) sh *= 0.18;
                        double foldX = 6.0 + (fy - 47.0) * 0.48;
                        if (fy >= 47 && fy <= 62 && Math.abs(cx - foldX) < 1.6) sh -= (1.6 - Math.abs(cx - foldX)) * 0.22;
                        double val = Math.max(0.03, Math.min(1.0, sh));
                        int r = (int) (182 * Math.pow(val, 1.05));
                        int g = (int) (172 * Math.pow(val, 1.08));
                        int b = (int) (168 * Math.pow(val, 1.10));
                        for (double exC : new double[]{20.5, 42.5}) {
                            double edx = (fx - exC) / 5.4, edy = (fy - 34.5) / 2.9, ed = edx * edx + edy * edy;
                            if (ed <= 1.0) {
                                int sc = (int) (222 * (1.0 - ed * 0.25));
                                r = sc; g = sc - 3; b = sc - 5;
                                double pd = Math.pow((fx - exC) / 2.9, 2) + Math.pow((fy - 34.3) / 2.7, 2);
                                if (pd <= 1.0) {
                                    r = 4; g = 4; b = 6;
                                    if (Math.abs(fx - (exC - 0.8)) < 0.9 && Math.abs(fy - 33.2) < 0.9) { r = 210; g = 215; b = 220; }
                                }
                            } else if (ed <= 1.35) { r = (int) (r * 0.35); g = (int) (g * 0.32); b = (int) (b * 0.32); }
                        }
                        double mCurve = 58.5 - 0.035 * Math.pow(fx - 31.5, 2);
                        double mdy = Math.abs(fy - mCurve);
                        if (cx < 11.5 && mdy < 2.6) {
                            if (mdy < 1.15 && cx < 10.5) {
                                if (fx % 2 == 0 && cx < 7.5 && mdy < 0.65) { r = 82; g = 78; b = 76; }
                                else { r = 5; g = 4; b = 6; }
                            } else {
                                double f = mdy / 2.6;
                                r = (int) (r * f * 0.62); g = (int) (g * f * 0.50); b = (int) (b * f * 0.52);
                            }
                        }
                        double strand = Math.sin(fx * 0.42) * 5.5 + Math.cos(fx * 0.95) * 3.5;
                        if (fy < 22.0 + strand || cx > 20.5 - (fy < 36 ? (36 - fy) * 0.22 : 0.0)) { r = 3; g = 3; b = 5; }
                        img.setPixelRGBA(16 + fx, 16 + fy, col(r, g, b, 255));
                    }
                }
                BASE_TEX = Minecraft.getInstance().getTextureManager().register("whisperer_uncanny_base", new DynamicTexture(img));
            }
            return BASE_TEX;
        }
    }
}
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.*;

@Mod(WhisperingDarkMod.MODID)
public class WhisperingDarkMod {
    public static final String MODID = "whisperingdark";
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final RegistryObject<EntityType<WhispererEntity>> WHISPERER = ENTITIES.register("whisperer",
            () -> EntityType.Builder.of(WhispererEntity::new, MobCategory.MONSTER).sized(0.75F, 3.2F).clientTrackingRange(10).build(new ResourceLocation(MODID, "whisperer").toString()));
    public static final RegistryObject<Item> WHISPERER_SPAWN_EGG = ITEMS.register("whisperer_spawn_egg",
            () -> new ForgeSpawnEggItem(WHISPERER, 0x0B0914, 0xC8142E, new Item.Properties()));
    public static final RegistryObject<Item> SANITY_TALISMAN = ITEMS.register("sanity_talisman",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)) {
                @Override public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
                    if (!l.isClientSide()) {
                        p.removeEffect(MobEffects.DARKNESS); p.removeEffect(MobEffects.BLINDNESS);
                        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0));
                        p.getCooldowns().addCooldown(this, 200);
                    }
                    return InteractionResultHolder.sidedSuccess(p.getItemInHand(h), l.isClientSide());
                }
            });

    public WhisperingDarkMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus); ITEMS.register(bus);
        bus.addListener((EntityAttributeCreationEvent e) -> e.put(WHISPERER.get(), WhispererEntity.createAttributes().build()));
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) e.accept(WHISPERER_SPAWN_EGG);
            if (e.getTabKey() == CreativeModeTabs.COMBAT || e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(SANITY_TALISMAN);
        });
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) { e.registerEntityRenderer(WHISPERER.get(), WhispererRenderer::new); }
        @SubscribeEvent public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions e) { e.registerLayerDefinition(WhispererModel.LAYER_LOCATION, WhispererModel::createBodyLayer); }
    }

    public static class WhispererEntity extends Monster {
        private static final EntityDataAccessor<Boolean> STARING = SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> RAGING = SynchedEntityData.defineId(WhispererEntity.class, EntityDataSerializers.BOOLEAN);
        public WhispererEntity(EntityType<? extends Monster> t, Level l) { super(t, l); this.xpReward = 30; }
        @Override protected void defineSynchedData() { super.defineSynchedData(); this.entityData.define(STARING, false); this.entityData.define(RAGING, false); }
        public boolean isStaring() { return this.entityData.get(STARING); }
        public boolean isRaging() { return this.entityData.get(RAGING); }
        @Override protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.45D, false));
            this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.85D));
            this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 28.0F));
            this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 65.0D).add(Attributes.MOVEMENT_SPEED, 0.33D)
                    .add(Attributes.ATTACK_DAMAGE, 10.0D).add(Attributes.FOLLOW_RANGE, 42.0D).add(Attributes.KNOCKBACK_RESISTANCE, 0.65D);
        }
        private boolean isPlayerLooking(Player p) {
            Vec3 v = p.getViewVector(1.0F).normalize();
            Vec3 d = new Vec3(this.getX() - p.getX(), this.getEyeY() - p.getEyeY(), this.getZ() - p.getZ());
            return v.dot(d.normalize()) > 1.0D - 0.04D / Math.max(d.length(), 1.0D) && p.hasLineOfSight(this);
        }
        @Override public void tick() {
            super.tick();
            if (!this.level().isClientSide()) {
                Player p = this.level().getNearestPlayer(this, 30.0D);
                if (p != null && !p.isCreative() && !p.isSpectator()) {
                    if (isPlayerLooking(p)) {
                        this.entityData.set(STARING, true); this.entityData.set(RAGING, false);
                        this.getNavigation().stop(); this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
                        if (this.tickCount % 40 == 0) {
                            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
                            this.playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6F, 0.45F);
                        }
                    } else {
                        boolean ch = this.getTarget() != null || this.distanceTo(p) < 16.0F;
                        this.entityData.set(STARING, false); this.entityData.set(RAGING, ch); this.setSprinting(ch);
                        if (this.distanceTo(p) < 12.0F && this.tickCount % 55 == 0) {
                            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                            this.playSound(SoundEvents.WARDEN_NEARBY_CLOSER, 0.85F, 0.55F);
                        }
                    }
                } else { this.entityData.set(STARING, false); this.entityData.set(RAGING, this.getTarget() != null); }
            }
        }
        @Override protected SoundEvent getAmbientSound() { return SoundEvents.WARDEN_AMBIENT; }
        @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENDERMAN_HURT; }
        @Override protected SoundEvent getDeathSound() { return SoundEvents.WARDEN_DEATH; }
        @Override protected void playStepSound(BlockPos p, BlockState s) { this.playSound(SoundEvents.SCULK_BLOCK_STEP, 0.35F, 0.5F); }
    }

    public static class WhispererModel extends EntityModel<WhispererEntity> {
        public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation(MODID, "whisperer"), "main");
        private final ModelPart pelvis, upperTorso, neck, head, jaw, rightUpperArm, rightForearm, rightClaws, leftUpperArm, leftForearm, leftClaws, rightThigh, rightShin, leftThigh, leftShin;
        public WhispererModel(ModelPart r) {
            this.pelvis = r.getChild("pelvis"); this.upperTorso = pelvis.getChild("upper_torso");
            this.neck = upperTorso.getChild("neck"); this.head = neck.getChild("head"); this.jaw = head.getChild("jaw");
            this.rightUpperArm = upperTorso.getChild("right_upper_arm"); this.rightForearm = rightUpperArm.getChild("right_forearm"); this.rightClaws = rightForearm.getChild("right_claws");
            this.leftUpperArm = upperTorso.getChild("left_upper_arm"); this.leftForearm = leftUpperArm.getChild("left_forearm"); this.leftClaws = leftForearm.getChild("left_claws");
            this.rightThigh = r.getChild("right_thigh"); this.rightShin = rightThigh.getChild("right_shin");
            this.leftThigh = r.getChild("left_thigh"); this.leftShin = leftThigh.getChild("left_shin");
        }
        public static LayerDefinition createBodyLayer() {
            MeshDefinition m = new MeshDefinition(); PartDefinition r = m.getRoot();
            PartDefinition pelvis = r.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(16, 34).addBox(-3.5F, -7.0F, -1.5F, 7.0F, 8.0F, 3.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
            PartDefinition torso = pelvis.addOrReplaceChild("upper_torso", CubeListBuilder.create()
                    .texOffs(16, 16).addBox(-4.5F, -12.0F, -2.5F, 9.0F, 12.0F, 5.0F)
                    .texOffs(0, 48).addBox(-5.0F, -10.0F, -3.0F, 10.0F, 2.0F, 6.0F)
                    .texOffs(0, 48).addBox(-5.0F, -7.0F, -3.0F, 10.0F, 2.0F, 6.0F)
                    .texOffs(0, 48).addBox(-4.5F, -4.0F, -3.0F, 9.0F, 2.0F, 5.0F)
                    .texOffs(52, 48).addBox(-1.0F, -11.0F, 2.5F, 2.0F, 3.0F, 3.0F)
                    .texOffs(52, 48).addBox(-1.0F, -7.0F, 2.5F, 2.0F, 3.0F, 2.0F)
                    .texOffs(52, 48).addBox(-1.0F, -3.0F, 2.5F, 2.0F, 3.0F, 2.0F), PartPose.offset(0.0F, -7.0F, 0.0F));
            PartDefinition neck = torso.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(44, 0).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 5.0F, 4.0F), PartPose.offset(0.0F, -12.0F, 0.0F));
            PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-4.0F, -9.0F, -4.5F, 8.0F, 7.0F, 8.0F).texOffs(0, 0).addBox(-3.5F, -2.0F, -4.5F, 7.0F, 2.0F, 5.0F)
                    .texOffs(52, 36).addBox(-5.0F, -13.0F, -1.0F, 2.0F, 6.0F, 2.0F).texOffs(52, 36).mirror().addBox(3.0F, -13.0F, -1.0F, 2.0F, 6.0F, 2.0F)
                    .texOffs(52, 36).addBox(-1.0F, -11.5F, 1.0F, 2.0F, 4.0F, 2.0F), PartPose.offset(0.0F, -3.5F, -0.5F));
            head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(24, 0).addBox(-3.0F, 0.0F, -3.5F, 6.0F, 3.0F, 5.0F), PartPose.offset(0.0F, -2.0F, -1.0F));
            PartDefinition rArm = torso.addOrReplaceChild("right_upper_arm", CubeListBuilder.create().texOffs(44, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F).texOffs(44, 24).addBox(-2.5F, 2.0F, -1.5F, 3.0F, 12.0F, 3.0F), PartPose.offset(-4.5F, -10.5F, 0.0F));
            PartDefinition rFore = rArm.addOrReplaceChild("right_forearm", CubeListBuilder.create().texOffs(44, 39).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 14.0F, 2.0F), PartPose.offset(-1.0F, 14.0F, 0.0F));
            rFore.addOrReplaceChild("right_claws", CubeListBuilder.create().texOffs(34, 48).addBox(-1.0F, 0.0F, -1.5F, 1.0F, 6.0F, 1.0F).texOffs(34, 48).addBox(-1.0F, 0.0F, 0.5F, 1.0F, 6.0F, 1.0F).texOffs(34, 48).addBox(0.2F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offset(0.0F, 14.0F, 0.0F));
            PartDefinition lArm = torso.addOrReplaceChild("left_upper_arm", CubeListBuilder.create().texOffs(44, 16).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F).texOffs(44, 24).mirror().addBox(-0.5F, 2.0F, -1.5F, 3.0F, 12.0F, 3.0F), PartPose.offset(4.5F, -10.5F, 0.0F));
            PartDefinition lFore = lArm.addOrReplaceChild("left_forearm", CubeListBuilder.create().texOffs(44, 39).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 14.0F, 2.0F), PartPose.offset(1.0F, 14.0F, 0.0F));
            lFore.addOrReplaceChild("left_claws", CubeListBuilder.create().texOffs(34, 48).addBox(0.0F, 0.0F, -1.5F, 1.0F, 6.0F, 1.0F).texOffs(34, 48).addBox(0.0F, 0.0F, 0.5F, 1.0F, 6.0F, 1.0F).texOffs(34, 48).addBox(-1.2F, 0.0F, -0.5F, 1.0F, 4.0F, 1.0F), PartPose.offset(0.0F, 14.0F, 0.0F));
            PartDefinition rThigh = r.addOrReplaceChild("right_thigh", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F), PartPose.offset(-2.2F, 0.0F, 0.0F));
            rThigh.addOrReplaceChild("right_shin", CubeListBuilder.create().texOffs(0, 31).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
            PartDefinition lThigh = r.addOrReplaceChild("left_thigh", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-1.5F, 0.0F, -1.5F, 3.0F, 12.0F, 3.0F), PartPose.offset(2.2F, 0.0F, 0.0F));
            lThigh.addOrReplaceChild("left_shin", CubeListBuilder.create().texOffs(0, 31).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F), PartPose.offset(0.0F, 12.0F, 0.0F));
            return LayerDefinition.create(m, 64, 64);
        }
        @Override public void setupAnim(WhispererEntity e, float s, float sa, float t, float yaw, float pitch) {
            boolean rag = e.isRaging() || sa > 0.45F, star = e.isStaring(); float sp = 0.6662F;
            this.pelvis.y = Mth.abs(Mth.cos(s * sp)) * 1.4F * sa; this.pelvis.zRot = Mth.sin(s * sp) * 0.05F * sa;
            float hunch = (rag ? 0.32F : 0.12F) + sa * 0.25F + Mth.sin(t * 0.1F) * 0.04F;
            this.upperTorso.xRot = hunch; this.upperTorso.yRot = Mth.cos(s * sp) * 0.18F * sa; this.upperTorso.zRot = Mth.sin(t * 0.07F) * 0.03F;
            this.neck.xRot = -hunch * 0.5F + Mth.sin(t * 0.15F) * 0.04F;
            this.head.yRot = yaw * ((float) Math.PI / 180F);
            this.head.xRot = pitch * ((float) Math.PI / 180F) - hunch * 0.5F + Mth.sin(t * 0.48F) * 0.07F + (star ? Mth.cos(t * 1.3F) * 0.05F : 0.0F);
            this.head.zRot = Mth.cos(t * 0.25F) * 0.16F + Mth.sin(t * 0.65F) * 0.04F;
            this.jaw.xRot = (rag || star || this.attackTime > 0.0F) ? 0.58F + Mth.sin(t * 0.85F) * 0.14F : 0.10F + Mth.abs(Mth.sin(t * 0.22F)) * 0.10F;
            float rLeg = Mth.cos(s * sp) * 1.15F * sa, lLeg = Mth.cos(s * sp + (float) Math.PI) * 1.15F * sa;
            this.rightThigh.xRot = rLeg - 0.10F; this.leftThigh.xRot = lLeg - 0.10F; this.rightThigh.zRot = 0.03F; this.leftThigh.zRot = -0.03F;
            this.rightShin.xRot = 0.20F + Math.max(0.0F, Mth.sin(s * sp) * 1.35F * sa);
            this.leftShin.xRot = 0.20F + Math.max(0.0F, Mth.sin(s * sp + (float) Math.PI) * 1.35F * sa);
            float rArmS = Mth.cos(s * sp + (float) Math.PI) * 0.95F * sa, lArmS = Mth.cos(s * sp) * 0.95F * sa, reach = rag ? -0.65F : -0.18F;
            this.rightUpperArm.xRot = reach + rArmS * 0.85F; this.leftUpperArm.xRot = reach + lArmS * 0.85F;
            this.rightUpperArm.zRot = 0.10F + Mth.cos(t * 0.09F) * 0.05F; this.leftUpperArm.zRot = -0.10F - Mth.cos(t * 0.09F) * 0.05F;
            this.rightUpperArm.yRot = -0.15F + Mth.sin(s * sp) * 0.15F * sa; this.leftUpperArm.yRot = 0.15F + Mth.sin(s * sp) * 0.15F * sa;
            this.rightForearm.xRot = -0.52F - Math.max(0.0F, -rArmS) * 0.65F - (rag ? 0.30F : 0.0F);
            this.leftForearm.xRot = -0.52F - Math.max(0.0F, -lArmS) * 0.65F - (rag ? 0.30F : 0.0F);
            this.rightForearm.zRot = -0.08F; this.leftForearm.zRot = 0.08F;
            this.rightClaws.zRot = 0.22F + Mth.sin(t * 0.3F) * 0.15F; this.leftClaws.zRot = -0.22F - Mth.sin(t * 0.3F) * 0.15F;
            if (this.attackTime > 0.0F) {
                float a = Mth.sin(this.attackTime * (float) Math.PI);
                this.rightUpperArm.xRot = -1.65F + a * 1.45F; this.leftUpperArm.xRot = -1.65F + a * 1.45F;
                this.rightForearm.xRot = -0.95F + a * 0.70F; this.leftForearm.xRot = -0.95F + a * 0.70F;
            }
        }
        @Override public void renderToBuffer(PoseStack ps, VertexConsumer b, int pl, int po, float r, float g, float bl, float a) {
            pelvis.render(ps, b, pl, po, r, g, bl, a); rightThigh.render(ps, b, pl, po, r, g, bl, a); leftThigh.render(ps, b, pl, po, r, g, bl, a);
        }
    }

    public static class WhispererRenderer extends MobRenderer<WhispererEntity, WhispererModel> {
        private static ResourceLocation BASE_TEX, GLOW_TEX;
        public WhispererRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new WhispererModel(ctx.bakeLayer(WhispererModel.LAYER_LOCATION)), 0.55F);
            this.addLayer(new EyesLayer<WhispererEntity, WhispererModel>(this) {
                @Override public RenderType renderType() { return RenderType.eyes(getGlow()); }
            });
        }
        private static int col(int r, int g, int b, int a) { return ((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((g & 0xFF) << 8) | (r & 0xFF); }
        private static ResourceLocation getGlow() {
            if (GLOW_TEX == null) {
                NativeImage g = new NativeImage(64, 64, false);
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) g.setPixelRGBA(x, y, 0);
                for (int ex : new int[]{9, 10, 13, 14}) { g.setPixelRGBA(ex, 10, col(235, 15, 35, 255)); g.setPixelRGBA(ex, 11, col(180, 10, 25, 255)); }
                g.setPixelRGBA(10, 10, col(255, 190, 190, 255)); g.setPixelRGBA(13, 10, col(255, 190, 190, 255));
                for (int cy = 23; cy <= 27; cy++) for (int cx = 24; cx <= 26; cx++) g.setPixelRGBA(cx, cy, col(200, 15, 40, 255));
                GLOW_TEX = Minecraft.getInstance().getTextureManager().register("whisperer_glow", new DynamicTexture(g));
            }
            return GLOW_TEX;
        }
        @Override public ResourceLocation getTextureLocation(WhispererEntity e) {
            if (BASE_TEX == null) {
                NativeImage img = new NativeImage(64, 64, false);
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                    int st = ((x * 19 + y * 3) % 11) - 5;
                    img.setPixelRGBA(x, y, col(Mth.clamp(22 + st, 10, 42), Mth.clamp(16 + st, 8, 34), Mth.clamp(26 + st, 12, 48), 255));
                }
                for (int y = 8; y <= 14; y++) for (int x = 9; x <= 14; x++) img.setPixelRGBA(x, y, col(115, 108, 112, 255));
                for (int y = 10; y <= 12; y++) for (int ex : new int[]{9, 10, 13, 14}) img.setPixelRGBA(ex, y, col(8, 4, 8, 255));
                img.setPixelRGBA(10, 13, col(120, 12, 24, 255)); img.setPixelRGBA(13, 13, col(120, 12, 24, 255));
                img.setPixelRGBA(9, 10, col(235, 20, 40, 255)); img.setPixelRGBA(10, 10, col(255, 160, 160, 255));
                img.setPixelRGBA(13, 10, col(255, 160, 160, 255)); img.setPixelRGBA(14, 10, col(235, 20, 40, 255));
                for (int x = 28; x <= 38; x++) img.setPixelRGBA(x, 5, (x % 2 == 0) ? col(195, 185, 175, 255) : col(90, 10, 20, 255));
                for (int y = 48; y < 60; y++) for (int x = 0; x < 36; x++) img.setPixelRGBA(x, y, col(135, 126, 122, 255));
                for (int y = 48; y < 56; y++) for (int x = 34; x < 42; x++) img.setPixelRGBA(x, y, (y > 52) ? col(155, 15, 28, 255) : col(180, 172, 165, 255));
                BASE_TEX = Minecraft.getInstance().getTextureManager().register("whisperer_base", new DynamicTexture(img));
            }
            return BASE_TEX;
        }
    }
}
