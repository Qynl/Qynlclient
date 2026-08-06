package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * ScaffoldWalk — places a block under your feet automatically while you
 * walk, so crossing gaps, bridging over lava and climbing out of holes
 * needs no precise aim and no fast clicking. Just walk forward and the
 * floor builds itself.
 */
public class ScaffoldWalkModule extends Module {
	private int cooldown = 0;

	public ScaffoldWalkModule() {
		super("ScaffoldWalk", "Places blocks under you as you walk, so you never fall.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_B);
		addSetting(Setting.range("cooldown", "Place delay", 3.0, 1, 10, 1, "t"));
	}

	@Override
	public void onTick(Minecraft client) {
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		int placeCooldown = (int) getDoubleSetting("cooldown");
		if (client.player == null || client.level == null || client.gameMode == null) {
			return;
		}
		var player = client.player;
		if (player.isSpectator() || player.isPassenger() || player.isUsingItem()
				|| player.getAbilities().flying) {
			return;
		}

		Vec3 velocity = player.getDeltaMovement();
		int dx = (int) Math.signum(velocity.x);
		int dz = (int) Math.signum(velocity.z);

		BlockPos feet = player.getBlockPosBelowThatAffectsMyMovement();
		BlockPos target = feet.offset(dx, 0, dz);
		if (dx == 0 && dz == 0) {
			// Standing still: only fill a hole directly under the player.
			target = feet;
		}
		if (!client.level.isEmptyBlock(target)) {
			return;
		}

		BlockPos support = findSupport(client, target, dx, dz);
		if (support == null) {
			return;
		}
		Direction dir = Direction.fromDelta(
				target.getX() - support.getX(),
				target.getY() - support.getY(),
				target.getZ() - support.getZ());
		if (dir == null) {
			return;
		}

		Inventory inventory = player.getInventory();
		int slot = findBlockSlot(inventory);
		if (slot < 0) {
			return;
		}
		if (inventory.selected != slot) {
			inventory.selected = slot;
		}

		BlockHitResult hit = new BlockHitResult(
				Vec3.atCenterOf(support)
						.add(dir.getStepX() * 0.5, dir.getStepY() * 0.5, dir.getStepZ() * 0.5),
				dir, support, false);
		if (player.getEyePosition().distanceTo(hit.getLocation()) > 5.0) {
			return;
		}
		player.swing(InteractionHand.MAIN_HAND);
		client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
		cooldown = placeCooldown;
	}

	/** Finds a solid block to place the target block against. */
	private BlockPos findSupport(Minecraft client, BlockPos target, int dx, int dz) {
		// Prefer placing straight up onto the block below.
		if (isSolid(client, target.below())) {
			return target.below();
		}
		// Then the block behind (where the player stands or last placed).
		BlockPos behind = target.offset(-dx, 0, -dz);
		if (isSolid(client, behind)) {
			return behind;
		}
		// Finally, any solid horizontal neighbour.
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos n = target.relative(dir);
			if (isSolid(client, n)) {
				return n;
			}
		}
		return null;
	}

	private boolean isSolid(Minecraft client, BlockPos pos) {
		return !client.level.getBlockState(pos).getCollisionShape(client.level, pos).isEmpty();
	}

	/** Finds a placeable block item in the hotbar, preferring the selected slot. */
	private int findBlockSlot(Inventory inventory) {
		if (isPlaceable(inventory.getItem(inventory.selected))) {
			return inventory.selected;
		}
		for (int i = 0; i < 9; i++) {
			if (i != inventory.selected && isPlaceable(inventory.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	private boolean isPlaceable(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) {
			return false;
		}
		return item.getBlock() != Blocks.AIR;
	}
}
