package com.blackmonster.client;

import com.blackmonster.entity.BlackMonsterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class BlackMonsterRenderer extends EntityRenderer<BlackMonsterEntity> {
    public BlackMonsterRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.4F;
        this.shadowStrength = 0.9F;
    }

    @Override
    public void render(BlackMonsterEntity entity, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        stack.pushPose();
        float bodyYaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
        stack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        if (entity.deathTime > 0) {
            float f = Math.min(1.0F, Mth.sqrt((entity.deathTime + partial - 1.0F) / 20.0F * 1.6F));
            stack.mulPose(Axis.ZP.rotationDegrees(f * 90.0F));
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        MonsterModelRenderer.draw(stack, buffers, light, overlay, MonsterAnimator.compute(entity, partial), entity.isEnraged());
        stack.popPose();
        super.render(entity, yaw, partial, stack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BlackMonsterEntity entity) { return MonsterModelRenderer.texture("skin"); }
}
