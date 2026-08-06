package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class TargetInfoModule extends Module {
	public TargetInfoModule() {
		super("TargetInfo", "Show the distance to the block or entity you are looking at.", Category.INFO);
	}

	public String getInfo(Minecraft client) {
		if (client.player == null || client.hitResult == null) {
			return "";
		}
		HitResult hit = client.hitResult;
		Vec3 eye = client.player.getEyePosition();
		double distance;
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = ((BlockHitResult) hit).getBlockPos();
			distance = eye.distanceTo(Vec3.atCenterOf(pos));
		} else if (hit.getType() == HitResult.Type.ENTITY) {
			distance = eye.distanceTo(((EntityHitResult) hit).getEntity().getEyePosition());
		} else {
			distance = eye.distanceTo(hit.getLocation());
		}
		return "Target " + String.format("%.1f", distance) + "m";
	}
}
