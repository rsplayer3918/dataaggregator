import java.awt.Point;

/**
 * Represents a predicted mouse movement for ML-based antiban
 */
public class MouseMovement {
    private Point targetPoint;
    private boolean shouldMove;
    private int confidence; // 0-100, how confident the ML is about this movement

    public MouseMovement(Point target, boolean move) {
        this.targetPoint = target;
        this.shouldMove = move;
        this.confidence = 50; // Default medium confidence
    }

    public MouseMovement(Point target, boolean move, int confidence) {
        this.targetPoint = target;
        this.shouldMove = move;
        this.confidence = Math.max(0, Math.min(100, confidence));
    }

    /**
     * Get the target point for mouse movement
     */
    public Point getTargetPoint() {
        return targetPoint;
    }

    /**
     * Whether the mouse should move at all
     */
    public boolean shouldMove() {
        return shouldMove;
    }

    /**
     * Get the confidence level (0-100)
     */
    public int getConfidence() {
        return confidence;
    }

    /**
     * Set the confidence level
     */
    public void setConfidence(int confidence) {
        this.confidence = Math.max(0, Math.min(100, confidence));
    }

    /**
     * Calculate distance from current position
     */
    public double getDistance(Point current) {
        if (current == null || targetPoint == null) return 0;
        return Math.sqrt(Math.pow(targetPoint.x - current.x, 2) + Math.pow(targetPoint.y - current.y, 2));
    }

    /**
     * Check if this is a reasonable movement (not too far)
     */
    public boolean isReasonable(Point current, int maxDistance) {
        return getDistance(current) <= maxDistance;
    }

    @Override
    public String toString() {
        return String.format("MouseMovement[target:(%d,%d), shouldMove:%s, confidence:%d]",
                targetPoint != null ? targetPoint.x : -1,
                targetPoint != null ? targetPoint.y : -1,
                shouldMove, confidence);
    }
}