package cam72cam.immersiverailroading.physics;

import cam72cam.immersiverailroading.library.TrackItems;
import cam72cam.immersiverailroading.tile.TileRailBase;
import cam72cam.immersiverailroading.util.Speed;
import cam72cam.immersiverailroading.util.VecUtil;
import cam72cam.immersiverailroading.thirdparty.trackapi.ITrack;
import cam72cam.mod.world.World;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;

public class MovementSimulator {
	private World world;
	private TickPos position;
	private float bogeyFrontOffset;
	private float bogeyRearOffset;
	private double gauge;

	public MovementSimulator(World world, TickPos startPos, float bogeyFrontOffset, float bogeyRearOffset, double gauge) {
		this.world = world;
		this.position = startPos.clone(); //  We may modify it for convenience later on
		this.bogeyFrontOffset = bogeyFrontOffset;
		this.bogeyRearOffset = bogeyRearOffset;
		this.gauge = gauge;
	}
	
	public TickPos nextPosition(double moveDistance) {
		position.tickID += 1;
		position.isOffTrack = false;
		TickPos origPosition = position.clone();

		Vec3d front = frontBogeyPosition();
		Vec3d rear = rearBogeyPosition();
		
		if (Math.abs(moveDistance) < PhysicsAccummulator.MIN_SPEED) {
			boolean isTurnTable;
			
			TileRailBase frontBase = world.getBlockEntity(new Vec3i(front), TileRailBase.class);
			TileRailBase rearBase  = world.getBlockEntity(new Vec3i(rear),  TileRailBase.class);
			isTurnTable = frontBase != null &&
					frontBase.getParentTile() != null &&
					frontBase.getParentTile().info.settings.type == TrackItems.TURNTABLE;
			isTurnTable = isTurnTable || rearBase != null &&
					rearBase.getParentTile() != null &&
					rearBase.getParentTile().info.settings.type == TrackItems.TURNTABLE;
			
			position.speed = Speed.ZERO;
			
			if (!isTurnTable) {
				return position;
			}
		}
		
		position.speed = Speed.fromMinecraft(moveDistance);

		boolean isReverse = moveDistance < 0;

		
		if (isReverse) {
			moveDistance = -moveDistance;
			position.frontYaw += 180;
			position.rearYaw += 180;
			position.rotationYaw += 180;
			//position.rotationPitch = -position.rotationPitch;
			position.rotationYaw = (position.rotationYaw + 360f) % 360f;
			position.frontYaw = (position.frontYaw + 360f) % 360f;
			position.rearYaw = (position.rearYaw + 360f) % 360f;
		}

		
		
		Vec3d nextFront = nextPosition(front, position.rotationYaw, position.frontYaw, moveDistance);
		Vec3d nextRear = nextPosition(rear, position.rotationYaw, position.rearYaw, moveDistance);
		if (nextFront.equals(front) || nextRear.equals(rear)) {
			origPosition.speed = Speed.ZERO;
			nextPosition(front, position.rotationYaw, position.frontYaw, moveDistance);
			if (position.isOffTrack) {
				origPosition.isOffTrack = true;
			}
			return origPosition;
		}
		Vec3d frontDelta = nextFront.subtract(front);
		Vec3d rearDelta = nextRear.subtract(rear);
		if (position.speed != Speed.ZERO) {
			position.frontYaw = VecUtil.toWrongYaw(frontDelta);
			position.rearYaw = VecUtil.toWrongYaw(rearDelta);
		}

		Vec3d currCenter = VecUtil.between(front, rear);
		Vec3d nextCenter = VecUtil.between(nextFront, nextRear);
		Vec3d deltaCenter = nextCenter.subtract(currCenter);

		Vec3d bogeySkew = nextFront.subtract(nextRear);
		position.rotationYaw = VecUtil.toWrongYaw(bogeySkew);
		position.rotationPitch = (float) Math.toDegrees(Math.atan2(bogeySkew.y, nextRear.distanceTo(nextFront)));

		if (isReverse) {
			position.frontYaw += 180;
			position.rearYaw += 180;
			//rotationYaw += 180;
			//position.rotationPitch = -position.rotationPitch;
			if (position.speed != Speed.ZERO) {
				position.rotationYaw = (position.rotationYaw + 360f) % 360f;
				position.frontYaw = (position.frontYaw + 360f) % 360f;
				position.rearYaw = (position.rearYaw + 360f) % 360f;
			}
		}
		
		

		position.position = position.position.add(deltaCenter);
		if (world.isAir(new Vec3i(position.position))) {
			// Fall
			//position.position = position.position.addVector(0, -0.1, 0);
		}
		
		return position;
	}
	

	public Vec3d nextPosition(Vec3d currentPosition, float rotationYaw, float bogeyYaw, double distance) {
		ITrack rail = MovementTrack.findTrack(world, currentPosition, rotationYaw, gauge);
		if (rail == null) {
			position.isOffTrack = true;
			return currentPosition;
		}
		// Not using bogey yaw here, is that OK?
		Vec3d result = rail.getNextPosition(currentPosition, VecUtil.fromWrongYaw(distance, rotationYaw));
		if (result == null) {
			position.isOffTrack = true;
			return currentPosition;
		}
		return result;
	}

	public Vec3d frontBogeyPosition() {
		return VecUtil.fromWrongYawPitch(bogeyFrontOffset, position.rotationYaw, position.rotationPitch).add(position.position);
	}

	public Vec3d rearBogeyPosition() {
		return VecUtil.fromWrongYawPitch(bogeyRearOffset, position.rotationYaw, position.rotationPitch).add(position.position);
	}
	
	public boolean isOffTrack() {
		return position.isOffTrack;
	}
}
