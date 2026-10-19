package com.example.whisperingdark;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(WhisperingDarkMod.MODID)
public class WhisperingDarkMod {
    public static final String MODID = "whisperingdark";

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<EntityType<WhispererEntity>> WHISPERER =
            ENTITIES.register("whisperer", () -> EntityType.Builder.of(WhispererEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 3.2F)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(MODID, "whisperer").toString()));

    public static final RegistryObject<Item> WHISPERER_SPAWN_EGG =
            ITEMS.register("whisperer_spawn_egg", () -> new ForgeSpawnEggItem(WHISPERER, 0x0A0A0F, 0xB01028, new Item.Properties()));

    public static final RegistryObject<Item> SANITY_TALISMAN =
            ITEMS.register("sanity_talisman", () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)) {
                @Override
                public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                    ItemStack stack = player.getItemInHand(hand);
                    if (!level.isClientSide()) {
                        player.removeEffect(MobEffects.DARKNESS);
                        player.removeEffect(MobEffects.BLINDNESS);
                        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0));
                        player.getCooldowns().addCooldown(this, 200);
                    }
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
                }
            });

    public WhisperingDarkMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        bus.addListener(this::registerAttributes);
        bus.addListener(this::addCreative);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WHISPERER.get(), WhispererEntity.createAttributes().build());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(WHISPERER_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT || event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(SANITY_TALISMAN);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(WHISPERER.get(), WhispererRenderer::new);
        }

        @SubscribeEvent
        public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(WhispererModel.LAYER_LOCATION, WhispererModel::createBodyLayer);
        }
    }

    public static class WhispererEntity extends Monster {
        public WhispererEntity(EntityType<? extends Monster> type, Level level) {
            super(type, level);
            this.xpReward = 25;
        }

        @Override
        protected void registerGoals() {
            this.goalSelector.addGoal(0, new FloatGoal(this));
            this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35D, false));
            this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
            this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
            this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Monster.createMonsterAttributes()
                    .add(Attributes.MAX_HEALTH, 60.0D)
                    .add(Attributes.MOVEMENT_SPEED, 0.32D)
                    .add(Attributes.ATTACK_DAMAGE, 9.0D)
                    .add(Attributes.FOLLOW_RANGE, 40.0D)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
        }

        private boolean isPlayerLooking(Player player) {
            Vec3 view = player.getViewVector(1.0F).normalize();
            Vec3 diff = new Vec3(this.getX() - player.getX(), this.getEyeY() - player.getEyeY(), this.getZ() - player.getZ());
            double dist = diff.length();
            return view.dot(diff.normalize()) > 1.0D - 0.035D / Math.max(dist, 1.0D) && player.hasLineOfSight(this);
        }

        @Override
        public void tick() {
            super.tick();
            if (!this.level().isClientSide()) {
                Player nearest = this.level().getNearestPlayer(this, 28.0D);
                if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) {
                    if (isPlayerLooking(nearest)) {
                        this.getNavigation().stop();
                        this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
                        if (this.tickCount % 40 == 0) {
                            nearest.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
                            this.playSound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5F, 0.4F);
                        }
                    } else if (this.distanceTo(nearest) < 12.0F && this.tickCount % 60 == 0) {
                        nearest.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                        this.playSound(SoundEvents.WARDEN_NEARBY_CLOSER, 0.8F, 0.6F);
                    }
                }
            }
        }

        @Override
        protected SoundEvent getAmbientSound() { return SoundEvents.WARDEN_AMBIENT; }
        @Override
        protected SoundEvent getHurtSound(DamageSource src) { return SoundEvents.ENDERMAN_HURT; }
        @Override
        protected SoundEvent getDeathSound() { return SoundEvents.WARDEN_DEATH; }
        @Override
        protected void playStepSound(BlockPos pos, BlockState state) {
            this.playSound(SoundEvents.SCULK_BLOCK_STEP, 0.25F, 0.5F);
        }
    }

    public static class WhispererModel extends EntityModel<WhispererEntity> {
        public static final ModelLayerLocation LAYER_LOCATION =
                new ModelLayerLocation(new ResourceLocation(MODID, "whisperer"), "main");
        private final ModelPart head, body, rightArm, leftArm, rightLeg, leftLeg;

        public WhispererModel(ModelPart root) {
            this.head = root.getChild("head");
            this.body = root.getChild("body");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.rightLeg = root.getChild("right_leg");
            this.leftLeg = root.getChild("left_leg");
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition part = mesh.getRoot();
            part.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, CubeDeformation.NONE), PartPose.offset(0.0F, -18.0F, 0.0F));
            part.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 18.0F, 4.0F, CubeDeformation.NONE), PartPose.offset(0.0F, -18.0F, 0.0F));
            part.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -2.0F, -1.5F, 3.0F, 30.0F, 3.0F, CubeDeformation.NONE), PartPose.offset(-5.5F, -16.0F, 0.0F));
            part.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0F, -2.0F, -1.5F, 3.0F, 30.0F, 3.0F, CubeDeformation.NONE), PartPose.offset(5.5F, -16.0F, 0.0F));
            part.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 18).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 24.0F, 3.0F, CubeDeformation.NONE), PartPose.offset(-2.0F, 0.0F, 0.0F));
            part.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 18).mirror().addBox(-1.5F, 0.0F, -1.5F, 3.0F, 24.0F, 3.0F, CubeDeformation.NONE), PartPose.offset(2.0F, 0.0F, 0.0F));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(WhispererEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
            this.head.xRot = headPitch * ((float) Math.PI / 180F) + Mth.sin(ageInTicks * 0.45F) * 0.06F;
            this.head.zRot = Mth.cos(ageInTicks * 0.25F) * 0.12F;
            this.rightLeg.xRot = Mth.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
            this.leftLeg.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.2F * limbSwingAmount;
            this.rightArm.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 0.9F * limbSwingAmount - 0.15F;
            this.leftArm.xRot = Mth.cos(limbSwing * 0.6662F) * 0.9F * limbSwingAmount - 0.15F;
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float r, float g, float b, float a) {
            head.render(poseStack, buffer, packedLight, packedOverlay, r, g, b, a);
            body.render(poseStack, buffer, packedLight, packedOverlay, r, g, b, a);
            rightArm.render(poseStack, buffer, packedLight, packedOverlay, r, g, b, a);
            leftArm.render(poseStack, buffer, packedLight, packedOverlay, r, g, b, a);
            rightLeg.render(poseStack, buffer, packedLight, packedOverlay, r, g, b, a);
            leftLeg.render(poseStack, buffer, packedLight, packedOverlay, r, g, b, a);
        }
    }

    public static class WhispererRenderer extends MobRenderer<WhispererEntity, WhispererModel> {
        private static ResourceLocation GENERATED_TEXTURE;

        public WhispererRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new WhispererModel(ctx.bakeLayer(WhispererModel.LAYER_LOCATION)), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(WhispererEntity entity) {
            if (GENERATED_TEXTURE == null) {
                NativeImage img = new NativeImage(64, 64, false);
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        int v = 12 + ((x * 37 + y * 13) % 14);
                        img.setPixelRGBA(x, y, (255 << 24) | (v << 16) | (v << 8) | v);
                    }
                }
                // Багровые светящиеся глаза
                img.setPixelRGBA(9, 10, 0xFF2814DC);
                img.setPixelRGBA(10, 10, 0xFF2814DC);
                img.setPixelRGBA(13, 10, 0xFF2814DC);
                img.setPixelRGBA(14, 10, 0xFF2814DC);
                GENERATED_TEXTURE = Minecraft.getInstance().getTextureManager().register("whisperer_dynamic", new DynamicTexture(img));
            }
            return GENERATED_TEXTURE;
        }
    }
}
