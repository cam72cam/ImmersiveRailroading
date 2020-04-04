package cam72cam.immersiverailroading.util;

public class Speed {
	// 20 tps * 3.6(km/h)/(m/s)
	private static final double speedRatio = 20 * 3.6;

	public static final Speed ZERO = fromMetric(0);

	// m/tick
	private double internalSpeed;

	// create a speed object from meters per tick
	public static Speed fromMinecraft(double speed) {
		return new Speed(speed);
	}

	// create a speed object from kilometers per hour
	public static Speed fromMetric(double speed) {
		return new Speed(speed / speedRatio);
	}

	private Speed(double speed) {
		internalSpeed = speed;
	}

	//return m/tick
	public double minecraft() {
		return internalSpeed;
	}

	//returns km/hr
	public double metric() {
		return internalSpeed * speedRatio;
	}

	public String metricString() {
		return String.format("%.2f km/h", metric());
	}

	public boolean isZero() {
		return internalSpeed == 0;
	}
}
