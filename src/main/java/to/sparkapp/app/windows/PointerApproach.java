package to.sparkapp.app.windows;

record PointerApproach(double horizontalPixels, double upwardPixels, long durationNanos, long recordedAtNanos) {
    static final long MAX_AGE_NANOS = 160_000_000L;
    private static final double MINIMUM_UPWARD_DISTANCE = 72;
    private static final double MINIMUM_UPWARD_SPEED = 950;

    boolean isFastUpward(double scaleX, double scaleY, long now) {
        if (durationNanos <= 0 || now < recordedAtNanos || now - recordedAtNanos > MAX_AGE_NANOS) {
            return false;
        }

        var horizontal = Math.abs(horizontalPixels) / scaleX;
        var upward = upwardPixels / scaleY;
        var speed = upward * 1_000_000_000.0 / durationNanos;

        // Require a deliberate vertical approach rather than edge jitter or a diagonal pass.
        return upward >= MINIMUM_UPWARD_DISTANCE && speed >= MINIMUM_UPWARD_SPEED && horizontal <= upward * 0.75;
    }
}
