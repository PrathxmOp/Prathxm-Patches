package app.prathxm.chess.extension.stockfish;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

/**
 * A clean, stable vertical evaluation bar.
 * WDL is now rendered separately in WdlBarView.
 */
public class EvalBarView extends View {
    private float score = 0.0f; // from White's perspective (positive = white advantage)
    private boolean hasMate = false;
    private int mateIn = 0;
    private boolean flipped = false;

    private final Paint paintWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLine  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rectWhite = new RectF();
    private final RectF rectBlack = new RectF();

    public EvalBarView(Context context) {
        super(context);
        paintWhite.setColor(0xF0E8E4E0); // off-white, premium feel
        paintBlack.setColor(0xF0252220); // dark charcoal, premium feel

        paintLine.setColor(0xFF6B6966);
        paintLine.setStrokeWidth(1.5f);

        float density = context.getResources().getDisplayMetrics().density;
        paintText.setTextSize(9.5f * density);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setFakeBoldText(true);
    }

    /**
     * Update the bar data and reposition it next to the chess board.
     * Call this from the main thread.
     */
    public void update(int x, int y, int width, int height,
                       float score, boolean hasMate, int mateIn, boolean flipped) {
        float target = ratioFor(score);
        boolean firstShow = !hasValue || this.flipped != flipped;
        this.score   = score;
        this.hasMate = hasMate;
        this.mateIn  = mateIn;
        this.flipped = flipped;
        this.hasValue = true;
        animateTo(target, firstShow);

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) {
            lp = new ViewGroup.LayoutParams(width, height);
        } else {
            lp.width  = width;
            lp.height = height;
        }
        setLayoutParams(lp);
        setTranslationX(x);
        setTranslationY(y);
        bringToFront();
        invalidate();
    }

    /** Ratio currently drawn (0..1, 1 = all white); animated towards the latest score. */
    private float shownRatio = 0.5f;
    private boolean hasValue = false;
    private android.animation.ValueAnimator animator;

    /** Evaluation (pawns, white POV) -> white share of the bar. Mates fill the bar. */
    static float ratioFor(float score) {
        if (score >= ReviewMath.MATE_THRESHOLD) return 1f;
        if (score <= -ReviewMath.MATE_THRESHOLD) return 0f;
        // Win-probability scale (same model as the review): +1 is clearly visible, +5 is
        // nearly full, instead of a linear +/-10 scale where most real evals looked equal.
        return Math.max(0.03f, Math.min(0.97f, ReviewMath.whiteWin(score)));
    }

    private void animateTo(float target, boolean immediate) {
        if (animator != null) animator.cancel();
        if (immediate || getVisibility() != VISIBLE) {
            shownRatio = target;
            return;
        }
        animator = android.animation.ValueAnimator.ofFloat(shownRatio, target);
        animator.setDuration(220);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shownRatio = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float whiteRatio = shownRatio;

        float divY;
        if (flipped) {
            // Board is flipped: black at top → white territory grows downward
            divY = h * whiteRatio;
            rectWhite.set(0, 0,    w, divY);
            rectBlack.set(0, divY, w, h);
        } else {
            // Normal: white at bottom → white territory at bottom
            divY = h * (1.0f - whiteRatio);
            rectBlack.set(0, 0,    w, divY);
            rectWhite.set(0, divY, w, h);
        }

        canvas.drawRect(rectBlack, paintBlack);
        canvas.drawRect(rectWhite, paintWhite);
        canvas.drawLine(0, divY, w, divY, paintLine);
        // Tick at 0.00 (middle of the bar)
        float mid = h / 2.0f;
        canvas.drawLine(0, mid, w * 0.25f, mid, paintLine);
        canvas.drawLine(w * 0.75f, mid, w, mid, paintLine);

        // Score label
        String label = hasMate ? (mateIn == 0 ? "#" : "M" + Math.abs(mateIn))
                                : formatScore(score);

        boolean whiteAhead = score >= 0;
        float textY;
        if (whiteAhead) {
            paintText.setColor(Color.BLACK);
            textY = flipped ? divY / 2.0f : divY + (h - divY) / 2.0f;
        } else {
            paintText.setColor(Color.WHITE);
            textY = flipped ? divY + (h - divY) / 2.0f : divY / 2.0f;
        }

        canvas.save();
        canvas.rotate(-90, w / 2.0f, textY);
        canvas.drawText(label, w / 2.0f, textY + paintText.getTextSize() / 3.0f, paintText);
        canvas.restore();
    }

    /** "0.4", "3.2", "12" (locale-independent: never "0,4"). */
    static String formatScore(float score) {
        float a = Math.abs(score);
        return a >= 10f ? String.valueOf(Math.round(a)) : String.format(java.util.Locale.US, "%.1f", a);
    }
}
