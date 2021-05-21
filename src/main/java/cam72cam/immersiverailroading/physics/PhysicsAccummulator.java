package cam72cam.immersiverailroading.physics;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.entity.ControllableStock;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.util.Speed;

public class PhysicsAccummulator {
	//http://evilgeniustech.com/idiotsGuideToRailroadPhysics/HorsepowerAndTractiveEffort/
	//http://www.republiclocomotive.com/locomotive-power-calculations.html
	//http://www.wplives.org/forms_and_documents/Air_Brake_Principles.pdf

	public double tractiveEffortNewtons = 0;
	public double airBrake = 0;
	//lbs
	public double rollingResistanceNewtons = 0;
	public double gradeForceNewtons = 0;
	public double massToMoveKg = 0;
	public double brakeAdhesionNewtons = 0;
	public int count = 0;
	private TickPos pos;
	
	public PhysicsAccummulator(TickPos pos) {
		this.pos = pos;
	}

	public void accumulate(EntityRollingStock stock, Boolean direction) {
		count++;
		
		massToMoveKg += stock.getWeight();
		
		if (!(stock instanceof EntityMoveableRollingStock)){
			return;
		}
		
		EntityMoveableRollingStock movable = ((EntityMoveableRollingStock)stock);
		
		// SHOULD THIS HAVE DIRECTION MULT?
		double stockMassLb = 2.20462 * stock.getWeight();
		rollingResistanceNewtons += 0.0015 * stockMassLb * 4.44822f;
		
		// SHOULD THIS HAVE DIRECTION MULT?
		if(!movable.ignoreSlope()) {
			double grade = -Math.tan(Math.toRadians(pos.rotationPitch % 90)) * Config.ConfigBalance.slopeMultiplier;
			// lbs * 1%gradeResistance * grade multiplier
			gradeForceNewtons += (stockMassLb / 100) * (grade * 100)  * 4.44822f;
		}

		if (stock instanceof ControllableStock) {
			ControllableStock controllable = (ControllableStock) stock;
			airBrake += Math.min(1, Math.pow(controllable.getAirBrake() * controllable.getDefinition().getBrakePower(), 2)) * controllable.slipCoefficient();
			brakeAdhesionNewtons += controllable.getDefinition().getStartingTractionNewtons(stock.gauge);
			if (controllable instanceof Locomotive) {
				Locomotive loco = (Locomotive) controllable;
				tractiveEffortNewtons += loco.getTractiveEffortNewtons(pos.speed) * (direction ? 1 : -1);
			}
		} else {
			// Air brake only applies 1/4th
			// 0.25 = steel wheel on steel rail	
			if(stock.getDefinition().hasBrakes()) {
				brakeAdhesionNewtons += stock.getWeight() * 0.25 * 0.25 * 4.44822f;
			}
		}
		
		int slowdown = movable.getSpeedRetarderSlowdown(pos);
		rollingResistanceNewtons += slowdown * stockMassLb / 300;
	}
	
	public Speed getVelocity() {
		double airBrakeNewtons = brakeAdhesionNewtons * Math.min(airBrake, 1) * Config.ConfigBalance.brakeMultiplier;
		
		// a = f (to newtons) * m (to newtons)
		double tractiveAccell = tractiveEffortNewtons / massToMoveKg;
		double resistanceAccell = rollingResistanceNewtons / massToMoveKg;
		double gradeAccell = gradeForceNewtons / massToMoveKg;
		double brakeAccell = airBrakeNewtons / massToMoveKg;
		
		double currentMCVelocity = pos.speed.minecraft();
		double deltaAccellTractiveMCVelocity = Speed.fromMetric(tractiveAccell).minecraft();
		
		
		double deltaAccellGradeMCVelocity = Speed.fromMetric(gradeAccell).minecraft();
		
		double deltaAccellRollingResistanceMCVelocity = Speed.fromMetric(resistanceAccell).minecraft();
		double deltaAccellBrakeMCVelocity = Speed.fromMetric(brakeAccell).minecraft();
		// Limit decell to current speed to trains stop
		// Apply in the reverse direction of current travel
		double deltaDecell = -1 * Math.copySign(Math.min(Math.abs(currentMCVelocity), deltaAccellRollingResistanceMCVelocity +  deltaAccellBrakeMCVelocity), currentMCVelocity);
		
		double newMCVelocity = currentMCVelocity + deltaAccellTractiveMCVelocity + deltaAccellGradeMCVelocity + deltaDecell;

		if (Math.abs(newMCVelocity) < 0.001) {
			newMCVelocity = 0;
		}
		
		return Speed.fromMinecraft(newMCVelocity);
	}
}