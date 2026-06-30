package app.prathxm.chess.extension.lichesspuzzle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.Locale;

public class PuzzleJourneyMapView extends View {

    public interface OnLevelClickListener {
        void onLevelClick(int levelIndex);
        void onPageChange(int pageOffset);
    }

    private OnLevelClickListener listener;

    private int unlockedLevel = 1;
    private int currentPage = 0; // Each page displays 20 levels
    private static final int LEVELS_PER_PAGE = 20;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    // Theme Colors (Matches Chess.com Premium Map style)
    private final int COLOR_PATH_LOCKED = Color.parseColor("#2B2A26");
    private final int COLOR_PATH_UNLOCKED = Color.parseColor("#81B64C");
    private final int COLOR_GEM_LOCKED = Color.parseColor("#363531");
    private final int COLOR_GEM_UNLOCKED = Color.parseColor("#457530");
    private final int COLOR_GEM_COMPLETED = Color.parseColor("#81B64C");
    private final int COLOR_TEXT_LIGHT = Color.parseColor("#FFFFFF");
    private final int COLOR_TEXT_DARK = Color.parseColor("#121214");

    public PuzzleJourneyMapView(Context context) {
        super(context);
        init();
    }

    public PuzzleJourneyMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
    }

    public void setOnLevelClickListener(OnLevelClickListener listener) {
        this.listener = listener;
    }

    public void setProgress(int unlockedLevel) {
        this.unlockedLevel = unlockedLevel;
        // Auto-set page to the unlocked level page
        this.currentPage = (unlockedLevel - 1) / LEVELS_PER_PAGE;
        invalidate();
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        this.currentPage = page;
        invalidate();
    }

    private float getXForIndex(int idx, float width) {
        // Serpentine winding pattern using sine wave
        float angle = idx * 0.9f;
        float amp = width * 0.22f; // Keep it within screen margins
        return width / 2.0f + (float) Math.sin(angle) * amp;
    }

    private float getYForIndex(int idx, float height, float spacing) {
        // Bottom-up: index 0 (bottom of list) to index 19 (top of list)
        float padding = spacing * 1.5f;
        return height - padding - (idx * spacing);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float spacing = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90, getResources().getDisplayMetrics());
        // Calculate height based on 20 levels + top/bottom page switch banners
        int height = (int) (spacing * (LEVELS_PER_PAGE + 2.5f));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        float spacing = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90, getResources().getDisplayMetrics());
        float gemSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, getResources().getDisplayMetrics());

        android.graphics.Typeface fontBold = null;
        try {
            fontBold = android.graphics.Typeface.createFromAsset(getContext().getAssets(), "composeResources/com.chess.designsystem.fonts.generated.resources/font/chess_sans_bold.ttf");
        } catch (Exception ignored) {}
        final android.graphics.Typeface finalFontBold = fontBold;

        int startLevelIdx = currentPage * LEVELS_PER_PAGE;

        // 1. Draw connecting path line (Base Locked Path)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()));
        paint.setColor(COLOR_PATH_LOCKED);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        path.reset();
        boolean pathStarted = false;
        for (int i = 0; i < LEVELS_PER_PAGE; i++) {
            float px = getXForIndex(i, w);
            float py = getYForIndex(i, h, spacing);
            if (!pathStarted) {
                path.moveTo(px, py);
                pathStarted = true;
            } else {
                path.lineTo(px, py);
            }
        }
        canvas.drawPath(path, paint);

        // 2. Draw connecting path line (Unlocked Green Overlay)
        path.reset();
        pathStarted = false;
        for (int i = 0; i < LEVELS_PER_PAGE; i++) {
            int globalLevel = startLevelIdx + i + 1;
            if (globalLevel <= unlockedLevel) {
                float px = getXForIndex(i, w);
                float py = getYForIndex(i, h, spacing);
                if (!pathStarted) {
                    path.moveTo(px, py);
                    pathStarted = true;
                } else {
                    path.lineTo(px, py);
                }
            }
        }
        if (pathStarted) {
            paint.setColor(COLOR_PATH_UNLOCKED);
            canvas.drawPath(path, paint);
        }

        // 3. Draw Level Gem Nodes
        for (int i = 0; i < LEVELS_PER_PAGE; i++) {
            int globalLevel = startLevelIdx + i + 1;
            float px = getXForIndex(i, w);
            float py = getYForIndex(i, h, spacing);

            // Gem Style
            paint.setStyle(Paint.Style.FILL);
            boolean isCompleted = globalLevel < unlockedLevel;
            boolean isActive = globalLevel == unlockedLevel;
            boolean isLocked = globalLevel > unlockedLevel;

            if (isCompleted) {
                paint.setColor(COLOR_GEM_COMPLETED);
            } else if (isActive) {
                paint.setColor(COLOR_GEM_UNLOCKED);
            } else {
                paint.setColor(COLOR_GEM_LOCKED);
            }

            // Draw Diamond shape
            Path gemPath = new Path();
            gemPath.moveTo(px, py - gemSize);
            gemPath.lineTo(px + gemSize, py);
            gemPath.lineTo(px, py + gemSize);
            gemPath.lineTo(px - gemSize, py);
            gemPath.close();
            canvas.drawPath(gemPath, paint);

            // Draw Gem Border Highlight
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics()));
            if (isActive) {
                paint.setColor(Color.WHITE);
            } else {
                paint.setColor(Color.argb(60, 255, 255, 255));
            }
            canvas.drawPath(gemPath, paint);

            // Draw indicator/text inside gem
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            if (finalFontBold != null) {
                paint.setTypeface(finalFontBold);
            } else {
                paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
            }

            if (isCompleted) {
                // Checkmark
                paint.setColor(COLOR_TEXT_LIGHT);
                paint.setTextSize(gemSize * 0.9f);
                canvas.drawText("✓", px, py + (gemSize * 0.3f), paint);
            } else {
                // Level Number
                paint.setColor(COLOR_TEXT_LIGHT);
                paint.setTextSize(gemSize * 0.7f);
                canvas.drawText(String.valueOf(globalLevel), px, py + (gemSize * 0.25f), paint);
            }

            // 4. Draw Active Pawn / Avatar Indicator
            if (isActive) {
                // Glow halo
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(50, 129, 182, 76));
                canvas.drawCircle(px, py, gemSize * 1.6f, paint);

                // Fetch pawn drawable from resources
                int pawnResId = getContext().getResources().getIdentifier("wp", "drawable", getContext().getPackageName());
                if (pawnResId != 0) {
                    Drawable pawn = getContext().getResources().getDrawable(pawnResId, null);
                    if (pawn != null) {
                        int pSize = (int) (gemSize * 1.4f);
                        pawn.setBounds((int) px - pSize / 2, (int) py - pSize, (int) px + pSize / 2, (int) py);
                        
                        int sc = canvas.save();
                        pawn.draw(canvas);
                        canvas.restoreToCount(sc);
                    }
                }
            }
        }

        // 5. Draw Top & Bottom Navigation Banners
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_GEM_LOCKED);
        paint.setTextAlign(Paint.Align.CENTER);
        if (finalFontBold != null) {
            paint.setTypeface(finalFontBold);
        } else {
            paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
        }
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, getResources().getDisplayMetrics()));

        // Top Banner (Next Page)
        float topY = getYForIndex(LEVELS_PER_PAGE - 1, h, spacing) - (spacing * 1.0f);
        paint.setColor(Color.parseColor("#1E1E24"));
        canvas.drawRect(w * 0.15f, topY - spacing * 0.35f, w * 0.85f, topY + spacing * 0.35f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawText("▲ Next 20 Levels", w / 2.0f, topY + spacing * 0.1f, paint);

        // Bottom Banner (Previous Page)
        float bottomY = getYForIndex(0, h, spacing) + (spacing * 1.0f);
        if (currentPage > 0) {
            paint.setColor(Color.parseColor("#1E1E24"));
            canvas.drawRect(w * 0.15f, bottomY - spacing * 0.35f, w * 0.85f, bottomY + spacing * 0.35f, paint);
            paint.setColor(Color.WHITE);
            canvas.drawText("▼ Previous 20 Levels", w / 2.0f, bottomY + spacing * 0.1f, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            float tx = event.getX();
            float ty = event.getY();

            float w = getWidth();
            float h = getHeight();
            float spacing = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90, getResources().getDisplayMetrics());
            float gemSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, getResources().getDisplayMetrics());

            // Check Navigation Taps
            float topY = getYForIndex(LEVELS_PER_PAGE - 1, h, spacing) - (spacing * 1.0f);
            if (tx >= w * 0.15f && tx <= w * 0.85f && ty >= topY - spacing * 0.35f && ty <= topY + spacing * 0.35f) {
                if (listener != null) {
                    listener.onPageChange(1);
                }
                return true;
            }

            float bottomY = getYForIndex(0, h, spacing) + (spacing * 1.0f);
            if (currentPage > 0 && tx >= w * 0.15f && tx <= w * 0.85f && ty >= bottomY - spacing * 0.35f && ty <= bottomY + spacing * 0.35f) {
                if (listener != null) {
                    listener.onPageChange(-1);
                }
                return true;
            }

            // Check Level Gem Node Taps
            int startLevelIdx = currentPage * LEVELS_PER_PAGE;
            for (int i = 0; i < LEVELS_PER_PAGE; i++) {
                float px = getXForIndex(i, w);
                float py = getYForIndex(i, h, spacing);
                float dist = (float) Math.hypot(tx - px, ty - py);

                if (dist < gemSize * 1.5f) {
                    int globalLevel = startLevelIdx + i + 1;
                    if (globalLevel <= unlockedLevel) {
                        if (listener != null) {
                            listener.onLevelClick(globalLevel);
                        }
                    } else {
                        Toast.makeText(getContext(), String.format(Locale.US, "Level %d is locked.", globalLevel), Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
            }
            return true;
        }

        return super.onTouchEvent(event);
    }
}
