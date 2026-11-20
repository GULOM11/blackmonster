package com.blackmonster.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Kubok: o'sha silliq OBJ model, kichraytirilgan va jonli (idle animatsiya). */
public class TrophyItemRenderer extends BlockEntityWithoutLevelRenderer {
    public TrophyItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5D, 0.05D, 0.5D);
        ps.scale(0.2F, 0.2F, 0.2F);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        ps.translate(0.0D, 0.0D, 0.0D);
        float t = Minecraft.getInstance().level != null ? (Minecraft.getInstance().level.getGameTime() + Minecraft.getInstance().getFrameTime()) / 20.0F : 0F;
        MonsterModelRenderer.draw(ps, buffers, light, overlay, MonsterAnimator.idlePose(t), false);
        ps.popPose();
    }
}
