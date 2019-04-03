package cam72cam.immersiverailroading.render.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.lwjgl.opengl.GL11;

import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.entity.EntityBuildableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock.PosRot;
import cam72cam.immersiverailroading.library.Gauge;
import cam72cam.immersiverailroading.library.ItemComponentType;
import cam72cam.immersiverailroading.library.RenderComponentType;
import cam72cam.immersiverailroading.model.RenderComponent;
import cam72cam.immersiverailroading.model.MultiRenderComponent;
import cam72cam.immersiverailroading.model.obj.OBJModel;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Freight;
import cam72cam.immersiverailroading.entity.LocomotiveDiesel;
import cam72cam.immersiverailroading.entity.LocomotiveSteam;
import cam72cam.immersiverailroading.registry.EntityRollingStockDefinition;
import cam72cam.immersiverailroading.registry.FreightDefinition;
import cam72cam.immersiverailroading.registry.LocomotiveSteamDefinition;
import cam72cam.immersiverailroading.render.OBJRender;
import cam72cam.immersiverailroading.util.GLBoolTracker;
import cam72cam.immersiverailroading.util.VecUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class StockModel extends OBJRender {
	private static final int MALLET_ANGLE_REAR = -45;

	public StockModel(OBJModel objModel, Collection<String> textureNames) {
		super(objModel, textureNames);
	}

	private boolean isBuilt;
	private List<RenderComponentType> availComponents;

	private double distanceTraveled;
	private void initComponents(EntityBuildableRollingStock stock) {
		this.isBuilt = stock.isBuilt();
		
		if (!isBuilt) {
			this.availComponents = new ArrayList<RenderComponentType>();
			for (ItemComponentType item : stock.getItemComponents()) {
				this.availComponents.addAll(item.render);
			}
		}
	}
	
	private void drawComponent(RenderComponent component) {
		if (component != null) {
			if (!isBuilt) {
				if (!availComponents.contains(component.type)) {
					// MISSING COMPONENT
					return;
				}
				// remove first
				availComponents.remove(component.type);
			}
			Minecraft.getMinecraft().mcProfiler.startSection("render");
			GL11.glPushMatrix();
			GL11.glScaled(component.scale, component.scale, component.scale);
			drawGroups(component.modelIDs);
			GL11.glPopMatrix();
			Minecraft.getMinecraft().mcProfiler.endSection();
		}
	}
	
	private void drawComponents(List<RenderComponent> components) {
		if (components == null) {
			return;
		}
		for (RenderComponent component : components) {
			this.drawComponent(component);
		}
	}

	public void draw(EntityRollingStock stock, float partialTicks) {
		GLBoolTracker tex = new GLBoolTracker(GL11.GL_TEXTURE_2D, super.hasTexture());
		
		
		if (stock instanceof EntityMoveableRollingStock) {
			EntityMoveableRollingStock mstock = (EntityMoveableRollingStock) stock;
			this.distanceTraveled = mstock.distanceTraveled + mstock.getCurrentSpeed().minecraft() * mstock.getTickSkew() * partialTicks * 1.1; 
		} else {
			this.distanceTraveled = 0;
		}

		this.bindTexture(stock.texture);
		
		if (stock instanceof LocomotiveSteam) {
			drawSteamLocomotive((LocomotiveSteam) stock);
		} else if (stock instanceof LocomotiveDiesel) {
			drawDieselLocomotive((LocomotiveDiesel)stock);
		} else if (stock instanceof EntityMoveableRollingStock) {
			drawStandardStock((EntityMoveableRollingStock) stock);
		} else {
			draw();
		}
		
		drawCargo(stock);
		
		this.restoreTexture();
		
		tex.restore();
	}
	
	public void drawCargo(EntityRollingStock stock) {
		if (stock instanceof Freight) {
			Freight freight = (Freight) stock;
			FreightDefinition def = freight.getDefinition();
			int fill = freight.getPercentCargoFull();
			
			List<RenderComponent> cargoLoads = def.getComponents(RenderComponentType.CARGO_FILL_X, stock.gauge);
			if (cargoLoads != null) {
				//this sorts through all the cargoLoad objects
				for (RenderComponent cargoLoad : cargoLoads) {
					if (cargoLoad.id <= fill) {
						drawComponent(cargoLoad);
						//if the stock should only render the current cargo load it'll stop at the highest matching load
						if (def.shouldShowCurrentLoadOnly()) {
							break;
						}
					}
				}
			}
		}
	}

	private void drawStandardStock(EntityMoveableRollingStock stock) {
		EntityRollingStockDefinition def = stock.getDefinition();
		
		initComponents(stock);
		
		drawComponent(def.getComponent(RenderComponentType.FRAME, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.SHELL, stock.gauge));
		
		drawFrameWheels(stock);

		if (def.getComponent(RenderComponentType.BOGEY_POS, "FRONT", stock.gauge) != null) {
			GlStateManager.pushMatrix();
			GlStateManager.translate(-def.getBogeyFront(stock.gauge), 0, 0);
			GlStateManager.rotate(180 - stock.getFrontYaw(), 0, 1, 0);		
			GlStateManager.rotate(-(180 - stock.rotationYaw), 0, 1, 0);
			GlStateManager.translate(def.getBogeyFront(stock.gauge), 0, 0);
			drawComponent(def.getComponent(RenderComponentType.BOGEY_POS, "FRONT", stock.gauge));
			List<RenderComponent> wheels = def.getComponents(RenderComponentType.BOGEY_POS_WHEEL_X, "FRONT", stock.gauge);
			if (wheels != null) {
				for (RenderComponent wheel : wheels) {
					double circumference = wheel.height() * (float) Math.PI;
					double relDist = distanceTraveled % circumference;
					Vec3d wheelPos = wheel.center();
					GlStateManager.pushMatrix();
					GlStateManager.translate(wheelPos.x, wheelPos.y, wheelPos.z);
					GlStateManager.rotate((float) (360 * relDist / circumference), 0, 0, 1);
					GlStateManager.translate(-wheelPos.x, -wheelPos.y, -wheelPos.z);
					drawComponent(wheel);
					GlStateManager.popMatrix();
				}
			}
			GlStateManager.popMatrix();
		}
		
		if (def.getComponent(RenderComponentType.BOGEY_POS, stock.gauge) != null) {
			GlStateManager.pushMatrix();
			GlStateManager.translate(-def.getBogeyRear(stock.gauge), 0, 0);
			GlStateManager.rotate(180 - stock.getRearYaw(), 0, 1, 0);
			GlStateManager.rotate(-(180 - stock.rotationYaw), 0, 1, 0);
			GlStateManager.translate(def.getBogeyRear(stock.gauge), 0, 0);
			drawComponent(def.getComponent(RenderComponentType.BOGEY_POS, "REAR", stock.gauge));
			List<RenderComponent> wheels = def.getComponents(RenderComponentType.BOGEY_POS_WHEEL_X, "REAR", stock.gauge);
			if (wheels != null) {
				for (RenderComponent wheel : wheels) {
					double circumference = wheel.height() * (float) Math.PI;
					double relDist = distanceTraveled % circumference;
					Vec3d wheelPos = wheel.center();
					GlStateManager.pushMatrix();
					GlStateManager.translate(wheelPos.x, wheelPos.y, wheelPos.z);
					GlStateManager.rotate((float) (360 * relDist / circumference), 0, 0, 1);
					GlStateManager.translate(-wheelPos.x, -wheelPos.y, -wheelPos.z);
					drawComponent(wheel);
					GlStateManager.popMatrix();
				}
			}
			GlStateManager.popMatrix();
		}

		if (this.isBuilt) {
			drawComponent(def.getComponent(RenderComponentType.REMAINING, stock.gauge));
		}
	}

	private void drawFrameWheels(EntityMoveableRollingStock stock) {
		EntityRollingStockDefinition def = stock.getDefinition();
		
		List<RenderComponent> wheels = def.getComponents(RenderComponentType.FRAME_WHEEL_X, stock.gauge);
		if (wheels != null) {
			for (RenderComponent wheel : wheels) {
				double circumference = wheel.height() * (float) Math.PI;
				double relDist = distanceTraveled % circumference;
				Vec3d wheelPos = wheel.center();
				GlStateManager.pushMatrix();
				GlStateManager.translate(wheelPos.x, wheelPos.y, wheelPos.z);
				GlStateManager.rotate((float) (360 * relDist / circumference), 0, 0, 1);
				GlStateManager.translate(-wheelPos.x, -wheelPos.y, -wheelPos.z);
				drawComponent(wheel);
				GlStateManager.popMatrix();
			}
		}
	}

	private void drawDieselLocomotive(EntityMoveableRollingStock stock) {
		EntityRollingStockDefinition def = stock.getDefinition();
		
		drawStandardStock(stock);

		drawComponent(def.getComponent(RenderComponentType.FUEL_TANK, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.ALTERNATOR, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.ENGINE_BLOCK, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.GEARBOX, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.FLUID_COUPLING, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.FINAL_DRIVE, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.TORQUE_CONVERTER, stock.gauge));
		drawComponents(def.getComponents(RenderComponentType.FAN_X, stock.gauge));
		drawComponents(def.getComponents(RenderComponentType.DRIVE_SHAFT_X, stock.gauge));
		
		drawComponents(def.getComponents(RenderComponentType.CAB, stock.gauge));
		drawComponents(def.getComponents(RenderComponentType.WHISTLE, stock.gauge));
		drawComponents(def.getComponents(RenderComponentType.BELL, stock.gauge));
		drawComponents(def.getComponents(RenderComponentType.HORN, stock.gauge));
	}


	private void drawSteamLocomotive(LocomotiveSteam stock) {
		LocomotiveSteamDefinition def = stock.getDefinition();
		
		initComponents(stock);

		drawBogies(stock);
		drawFrameWheels(stock);

		switch (def.getValveGear()) {
		case WALSCHAERTS:
			{
				List<RenderComponent> wheels = def.getComponents(RenderComponentType.WHEEL_DRIVER_X, stock.gauge);
				drawDrivingWheels(stock, wheels);
				MultiRenderComponent center = new MultiRenderComponent(wheels).scale(stock.gauge);
				RenderComponent wheel = wheels.get(wheels.size() / 2);
				drawWalschaerts(stock, "LEFT", 0, wheel.height(), center.center(), wheel.center());
				drawWalschaerts(stock, "RIGHT", -90, wheel.height(), center.center(), wheel.center());
			}
			break;
		case TRI_WALSCHAERTS:{
			List<RenderComponent> wheels = def.getComponents(RenderComponentType.WHEEL_DRIVER_X, stock.gauge);
			drawDrivingWheels(stock, wheels);
			MultiRenderComponent center = new MultiRenderComponent(wheels).scale(stock.gauge);
			RenderComponent wheel = wheels.get(wheels.size() / 2);
			drawWalschaerts(stock, "LEFT", 0, wheel.height(), center.center(), wheel.center());
			drawWalschaerts(stock, "RIGHT", -240, wheel.height(), center.center(), wheel.center());
			drawWalschaerts(stock, "CENTER", -120, wheel.height(), wheels.get(0).center(), wheels.get(0).center());
			break;
		}
		case MALLET_WALSCHAERTS:
			{
				GL11.glPushMatrix();
				
				RenderComponent frontLocomotive = def.getComponent(RenderComponentType.FRONT_LOCOMOTIVE, stock.gauge);

                Vec3d frontVec = frontLocomotive.center();
                Vec3d frontPos = stock.predictFrontBogeyPosition((float) (-frontVec.x - def.getBogeyFront(stock.gauge)));
                Vec3d frontNext = stock.predictFrontBogeyPosition((float) (-frontVec.x - def.getBogeyFront(stock.gauge) - 0.5));
                float frontWheelYaw = VecUtil.toYaw(frontPos.subtract(frontNext));
                float frontYaw = VecUtil.toYaw(frontPos)+stock.rotationYaw+180;
				float frontPitch = -VecUtil.toPitch(VecUtil.rotateYaw(frontPos, stock.rotationYaw+180))+90 +stock.rotationPitch;

                GL11.glRotated(frontYaw, 0, 1, 0);
                GL11.glRotated(frontPitch, 0, 0, 1);

                if (frontPos.distanceTo(frontNext) > 0.1) {
					GL11.glTranslated(frontVec.x, frontVec.y, frontVec.z);
					GL11.glRotated(frontWheelYaw + stock.rotationYaw - frontYaw + 180, 0, 1, 0);
					GL11.glTranslated(-frontVec.x, -frontVec.y, -frontVec.z);
				}

				List<RenderComponent> wheels = def.getComponents(RenderComponentType.WHEEL_DRIVER_FRONT_X, stock.gauge);
				MultiRenderComponent center = new MultiRenderComponent(wheels).scale(stock.gauge);
				drawComponent(def.getComponent(RenderComponentType.STEAM_CHEST_FRONT, stock.gauge));
				drawComponent(frontLocomotive);
				drawDrivingWheels(stock, wheels);
				RenderComponent wheel = wheels.get(wheels.size() / 2);
				drawWalschaerts(stock, "LEFT_FRONT", 0, wheel.height(), center.center(), wheel.center());
				drawWalschaerts(stock, "RIGHT_FRONT", -90, wheel.height(), center.center(), wheel.center());
				GL11.glPopMatrix();
			}
			{
				List<RenderComponent> wheels = def.getComponents(RenderComponentType.WHEEL_DRIVER_REAR_X, stock.gauge);
				MultiRenderComponent center = new MultiRenderComponent(wheels).scale(stock.gauge);
				drawDrivingWheels(stock, wheels);
				RenderComponent wheel = wheels.get(wheels.size() / 2);
				drawWalschaerts(stock, "LEFT_REAR", 0 + MALLET_ANGLE_REAR, center.height(), center.center(), wheel.center());
				drawWalschaerts(stock, "RIGHT_REAR", -90 + MALLET_ANGLE_REAR,  center.height(), center.center(), wheel.center());
			}
			break;
		case CLIMAX:
			break;
		case SHAY:
			break;
		case HIDDEN:
			{
				List<RenderComponent> wheels = def.getComponents(RenderComponentType.WHEEL_DRIVER_X, stock.gauge);
				drawDrivingWheels(stock, wheels);
			}
			break;
		case STEPHENSON:
			{
				List<RenderComponent> wheels = def.getComponents(RenderComponentType.WHEEL_DRIVER_X, stock.gauge);
				drawDrivingWheels(stock, wheels);
				MultiRenderComponent center = new MultiRenderComponent(wheels).scale(stock.gauge);
				RenderComponent wheel = wheels.get(wheels.size() / 2);
				drawStephenson(stock, "LEFT", 0, wheel.height(), center.center(), wheel.center());
				drawStephenson(stock, "RIGHT", -90, wheel.height(), center.center(), wheel.center());
			}
			break;
		case T1:
			drawComponent(def.getComponent(RenderComponentType.STEAM_CHEST_FRONT, stock.gauge));
			{
				List<RenderComponent> wheels = def.getComponents(RenderComponentType.WHEEL_DRIVER_FRONT_X, stock.gauge);
				drawDrivingWheels(stock, wheels);
				MultiRenderComponent center = new MultiRenderComponent(wheels).scale(stock.gauge);
				RenderComponent wheel = wheels.get(wheels.size() / 2);
				drawT1(stock, "LEFT_FRONT", 0, wheel.height(), center.center(), wheel.center());
				drawT1(stock, "RIGHT_FRONT", -90, wheel.height(), center.center(), wheel.center());
			}
			{
				List<RenderComponent> wheels = def.getComponents(RenderComponentType.WHEEL_DRIVER_REAR_X, stock.gauge);
				drawDrivingWheels(stock, wheels);
				MultiRenderComponent center = new MultiRenderComponent(wheels).scale(stock.gauge);
				RenderComponent wheel = wheels.get(wheels.size() / 2);
				
				drawT1(stock, "LEFT_REAR", 0 + MALLET_ANGLE_REAR, wheel.height(), center.center(), wheel.center());
				drawT1(stock, "RIGHT_REAR", -90 + MALLET_ANGLE_REAR, wheel.height(), center.center(), wheel.center());
			}
			break;
		}
		
		// Draw remaining groups

		drawComponent(def.getComponent(RenderComponentType.FRAME, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.SHELL, stock.gauge));
		
		drawComponents(def.getComponents(RenderComponentType.BOILER_SEGMENT_X, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.FIREBOX, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.SMOKEBOX, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.STEAM_CHEST, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.STEAM_CHEST_REAR, stock.gauge));
		drawComponent(def.getComponent(RenderComponentType.PIPING, stock.gauge));
		drawComponents(def.getComponents(RenderComponentType.CYLINDER_SIDE, stock.gauge));
		
		drawComponents(def.getComponents(RenderComponentType.CAB, stock.gauge));
		drawComponents(def.getComponents(RenderComponentType.WHISTLE, stock.gauge));
		drawComponents(def.getComponents(RenderComponentType.BELL, stock.gauge));
		drawComponents(def.getComponents(RenderComponentType.HORN, stock.gauge));
		
		if (stock.isBuilt()) {
			drawComponent(def.getComponent(RenderComponentType.REMAINING, stock.gauge));
		}
	}


	private void drawDrivingWheels(LocomotiveSteam stock, List<RenderComponent> wheels) {
		for (RenderComponent wheel : wheels) {
			double circumference = wheel.height() * (float) Math.PI;
			double relDist = distanceTraveled % circumference;
			double wheelAngle = 360 * relDist / circumference;
			if (wheel.type == RenderComponentType.WHEEL_DRIVER_REAR_X) {
				//MALLET HACK
				wheelAngle += MALLET_ANGLE_REAR;
			}
			Vec3d wheelPos = wheel.center();
			GlStateManager.pushMatrix();
			GlStateManager.translate(wheelPos.x, wheelPos.y, wheelPos.z);
			GlStateManager.rotate((float) wheelAngle, 0, 0, 1);
			GlStateManager.translate(-wheelPos.x, -wheelPos.y, -wheelPos.z);
			drawComponent(wheel);
			GlStateManager.popMatrix();
		}
	}


	private void drawBogies(EntityMoveableRollingStock stock) {
		EntityRollingStockDefinition def = stock.getDefinition();
		
		RenderComponent frontBogey = def.getComponent(RenderComponentType.BOGEY_FRONT, stock.gauge);
		List<RenderComponent> frontBogeyWheels = def.getComponents(RenderComponentType.BOGEY_FRONT_WHEEL_X, stock.gauge);
		RenderComponent rearBogey = def.getComponent(RenderComponentType.BOGEY_REAR, stock.gauge);
		List<RenderComponent> rearBogeyWheels = def.getComponents(RenderComponentType.BOGEY_REAR_WHEEL_X, stock.gauge);

		if (frontBogey != null) {
			Vec3d frontVec = frontBogey.center();
			Vec3d frontPos = stock.predictFrontBogeyPosition((float) (-frontVec.x - def.getBogeyFront(stock.gauge)));
			Vec3d frontNext = stock.predictFrontBogeyPosition((float) (-frontVec.x - def.getBogeyFront(stock.gauge) - 0.5));
			float frontWheelYaw = VecUtil.toYaw(frontPos.subtract(frontNext));
			float frontYaw = VecUtil.toYaw(frontPos)+stock.rotationYaw+180;
			float frontPitch = -VecUtil.toPitch(VecUtil.rotateYaw(frontPos, stock.rotationYaw+180))+90 +stock.rotationPitch;

			GL11.glPushMatrix();
			{
				GL11.glRotated(frontYaw, 0, 1, 0);
				GL11.glRotated(frontPitch, 0, 0, 1);

				if (frontPos.distanceTo(frontNext) > 0.1) {
					GL11.glTranslated(frontVec.x, frontVec.y, frontVec.z);
					GL11.glRotated(frontWheelYaw + stock.rotationYaw - frontYaw + 180, 0, 1, 0);
					GL11.glTranslated(-frontVec.x, -frontVec.y, -frontVec.z);
				}

				drawComponent(frontBogey);
				if (frontBogeyWheels != null) {
					for (RenderComponent wheel : frontBogeyWheels) {
						double circumference = wheel.height() * (float) Math.PI;
						double relDist = distanceTraveled % circumference;
						Vec3d wheelPos = wheel.center();
						GL11.glPushMatrix();
						{
							GL11.glTranslated(wheelPos.x, wheelPos.y, wheelPos.z);
							GL11.glRotatef((float) (360 * relDist / circumference), 0, 0, 1);
							GL11.glTranslated(-wheelPos.x, -wheelPos.y, -wheelPos.z);
							drawComponent(wheel);
						}
						GL11.glPopMatrix();
					}
				}
			}
			GL11.glPopMatrix();
		}
		if (rearBogey != null)
		{
			Vec3d rearVec = rearBogey.center();
			Vec3d rearPos = stock.predictRearBogeyPosition((float) (-rearVec.x - def.getBogeyRear(stock.gauge)));
			Vec3d rearNext = stock.predictRearBogeyPosition((float) (-rearVec.x - def.getBogeyRear(stock.gauge) - 0.5));
			float rearWheelYaw = VecUtil.toYaw(rearPos.subtract(rearNext));
			float rearYaw = VecUtil.toYaw(rearPos)+stock.rotationYaw;
			float rearPitch = VecUtil.toPitch(VecUtil.rotateYaw(rearPos, stock.rotationYaw+180))-90 +stock.rotationPitch;

			GL11.glPushMatrix();
			{
				GL11.glRotated(rearYaw, 0, 1, 0);
				GL11.glRotated(rearPitch, 0, 0, 1);

				if (rearPos.distanceTo(rearNext) > 0.1) {
					GL11.glTranslated(rearVec.x, rearVec.y, rearVec.z);
					GL11.glRotated(rearWheelYaw + stock.rotationYaw - rearYaw + 180, 0, 1, 0);
					GL11.glTranslated(-rearVec.x, -rearVec.y, -rearVec.z);
				}

				drawComponent(rearBogey);
				if (rearBogeyWheels != null) {
					for (RenderComponent wheel : rearBogeyWheels) {
						double circumference = wheel.height() * (float) Math.PI;
						double relDist = distanceTraveled % circumference;
						Vec3d wheelPos = wheel.center();
						GL11.glPushMatrix();
						{
							GL11.glTranslated(wheelPos.x, wheelPos.y, wheelPos.z);
							GL11.glRotatef((float) (360 * relDist / circumference), 0, 0, 1);
							GL11.glTranslated(-wheelPos.x, -wheelPos.y, -wheelPos.z);
							drawComponent(wheel);
						}
						GL11.glPopMatrix();
					}
				}
			}
			GL11.glPopMatrix();
		}
	}
	
	// PISTON/MAIN/SIDE
	private void drawStephenson(LocomotiveSteam stock, String side, int wheelAngleOffset, double diameter, Vec3d wheelCenter, Vec3d wheelPos) {
		LocomotiveSteamDefinition def = stock.getDefinition();
		
		double circumference = diameter * (float) Math.PI;
		double relDist = distanceTraveled % circumference;
		double wheelAngle = 360 * relDist / circumference + wheelAngleOffset;
		
		RenderComponent connectingRod = requireComponent(def, RenderComponentType.SIDE_ROD_SIDE, side, stock.gauge);
		RenderComponent drivingRod = requireComponent(def, RenderComponentType.MAIN_ROD_SIDE, side, stock.gauge);
		RenderComponent pistonRod = requireComponent(def, RenderComponentType.PISTON_ROD_SIDE, side, stock.gauge);


		// Center of the connecting rod, may not line up with a wheel directly
		Vec3d connRodPos = connectingRod.center();
		// Wheel Center is the center of all wheels, may not line up with a wheel directly
		// The difference between these centers is the radius of the connecting rod movement
		double connRodRadius = connRodPos.x - wheelCenter.x;
		// Find new connecting rod pos based on the connecting rod rod radius 
		Vec3d connRodMovment = VecUtil.fromWrongYaw(connRodRadius, (float) wheelAngle);
		
		// Draw Connecting Rod
		GL11.glPushMatrix();
		{
			// Move to origin
			GL11.glTranslated(-connRodRadius, 0, 0);
			// Apply connection rod movement
			GL11.glTranslated(connRodMovment.x, connRodMovment.z, 0);
			
			drawComponent(connectingRod);
		}
		GL11.glPopMatrix();
		
		// X: rear driving rod X - driving rod height/2 (hack assuming diameter == height)
		// Y: Center of the rod
		// Z: does not really matter due to rotation axis
		Vec3d drivingRodRotPoint = new Vec3d(drivingRod.max().x - drivingRod.height()/2, drivingRod.center().y, drivingRod.max().z);
		// Angle for movement height vs driving rod length (adjusted for assumed diameter == height, both sides == 2r)
		float drivingRodAngle = (float) Math.toDegrees(MathHelper.atan2(connRodMovment.z, drivingRod.length() - drivingRod.height()));
		
		// Draw driving rod
		GL11.glPushMatrix();
		{
			// Move to conn rod center
			GL11.glTranslated(-connRodRadius, 0, 0);
			// Apply conn rod movement
			GL11.glTranslated(connRodMovment.x, connRodMovment.z, 0);
			
			// Move to rot point center
			GL11.glTranslated(drivingRodRotPoint.x, drivingRodRotPoint.y, drivingRodRotPoint.z);
			// Rotate rod angle
			GL11.glRotated(drivingRodAngle, 0, 0, 1);
			// Move back from rot point center
			GL11.glTranslated(-drivingRodRotPoint.x, -drivingRodRotPoint.y, -drivingRodRotPoint.z);
			
			drawComponent(drivingRod);
		}
		GL11.glPopMatrix();
		
		// Piston movement is rod movement offset by the rotation radius
		// Not 100% accurate, missing the offset due to angled driving rod
		double pistonDelta = connRodMovment.x - connRodRadius;
		
		// Draw piston rod and cross head
		GL11.glPushMatrix();
		{
			GL11.glTranslated(pistonDelta, 0, 0);
			drawComponent(pistonRod);
		}
		GL11.glPopMatrix();
	}
	
	private void drawT1(LocomotiveSteam stock, String side, int wheelAngleOffset, double diameter, Vec3d wheelCenter, Vec3d wheelPos) {
		LocomotiveSteamDefinition def = stock.getDefinition();
		
		double circumference = diameter * (float) Math.PI;
		double relDist = distanceTraveled % circumference;
		double wheelAngle = 360 * relDist / circumference + wheelAngleOffset;
		
		RenderComponent connectingRod = requireComponent(def, RenderComponentType.SIDE_ROD_SIDE, side, stock.gauge);
		RenderComponent drivingRod = requireComponent(def, RenderComponentType.MAIN_ROD_SIDE, side, stock.gauge);
		RenderComponent pistonRod = requireComponent(def, RenderComponentType.PISTON_ROD_SIDE, side, stock.gauge);
		
		// Center of the connecting rod, may not line up with a wheel directly
		Vec3d connRodPos = connectingRod.center();
		// Wheel Center is the center of all wheels, may not line up with a wheel directly
		// The difference between these centers is the radius of the connecting rod movement
		double connRodRadius = connRodPos.x - wheelCenter.x;
		// Find new connecting rod pos based on the connecting rod rod radius 
		Vec3d connRodMovment = VecUtil.fromWrongYaw(connRodRadius, (float) wheelAngle);
		
		// Draw Connecting Rod
		GL11.glPushMatrix();
		{
			// Move to origin
			GL11.glTranslated(-connRodRadius, 0, 0);
			// Apply connection rod movement
			GL11.glTranslated(connRodMovment.x, connRodMovment.z, 0);
			
			drawComponent(connectingRod);
		}
		GL11.glPopMatrix();
		
		// X: rear driving rod X - driving rod height/2 (hack assuming diameter == height)
		// Y: Center of the rod
		// Z: does not really matter due to rotation axis
		Vec3d drivingRodRotPoint = new Vec3d(drivingRod.max().x - drivingRod.height()/2, drivingRod.center().y, drivingRod.max().z);
		// Angle for movement height vs driving rod length (adjusted for assumed diameter == height, both sides == 2r)
		float drivingRodAngle = (float) Math.toDegrees(MathHelper.atan2(connRodMovment.z, drivingRod.length() - drivingRod.height()));
		
		// Draw driving rod
		GL11.glPushMatrix();
		{
			// Move to conn rod center
			GL11.glTranslated(-connRodRadius, 0, 0);
			// Apply conn rod movement
			GL11.glTranslated(connRodMovment.x, connRodMovment.z, 0);
			
			// Move to rot point center
			GL11.glTranslated(drivingRodRotPoint.x, drivingRodRotPoint.y, drivingRodRotPoint.z);
			// Rotate rod angle
			GL11.glRotated(drivingRodAngle, 0, 0, 1);
			// Move back from rot point center
			GL11.glTranslated(-drivingRodRotPoint.x, -drivingRodRotPoint.y, -drivingRodRotPoint.z);
			
			drawComponent(drivingRod);
		}
		GL11.glPopMatrix();
		
		// Piston movement is rod movement offset by the rotation radius
		// Not 100% accurate, missing the offset due to angled driving rod
		double pistonDelta = connRodMovment.x - connRodRadius;
		
		// Draw piston rod and cross head
		GL11.glPushMatrix();
		{
			GL11.glTranslated(pistonDelta, 0, 0);
			drawComponent(pistonRod);
		}
		GL11.glPopMatrix();
	}
	
	private RenderComponent requireComponent(LocomotiveSteamDefinition def, RenderComponentType rct, String side, Gauge gauge) {
		RenderComponent comp = def.getComponent(rct, side, gauge);
		if (comp == null) {
			ImmersiveRailroading.error("Missing component for %s: %s %s", def.name(), rct, side);
		}
		
		return comp;
	}
	
	private void drawWalschaerts(LocomotiveSteam stock, String side, int wheelAngleOffset, double diameter, Vec3d wheelCenter, Vec3d wheelPos) {
		LocomotiveSteamDefinition def = stock.getDefinition();
		
		double circumference = diameter * (float) Math.PI;
		double relDist = distanceTraveled % circumference;
		double wheelAngle = 360 * relDist / circumference + wheelAngleOffset;
		
		RenderComponent connectingRod = requireComponent(def, RenderComponentType.SIDE_ROD_SIDE, side, stock.gauge);
		RenderComponent drivingRod = requireComponent(def, RenderComponentType.MAIN_ROD_SIDE, side, stock.gauge);
		RenderComponent pistonRod = requireComponent(def, RenderComponentType.PISTON_ROD_SIDE, side, stock.gauge);
		RenderComponent crossHead = requireComponent(def, RenderComponentType.UNION_LINK_SIDE, side, stock.gauge);
		RenderComponent combinationLever = requireComponent(def, RenderComponentType.COMBINATION_LEVER_SIDE, side, stock.gauge);
		RenderComponent returnCrank = requireComponent(def, RenderComponentType.ECCENTRIC_CRANK_SIDE, side, stock.gauge);
		RenderComponent returnCrankRod = requireComponent(def, RenderComponentType.ECCENTRIC_ROD_SIDE, side, stock.gauge);
		RenderComponent slottedLink = requireComponent(def, RenderComponentType.EXPANSION_LINK_SIDE, side, stock.gauge);
		RenderComponent radiusBar = requireComponent(def, RenderComponentType.RADIUS_BAR_SIDE, side, stock.gauge);
		
		// Center of the connecting rod, may not line up with a wheel directly
		Vec3d connRodPos = connectingRod.center();
		// Wheel Center is the center of all wheels, may not line up with a wheel directly
		// The difference between these centers is the radius of the connecting rod movement
		double connRodRadius = connRodPos.x - wheelCenter.x;
		// Find new connecting rod pos based on the connecting rod rod radius 
		Vec3d connRodMovment = VecUtil.fromWrongYaw(connRodRadius, (float) wheelAngle);
		
		// Draw Connecting Rod
		GL11.glPushMatrix();
		{
			// Move to origin
			GL11.glTranslated(-connRodRadius, 0, 0);
			// Apply connection rod movement
			GL11.glTranslated(connRodMovment.x, connRodMovment.z, 0);
			
			drawComponent(connectingRod);
		}
		GL11.glPopMatrix();
		
		// X: rear driving rod X - driving rod height/2 (hack assuming diameter == height)
		// Y: Center of the rod
		// Z: does not really matter due to rotation axis
		Vec3d drivingRodRotPoint = new Vec3d(drivingRod.max().x - drivingRod.height()/2, drivingRod.center().y, drivingRod.max().z);
		// Angle for movement height vs driving rod length (adjusted for assumed diameter == height, both sides == 2r)
		float drivingRodAngle = (float) Math.toDegrees(MathHelper.atan2(connRodMovment.z, drivingRod.length() - drivingRod.height()));
		
		// Draw driving rod
		GL11.glPushMatrix();
		{
			// Move to conn rod center
			GL11.glTranslated(-connRodRadius, 0, 0);
			// Apply conn rod movement
			GL11.glTranslated(connRodMovment.x, connRodMovment.z, 0);
			
			// Move to rot point center
			GL11.glTranslated(drivingRodRotPoint.x, drivingRodRotPoint.y, drivingRodRotPoint.z);
			// Rotate rod angle
			GL11.glRotated(drivingRodAngle, 0, 0, 1);
			// Move back from rot point center
			GL11.glTranslated(-drivingRodRotPoint.x, -drivingRodRotPoint.y, -drivingRodRotPoint.z);
			
			drawComponent(drivingRod);
		}
		GL11.glPopMatrix();
		
		// Piston movement is rod movement offset by the rotation radius
		// Not 100% accurate, missing the offset due to angled driving rod
		double pistonDelta = connRodMovment.x - connRodRadius;
		
		// Draw piston rod and cross head
		GL11.glPushMatrix();
		{
			GL11.glTranslated(pistonDelta, 0, 0);
			drawComponent(pistonRod);
			drawComponent(crossHead);
		}
		GL11.glPopMatrix();

		Vec3d returnCrankRotPoint = returnCrank.max().addVector(-returnCrank.height()/2, -returnCrank.height()/2, 0);
		Vec3d wheelRotationOffset = VecUtil.fromWrongYaw(returnCrankRotPoint.x - wheelPos.x, (float) wheelAngle);
		Vec3d returnCrankOriginOffset = wheelPos.addVector(wheelRotationOffset.x, wheelRotationOffset.z, 0);
		double returnCrankAngle = wheelAngle + 90 + 30;
		GL11.glPushMatrix();
		{
			// Move to crank offset from origin
			GL11.glTranslated(returnCrankOriginOffset.x, returnCrankOriginOffset.y, 0);
			// Rotate crank
			GL11.glRotated(returnCrankAngle, 0, 0, 1);
			// Draw return crank at current position
			GL11.glTranslated(-returnCrankRotPoint.x, -returnCrankRotPoint.y, 0);
			drawComponent(returnCrank);			
		}
		GL11.glPopMatrix();

		// We take the length of the crank and subtract the radius on either side.
		// We use rod radius and crank radius since it can be a funny shape 
		double returnCrankLength = -(returnCrank.length() - returnCrank.height()/2 - returnCrankRod.height()/2);
		// Rotation offset around the return crank point
		Vec3d returnCrankRotationOffset = VecUtil.fromWrongYaw(returnCrankLength, (float) returnCrankAngle-90);
		// Combine wheel->crankpoint offset and the crankpoint->crankrod offset 
		Vec3d returnCrankRodOriginOffset = returnCrankOriginOffset.addVector(returnCrankRotationOffset.x, returnCrankRotationOffset.z, 0);
		// Point about which the return crank rotates
		Vec3d returnCrankRodRotPoint = returnCrankRod.max().addVector(-returnCrankRod.height()/2, -returnCrankRod.height()/2, 0);
		// Length between return crank rod centers
		double returnCrankRodLength = returnCrankRod.length() - returnCrankRod.height()/2; 
		// Height that the return crank rod should shoot for
		double slottedLinkLowest = slottedLink.min().y + slottedLink.width()/2;
		// Fudge
		double returnCrankRodFudge = Math.abs(slottedLink.center().x - (returnCrankRodOriginOffset.x - returnCrankRodLength))/3;
		// Angle the return crank rod should be at to hit the slotted link
		float returnCrankRodRot = VecUtil.toWrongYaw(new Vec3d(slottedLinkLowest - returnCrankRodOriginOffset.y + returnCrankRodFudge, 0, returnCrankRodLength));
		GL11.glPushMatrix();
		{
			// Move to crank rod offset from origin
			GL11.glTranslated(returnCrankRodOriginOffset.x, returnCrankRodOriginOffset.y, 0);
			
			GL11.glRotated(returnCrankRodRot, 0, 0, 1);
			
			// Draw return crank rod at current position
			GL11.glTranslated(-returnCrankRodRotPoint.x, -returnCrankRodRotPoint.y, 0);
			drawComponent(returnCrankRod);
		}
		GL11.glPopMatrix();
		
		Vec3d returnCrankRodRotationOffset = VecUtil.fromWrongYaw(returnCrankRodLength, returnCrankRodRot+90);
		Vec3d returnCrankRodFarPoint = returnCrankRodOriginOffset.addVector(returnCrankRodRotationOffset.x, returnCrankRodRotationOffset.z, 0);
		// Slotted link rotation point
		Vec3d slottedLinkRotPoint = slottedLink.center();
		double slottedLinkRot = Math.toDegrees(MathHelper.atan2(-slottedLinkRotPoint.x + returnCrankRodFarPoint.x, slottedLinkRotPoint.y - returnCrankRodFarPoint.y));
		GL11.glPushMatrix();
		{
			// Move to origin
			GL11.glTranslated(slottedLinkRotPoint.x, slottedLinkRotPoint.y, 0);
			
			// Rotate around center point
			GL11.glRotated(slottedLinkRot, 0, 0, 1);
			
			// Draw slotted link at current position
			GL11.glTranslated(-slottedLinkRotPoint.x, -slottedLinkRotPoint.y, 0);
			drawComponent(slottedLink);
		}
		GL11.glPopMatrix();
		
		float throttle = stock.getReverser();
		double forwardMax = (slottedLink.min().y - slottedLinkRotPoint.y) * 0.4;
		double forwardMin = (slottedLink.max().y - slottedLinkRotPoint.y) * 0.65;
		double throttleSlotPos = 0;
		if (throttle > 0) {
			throttleSlotPos = forwardMax * throttle;
		} else {
			throttleSlotPos = forwardMin * -throttle;
		}
		
		double radiusBarSliding = Math.sin(Math.toRadians(-slottedLinkRot)) * (throttleSlotPos);
		
		Vec3d radiusBarClose = radiusBar.max();
		throttleSlotPos += slottedLinkRotPoint.y - radiusBarClose.y;
		
		float raidiusBarAngle = VecUtil.toWrongYaw(new Vec3d(radiusBar.length(), 0, throttleSlotPos))+90;
		
		GL11.glPushMatrix();
		{
			GL11.glTranslated(0, throttleSlotPos, 0);
			
			GL11.glTranslated(radiusBarSliding, 0, 0);
			
			GL11.glTranslated(radiusBarClose.x, radiusBarClose.y, 0);
			GL11.glRotated(raidiusBarAngle, 0, 0, 1);
			GL11.glTranslated(-radiusBarClose.x, -radiusBarClose.y, 0);
			drawComponent(radiusBar);
		}
		GL11.glPopMatrix();
		
		Vec3d radiusBarFar = radiusBar.min();
		//radiusBarSliding != correct TODO angle offset
		Vec3d radiusBarFarPoint = radiusBarFar.addVector(radiusBarSliding + combinationLever.width()/2, 0, 0);
		
		Vec3d combinationLeverRotPos = combinationLever.min().addVector(combinationLever.width()/2, combinationLever.width()/2, 0);
		
		Vec3d delta = radiusBarFarPoint.subtract(combinationLeverRotPos.addVector(pistonDelta, 0, 0));
		
		float combinationLeverAngle = VecUtil.toWrongYaw(new Vec3d(delta.x, 0, delta.y));

		GL11.glPushMatrix();
		{
			GL11.glTranslated(pistonDelta, 0, 0);
			GL11.glTranslated(combinationLeverRotPos.x, combinationLeverRotPos.y, 0);
			GL11.glRotated(combinationLeverAngle, 0, 0, 1);
			GL11.glTranslated(-combinationLeverRotPos.x, -combinationLeverRotPos.y, 0);
			drawComponent(combinationLever);
		}
		GL11.glPopMatrix();
	}
}
