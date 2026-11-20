package com.blackmonster.client;

import com.blackmonster.BlackMonsterMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** Silliq OBJ modelni suyak (bone) ierarxiyasi bo'yicha harakatlantirib chizadi (aylanish, siljish, notekis cho'zish). */
public final class MonsterModelRenderer {
    public static final ResourceLocation MESH = new ResourceLocation(BlackMonsterMod.MODID, "models/entity/black_monster.obj");
    public record Bone(String name, String parent, float px, float py, float pz) {}
    /** Ota suyaklar bolalaridan oldin keladi. Pivotlar bloklarda, old tomon = -Z, oyoqlar y=0. */
    public static final Bone[] RIG = {
            new Bone("root", null, 0.0000F, 0.0000F, 0.0000F),
            new Bone("hips", "root", 0.0000F, 1.6700F, 0.3000F),
            new Bone("torso", "hips", 0.0000F, 1.8000F, 0.3000F),
            new Bone("head", "torso", 0.0000F, 2.7500F, -0.2400F),
            new Bone("jaw", "head", 0.0000F, 2.4300F, -0.4100F),
            new Bone("arm_upper_l", "torso", 0.6000F, 2.6800F, -0.1260F),
            new Bone("arm_lower_l", "arm_upper_l", 0.8200F, 2.0000F, -0.0900F),
            new Bone("hand_l", "arm_lower_l", 0.8800F, 1.2500F, -0.8500F),
            new Bone("tendril_a_l", "jaw", 0.1200F, 2.3593F, -0.4609F),
            new Bone("tendril_b_l", "jaw", 0.1000F, 2.3458F, -0.3925F),
            new Bone("arm_upper_r", "torso", -0.6000F, 2.6800F, -0.1260F),
            new Bone("arm_lower_r", "arm_upper_r", -0.8200F, 2.0000F, -0.0900F),
            new Bone("hand_r", "arm_lower_r", -0.8800F, 1.2500F, -0.8500F),
            new Bone("tendril_a_r", "jaw", -0.1200F, 2.3593F, -0.4609F),
            new Bone("tendril_b_r", "jaw", -0.1000F, 2.3458F, -0.3925F),
            new Bone("cloth_f", "hips", 0.0000F, 1.8400F, 0.0000F),
            new Bone("cloth_b", "hips", 0.0000F, 1.8400F, 0.5600F),
            new Bone("thigh_l", "root", 0.2700F, 1.6700F, 0.2500F),
            new Bone("shin_l", "thigh_l", 0.6200F, 1.2000F, -0.2900F),
            new Bone("foot_l", "shin_l", 0.5000F, 0.3700F, 0.5900F),
            new Bone("thigh_r", "root", -0.2700F, 1.6700F, 0.2500F),
            new Bone("shin_r", "thigh_r", -0.6200F, 1.2000F, -0.2900F),
            new Bone("foot_r", "shin_r", -0.5000F, 0.3700F, 0.5900F)
    };
    private static ObjMesh mesh;
    private static boolean failed;
    private static final Map<String, ResourceLocation> TEX = new HashMap<>();

    public static ResourceLocation texture(String mat) {
        return TEX.computeIfAbsent(mat, m -> new ResourceLocation(BlackMonsterMod.MODID, "textures/entity/black_monster/" + m + ".png"));
    }

    private static ObjMesh mesh() {
        if (mesh == null && !failed) {
            try { mesh = ObjMesh.load(Minecraft.getInstance().getResourceManager(), MESH); }
            catch (Exception ex) { failed = true; LogUtils.getLogger().error("Black Monster OBJ model yuklanmadi", ex); }
        }
        return mesh;
    }

    public static void draw(PoseStack stack, MultiBufferSource buffers, int light, int overlay, MonsterAnimator.Pose pose, boolean enraged) {
        ObjMesh m = mesh();
        if (m == null) return;
        Map<String, PoseStack.Pose> poses = new HashMap<>();
        for (Bone b : RIG) {
            PoseStack.Pose parent = b.parent() == null ? stack.last() : poses.get(b.parent());
            PoseStack ps = new PoseStack();
            ps.last().pose().set(parent.pose());
            ps.last().normal().set(parent.normal());
            float[] off = pose.pos.get(b.name());
            float[] rot = pose.rot.get(b.name());
            float[] sc = pose.scale.get(b.name());
            ps.translate(b.px() + (off != null ? off[0] : 0F), b.py() + (off != null ? off[1] : 0F), b.pz() + (off != null ? off[2] : 0F));
            if (rot != null) {
                ps.mulPose(Axis.ZP.rotationDegrees(rot[2]));
                ps.mulPose(Axis.YN.rotationDegrees(rot[1]));
                ps.mulPose(Axis.XN.rotationDegrees(rot[0]));
            }
            if (sc != null) ps.scale(Math.max(0.001F, sc[0]), Math.max(0.001F, sc[1]), Math.max(0.001F, sc[2]));
            ps.translate(-b.px(), -b.py(), -b.pz());
            poses.put(b.name(), ps.last());
        }
        for (Map.Entry<String, Map<String, float[]>> mat : m.data.entrySet()) {
            boolean eye = mat.getKey().equals("eye");
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture(mat.getKey())));
            int l = eye ? 0xF000F0 : light; // ko'zlar qorong'ida yonadi
            float r = 1.0F, g = 1.0F, bl = 1.0F;
            if (enraged && !eye) { r = 1.0F; g = 0.75F; bl = 0.85F; }
            for (Map.Entry<String, float[]> part : mat.getValue().entrySet()) {
                PoseStack.Pose p = poses.getOrDefault(part.getKey(), stack.last());
                Matrix4f mx = p.pose(); Matrix3f nm = p.normal();
                float[] d = part.getValue();
                for (int i = 0; i + 23 < d.length; i += 24) {
                    put(vc, mx, nm, d, i, l, overlay, r, g, bl); put(vc, mx, nm, d, i + 8, l, overlay, r, g, bl);
                    put(vc, mx, nm, d, i + 16, l, overlay, r, g, bl); put(vc, mx, nm, d, i + 16, l, overlay, r, g, bl); // uchburchak = degenerat to'rtburchak
                }
            }
        }
    }

    private static void put(VertexConsumer vc, Matrix4f mx, Matrix3f nm, float[] d, int o, int light, int overlay, float r, float g, float b) {
        vc.vertex(mx, d[o], d[o + 1], d[o + 2]).color(r, g, b, 1.0F).uv(d[o + 3], d[o + 4])
                .overlayCoords(overlay).uv2(light).normal(nm, d[o + 5], d[o + 6], d[o + 7]).endVertex();
    }

    private MonsterModelRenderer() {}
}
