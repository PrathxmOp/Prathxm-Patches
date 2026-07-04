/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.stockfish;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;

/**
 * A horizontal Win/Draw/Loss probability bar rendered below the chess board.
 *
 * Layout (left → right):
 *   [Win% green] [Draw% gray] [Loss% red]
 *
 * Percentages are shown as text labels inside each segment when wide enough.
 */
public class WdlBarView extends View {

    private int wdlWin  = 0;
    private int wdlDraw = 0;
    private int wdlLoss = 0;

    // Chess.com palette
    private final Paint paintWin  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintDraw = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLoss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rectWin  = new RectF();
    private final RectF rectDraw = new RectF();
    private final RectF rectLoss = new RectF();

    public WdlBarView(Context context) {
        super(context);
        paintWin.setColor(0xFF81B64C);  // Chess.com green
        paintDraw.setColor(0xFF7D8796); // Chess.com muted gray
        paintLoss.setColor(0xFFE15554); // Chess.com red

        float density = context.getResources().getDisplayMetrics().density;
        paintText.setTextSize(8.5f * density);
        paintText.setTextAlign(Paint.Align.CENTER);
        paintText.setFakeBoldText(true);
        paintText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paintText.setColor(0xFFFFFFFF);
    }

    /**
     * Update WDL data and position the bar below the chess board.
     */
    public void update(int x, int y, int width, int height,
                       int wdlWin, int wdlDraw, int wdlLoss) {
        this.wdlWin  = wdlWin;
        this.wdlDraw = wdlDraw;
        this.wdlLoss = wdlLoss;

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

        int total = wdlWin + wdlDraw + wdlLoss;
        if (total <= 0) return;

        float winW  = w * (wdlWin  / (float) total);
        float drawW = w * (wdlDraw / (float) total);
        float lossW = w - winW - drawW;

        float radius = h / 3.0f;

        // Win segment (left, rounded left corners)
        rectWin.set(0, 0, winW, h);
        canvas.drawRoundRect(rectWin, radius, radius, paintWin);
        // square off right edge of win segment
        if (winW > radius) {
            canvas.drawRect(winW - radius, 0, winW, h, paintWin);
        }

        // Draw segment (middle)
        rectDraw.set(winW, 0, winW + drawW, h);
        canvas.drawRect(rectDraw, paintDraw);

        // Loss segment (right, rounded right corners)
        float lossX = winW + drawW;
        rectLoss.set(lossX, 0, w, h);
        canvas.drawRoundRect(rectLoss, radius, radius, paintLoss);
        // square off left edge of loss segment
        if (lossW > radius) {
            canvas.drawRect(lossX, 0, lossX + radius, h, paintLoss);
        }

        // Convert permille WDL values (sum=1000) to percentages (sum=100)
        int pctWin = Math.round(wdlWin / 10.0f);
        int pctDraw = Math.round(wdlDraw / 10.0f);
        int pctLoss = Math.round(wdlLoss / 10.0f);
        int sum = pctWin + pctDraw + pctLoss;
        if (sum != 100 && sum > 0) {
            int diff = 100 - sum;
            if (pctWin >= pctDraw && pctWin >= pctLoss) {
                pctWin += diff;
            } else if (pctDraw >= pctWin && pctDraw >= pctLoss) {
                pctDraw += diff;
            } else {
                pctLoss += diff;
            }
        }

        // Labels — only draw if segment is wide enough
        float textY = h / 2.0f + paintText.getTextSize() / 3.0f;
        float minSegW = 28 * getContext().getResources().getDisplayMetrics().density;

        if (winW >= minSegW) {
            canvas.drawText(pctWin + "%", winW / 2.0f, textY, paintText);
        }
        if (drawW >= minSegW) {
            canvas.drawText(pctDraw + "%", winW + drawW / 2.0f, textY, paintText);
        }
        if (lossW >= minSegW) {
            canvas.drawText(pctLoss + "%", lossX + lossW / 2.0f, textY, paintText);
        }
    }
}
