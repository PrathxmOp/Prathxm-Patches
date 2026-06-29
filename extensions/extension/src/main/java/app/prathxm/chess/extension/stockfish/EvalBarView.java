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
        this.score   = score;
        this.hasMate = hasMate;
        this.mateIn  = mateIn;
        this.flipped = flipped;

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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Clamp score ±10 pawns → 0..1 ratio (1 = full white)
        float clamped    = Math.max(-10.0f, Math.min(10.0f, score));
        float whiteRatio = (clamped + 10.0f) / 20.0f;

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

        // Score label
        String label = hasMate ? "M" + Math.abs(mateIn)
                                : String.format("%.1f", Math.abs(score));

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
}
