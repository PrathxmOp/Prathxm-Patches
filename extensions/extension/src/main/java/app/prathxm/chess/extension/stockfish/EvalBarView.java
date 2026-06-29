package app.prathxm.chess.extension.stockfish;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

public class EvalBarView extends View {
    private float score = 0.0f; // from White's perspective
    private boolean hasMate = false;
    private int mateIn = 0;
    private boolean flipped = false;

    private int wdlWin = 0;
    private int wdlDraw = 0;
    private int wdlLoss = 0;
    private boolean showWdl = false;

    private final Paint paintWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint paintWdlWin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintWdlDraw = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintWdlLoss = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    private final RectF rectWhite = new RectF();
    private final RectF rectBlack = new RectF();

    public EvalBarView(Context context) {
        super(context);
        paintWhite.setColor(0xE6E0E0E0); // Sleek off-white/light gray with premium opacity
        paintBlack.setColor(0xE6262522); // Premium dark charcoal with premium opacity
        
        paintLine.setColor(0xFF5f5e5b);
        paintLine.setStrokeWidth(2.0f);
        
        paintText.setTextSize(10 * getResources().getDisplayMetrics().density);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setFakeBoldText(true);

        paintWdlWin.setColor(0xE681B64C);  // Chess.com Green
        paintWdlDraw.setColor(0xE68B9A9A); // Chess.com Gray
        paintWdlLoss.setColor(0xE6E15554); // Chess.com Red
    }

    public void update(int x, int y, int width, int height, float score, boolean hasMate, int mateIn, boolean flipped,
                       int wdlWin, int wdlDraw, int wdlLoss, boolean showWdl) {
        this.score = score;
        this.hasMate = hasMate;
        this.mateIn = mateIn;
        this.flipped = flipped;
        this.wdlWin = wdlWin;
        this.wdlDraw = wdlDraw;
        this.wdlLoss = wdlLoss;
        this.showWdl = showWdl;

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) {
            lp = new ViewGroup.LayoutParams(width, height);
        } else {
            lp.width = width;
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

        float density = getResources().getDisplayMetrics().density;
        float evalW = w;
        if (showWdl && (wdlWin + wdlDraw + wdlLoss) > 0) {
            evalW = 10.0f * density;
        }

        // Clamp the score between -10.0 and +10.0 pawns
        float clampedScore = Math.max(-10.0f, Math.min(10.0f, score));

        // Normalize ratio: 0.0 means black advantage (-10.0), 1.0 means white advantage (+10.0)
        float whiteRatio = (clampedScore + 10.0f) / 20.0f;

        float divisionY;
        if (flipped) {
            // White is at the top when flipped
            divisionY = h * whiteRatio;
            rectWhite.set(0, 0, evalW, divisionY);
            rectBlack.set(0, divisionY, evalW, h);
        } else {
            // White is at the bottom when normal
            divisionY = h * (1.0f - whiteRatio);
            rectBlack.set(0, 0, evalW, divisionY);
            rectWhite.set(0, divisionY, evalW, h);
        }

        // Draw the background bars
        canvas.drawRect(rectWhite, paintWhite);
        canvas.drawRect(rectBlack, paintBlack);

        // Draw the divider line
        canvas.drawLine(0, divisionY, evalW, divisionY, paintLine);

        // Format evaluation text
        String text;
        if (hasMate) {
            text = "M" + Math.abs(mateIn);
        } else {
            text = String.format("%.1f", Math.abs(score));
        }

        // Determine where to draw the text (on the winning territory)
        boolean whiteWinning = score >= 0;
        float textY;
        
        if (whiteWinning) {
            paintText.setColor(Color.BLACK); // High contrast on white
            if (flipped) {
                textY = divisionY / 2.0f;
            } else {
                textY = divisionY + (h - divisionY) / 2.0f;
            }
        } else {
            paintText.setColor(Color.WHITE); // High contrast on black
            if (flipped) {
                textY = divisionY + (h - divisionY) / 2.0f;
            } else {
                textY = divisionY / 2.0f;
            }
        }

        // Draw rotated vertical text to fit beautifully inside the width
        canvas.save();
        canvas.rotate(-90, evalW / 2.0f, textY);
        canvas.drawText(text, evalW / 2.0f, textY + (paintText.getTextSize() / 3.0f), paintText);
        canvas.restore();

        // Draw WDL bar if enabled
        if (showWdl) {
            int total = wdlWin + wdlDraw + wdlLoss;
            if (total > 0) {
                float gap = 2.0f * density;
                float wdlW = 4.0f * density;
                float wdlX = evalW + gap;
                
                float winHeight, drawHeight, lossHeight;
                if (flipped) {
                    // Black is at bottom: bottom segment is Black Win = White Loss
                    winHeight  = h * (wdlLoss / (float) total);
                    drawHeight = h * (wdlDraw / (float) total);
                    lossHeight = h * (wdlWin  / (float) total);
                } else {
                    // White is at bottom: bottom segment is White Win
                    winHeight  = h * (wdlWin  / (float) total);
                    drawHeight = h * (wdlDraw / (float) total);
                    lossHeight = h * (wdlLoss / (float) total);
                }
                
                // Draw bottom segment (Win - Green)
                canvas.drawRect(wdlX, h - winHeight, wdlX + wdlW, h, paintWdlWin);
                
                // Draw middle segment (Draw - Gray)
                canvas.drawRect(wdlX, h - winHeight - drawHeight, wdlX + wdlW, h - winHeight, paintWdlDraw);
                
                // Draw top segment (Loss - Red)
                canvas.drawRect(wdlX, 0, wdlX + wdlW, h - winHeight - drawHeight, paintWdlLoss);
            }
        }
    }
}
