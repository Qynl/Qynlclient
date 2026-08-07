package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

/**
 * TargetInfoModule for 1.8.9 — distance to whatever the crosshair is on.
 * Mirrors the 1.21.1 TargetInfo module.
 */
public class TargetInfoModule extends Module {

    public TargetInfoModule() {
        super("TargetInfo", "Show the distance to the block or entity you are looking at.", Category.INFO);
    }

    public String getInfo(MinecraftClient client) {
        if (client.player == null || client.result == null) {
            return "";
        }
        BlockHitResult hit = client.result;
        Vec3d eye = client.player.getCameraPosVec(1.0F);
        double distance;
        if (hit.type == BlockHitResult.Type.ENTITY && hit.entity != null) {
            distance = eye.distanceTo(new Vec3d(
                    hit.entity.x, hit.entity.y + hit.entity.getEyeHeight(), hit.entity.z));
        } else if (hit.type == BlockHitResult.Type.BLOCK && hit.getBlockPos() != null) {
            net.minecraft.util.math.BlockPos pos = hit.getBlockPos();
            distance = eye.distanceTo(new Vec3d(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        } else if (hit.pos != null) {
            distance = eye.distanceTo(hit.pos);
        } else {
            return "";
        }
        return "Target " + String.format("%.1f", distance) + "m";
    }
}
