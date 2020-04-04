package cam72cam.immersiverailroading.physics;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import cam72cam.immersiverailroading.util.Speed;

public class PhysicsAccummulator {
	//http://evilgeniustech.com/idiotsGuideToRailroadPhysics/HorsepowerAndTractiveEffort/
	//http://www.republiclocomotive.com/locomotive-power-calculations.html
	//http://www.wplives.org/forms_and_documents/Air_Brake_Principles.pdf

	public static final double MIN_SPEED = 0.00001;

	public static final double poundToNewton = 4.44822;

	public double tractiveEffortNewtons = 0;
	public double airBrakeEffect = 0;
	//lbs
	public double rollingResistanceNewtons = 0;
	public double gradeForceNewtons = 0;
	public double massToMoveKg = 0;
	public double wheelAdhesionNewtons = 0;
	public int count = 0;
	private TickPos pos;
	
	public PhysicsAccummulator(TickPos pos) {
		this.pos = pos;
	}

	//accumulates all the forces currently acting on this train car
	public void accumulate(EntityRollingStock stock, Boolean direction) {
		count++;
		
		massToMoveKg += stock.getWeight();
		
		if (!(stock instanceof EntityMoveableRollingStock)){
			return;
		}
		
		EntityMoveableRollingStock movable = ((EntityMoveableRollingStock)stock);

		//coefficient of rolling resistance * mass * gravity
		//perfect accuracy would calculate normal force instead of using gravity
		rollingResistanceNewtons += 0.0015 * stock.getWeight() * 9.8;

		TickPos posToUse = movable.getTickPos((int) movable.tickPosID);
		if(posToUse == null)
		{
			posToUse = pos;
		}

		// see http://hyperphysics.phy-astr.gsu.edu/hbase/mincl.html for slope physics
		// negative because a positive rotationPitch means stock is pointing uphill
		double gradeForceFactor = -Math.sin(Math.toRadians(posToUse.rotationPitch)) * Config.ConfigBalance.slopeMultiplier;
		gradeForceNewtons += stock.getWeight() * gradeForceFactor * 9.8 * (direction ? 1 : -1);

		if (stock instanceof Locomotive) {
			Locomotive loco = (Locomotive) stock;
			tractiveEffortNewtons += loco.getTractiveEffortNewtons(pos.speed) * (direction ? 1 : -1);

			// note, multiple locomotives in a train will conflict brake settings
			// maybe should be average of all of them?
			// previous implementation simply added all brake effects and capped at 1 (so 4 locos at 1/4 would make full braking power)
			airBrakeEffect = Math.max(loco.getAirBrake() * loco.getDefinition().getBrakePower() * loco.slipCoefficient(), airBrakeEffect);

			//wheelAdhesionNewtons += loco.getDefinition().getStartingTractionNewtons(stock.gauge); I have no idea where this came from
		}
		// mass * gravity * coefficient of friction
		wheelAdhesionNewtons += stock.getWeight() * 9.8 * 0.25;
		
		int slowdown = movable.getSpeedRetarderSlowdown(pos);
		//UNITS???? slowdown * weight in lb / 300
		rollingResistanceNewtons += slowdown * stock.getWeight() * 2.20462 / 300;
	}

	//gets velocity in m/t for 1 tick (1/20 second) of recorded acceleration forces
	public Speed getVelocity() {
		double currentMCVelocity = pos.speed.minecraft();
		double tickRatio = 1.0 / 400.0; // second^2 per tick^2

		// Note, brake strength (pad to wheel) is actually independent of wheel adhesion (wheel to rails)
		// It seems that we have just been assuming that brake strength is always strong enough to match wheel adhesion
		// Implementing over-braking is probably too immersive even for this mod
		// Note, this assumption means brakes are very powerful in game, roughly 2x better than real life
		double brakeForceNewtons = wheelAdhesionNewtons * airBrakeEffect * Config.ConfigBalance.brakeMultiplier;

		// a = f (to newtons) * m (to newtons)
		double deltaAccellTractiveMCVelocity = tractiveEffortNewtons / massToMoveKg * tickRatio;
		double deltaAccellRollingResistanceMCVelocity = rollingResistanceNewtons / massToMoveKg * tickRatio;
		double deltaAccellGradeMCVelocity = gradeForceNewtons / massToMoveKg * tickRatio; //implied * 1 for 1 tick per call
		double deltaAccellBrakeMCVelocity = brakeForceNewtons / massToMoveKg * tickRatio; //N / kg * s^2 / t^2 * t = m / t

		// no wind resistance is current;y given on stock definitions, so we approximate
		// http://eprints.whiterose.ac.uk/87909/1/The%20aerodynamics%20of%20freight_WRRO.pdf
		// wind force = coefficient * area * 1/2 * air pressure (~1.2 kg/m^3) * velocity^2
		double assumedStockFactor = .005; // coefficient

		// the parts of the equation shared per type (area, air pressure, velocity)
		double commonWindFactor = 10 * .5 * 1.2 * Math.pow(currentMCVelocity * 20, 2);// velocity * 20 for m/t -> m/s

		//the front car is fully, exposed, so it has a higher coefficient
		//the coefficient of a flat surface is ~2.1, we assume half that for the front train because most trains are blocky
		double windFrontNewtons = 1.05 * commonWindFactor; //velocity * 20 for m/t -> m/s
		double windOtherNewtons = assumedStockFactor * commonWindFactor * (count - 1); //count - 1 cars behind front car
		double deltaAccellWindMCVelocity = (windFrontNewtons + windOtherNewtons) / massToMoveKg * tickRatio;

		// tractive acceleration and grade are always applied
		// technically tractive force and braking force should be summed and capped to wheel adhesion
		// since they are normally not applied at both the same time and same direction, just ignore for simplicity
		double newMCVelocity = currentMCVelocity + deltaAccellTractiveMCVelocity + deltaAccellGradeMCVelocity;


		// friction and brakes are limited to stopping
		// Apply in the reverse direction of current travel
		double deltaDecell = -1 * Math.copySign(Math.min(deltaAccellRollingResistanceMCVelocity +
				deltaAccellBrakeMCVelocity + deltaAccellWindMCVelocity, Math.abs(newMCVelocity)), newMCVelocity);
		newMCVelocity = newMCVelocity + deltaDecell;

		if (Math.abs(newMCVelocity) < PhysicsAccummulator.MIN_SPEED) {
			newMCVelocity = 0;
		}

		return Speed.fromMinecraft(newMCVelocity);
	}
}