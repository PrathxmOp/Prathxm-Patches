/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

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

        // 1. Draw connecting path (Rustic Rope Bridge with Wooden Planks)
        for (int i = 0; i < LEVELS_PER_PAGE - 1; i++) {
            float x1 = getXForIndex(i, w);
            float y1 = getYForIndex(i, h, spacing);
            float x2 = getXForIndex(i + 1, w);
            float y2 = getYForIndex(i + 1, h, spacing);

            float dx = x2 - x1;
            float dy = y2 - y1;
            float len = (float) Math.hypot(dx, dy);
            if (len == 0) continue;

            float ux = dx / len;
            float uy = dy / len;
            float perpX = -uy;
            float perpY = ux;

            int globalLevel = startLevelIdx + i + 1;
            boolean isSegmentUnlocked = globalLevel < unlockedLevel;

            // Draw Ropes
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.5f, getResources().getDisplayMetrics()));
            
            // Unlocked ropes are rich vines/green rope, locked are weathered brown
            paint.setColor(isSegmentUnlocked ? Color.parseColor("#4E7833") : Color.parseColor("#4E342E"));

            float ropeOffset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
            canvas.drawLine(x1 + perpX * ropeOffset, y1 + perpY * ropeOffset, x2 + perpX * ropeOffset, y2 + perpY * ropeOffset, paint);
            canvas.drawLine(x1 - perpX * ropeOffset, y1 - perpY * ropeOffset, x2 - perpX * ropeOffset, y2 - perpY * ropeOffset, paint);

            // Draw Wooden Planks along the bridge
            paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics()));
            paint.setStrokeCap(Paint.Cap.ROUND);
            
            float plankSpacing = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18, getResources().getDisplayMetrics());
            float plankWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());

            for (float dist = plankSpacing / 2.0f; dist < len; dist += plankSpacing) {
                float px = x1 + ux * dist;
                float py = y1 + uy * dist;
                
                // Unlocked planks are golden wood, locked are dark/weathered wood
                paint.setColor(isSegmentUnlocked ? Color.parseColor("#A17A4A") : Color.parseColor("#4A3728"));
                canvas.drawLine(px - perpX * plankWidth, py - perpY * plankWidth, px + perpX * plankWidth, py + perpY * plankWidth, paint);

                // Add dark wood grain lines on planks for realism
                paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, getResources().getDisplayMetrics()));
                paint.setColor(isSegmentUnlocked ? Color.parseColor("#5A452B") : Color.parseColor("#2A1F16"));
                canvas.drawLine(px - perpX * (plankWidth * 0.7f), py - perpY * (plankWidth * 0.7f), px + perpX * (plankWidth * 0.7f), py + perpY * (plankWidth * 0.7f), paint);
                paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics()));
            }
        }

        // 2. Draw Level Gem Nodes (Tree Trunk slices)
        for (int i = 0; i < LEVELS_PER_PAGE; i++) {
            int globalLevel = startLevelIdx + i + 1;
            float px = getXForIndex(i, w);
            float py = getYForIndex(i, h, spacing);

            boolean isCompleted = globalLevel < unlockedLevel;
            boolean isActive = globalLevel == unlockedLevel;
            boolean isLocked = globalLevel > unlockedLevel;

            // Draw grass/foliage backdrop for active/completed levels
            if (!isLocked) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#5C7A41")); // Soft Moss Green
                canvas.drawCircle(px, py, gemSize * 1.25f, paint);

                // Tiny grass blade details
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.5f, getResources().getDisplayMetrics()));
                paint.setColor(Color.parseColor("#82B64C"));
                canvas.drawLine(px - 15, py - 40, px - 10, py - 55, paint);
                canvas.drawLine(px - 15, py - 40, px - 25, py - 50, paint);
                canvas.drawLine(px + 20, py + 35, px + 25, py + 50, paint);
                canvas.drawLine(px + 20, py + 35, px + 12, py + 48, paint);
            }

            // Draw Outer Bark (Dark wood texture)
            paint.setStyle(Paint.Style.FILL);
            if (isLocked) {
                paint.setColor(Color.parseColor("#3D291F"));
            } else if (isActive) {
                paint.setColor(Color.parseColor("#4E342E"));
            } else {
                paint.setColor(Color.parseColor("#5D4037"));
            }
            canvas.drawCircle(px, py, gemSize, paint);

            // Draw Inner Wood (Concentric rings surface)
            paint.setStyle(Paint.Style.FILL);
            if (isLocked) {
                paint.setColor(Color.parseColor("#5D473C"));
            } else if (isActive) {
                paint.setColor(Color.parseColor("#CDA275"));
            } else {
                paint.setColor(Color.parseColor("#B18E66"));
            }
            float innerRadius = gemSize - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
            canvas.drawCircle(px, py, innerRadius, paint);

            // Draw tree age rings inside wood slice
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.2f, getResources().getDisplayMetrics()));
            paint.setColor(isLocked ? Color.parseColor("#4A372D") : Color.parseColor("#7D5E45"));
            canvas.drawCircle(px, py, innerRadius * 0.45f, paint);
            canvas.drawCircle(px, py, innerRadius * 0.75f, paint);

            // Text / Symbol
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            if (finalFontBold != null) {
                paint.setTypeface(finalFontBold);
            } else {
                paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
            }

            if (isCompleted) {
                // Completed: green checkmark
                paint.setColor(Color.parseColor("#388E3C"));
                paint.setTextSize(gemSize * 1.0f);
                canvas.drawText("✓", px, py + (gemSize * 0.35f), paint);
            } else {
                // Number
                paint.setColor(isLocked ? Color.parseColor("#8D6E63") : Color.parseColor("#3E2723"));
                paint.setTextSize(gemSize * 0.75f);
                canvas.drawText(String.valueOf(globalLevel), px, py + (gemSize * 0.25f), paint);
            }

            // Draw Active Pawn / Avatar Indicator
            if (isActive) {
                // Glow halo
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3.5f, getResources().getDisplayMetrics()));
                paint.setColor(Color.WHITE);
                canvas.drawCircle(px, py, gemSize + 1, paint);

                // Fetch pawn drawable from resources
                int pawnResId = getContext().getResources().getIdentifier("wp", "drawable", getContext().getPackageName());
                if (pawnResId != 0) {
                    Drawable pawn = getContext().getResources().getDrawable(pawnResId, null);
                    if (pawn != null) {
                        pawn = pawn.mutate();
                        int pSize = (int) (gemSize * 1.5f);
                        int left = (int) px - pSize / 2;
                        int top = (int) py - pSize;
                        int right = (int) px + pSize / 2;
                        int bottom = (int) py;

                        if (pawn instanceof android.graphics.drawable.BitmapDrawable) {
                            android.graphics.Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) pawn).getBitmap();
                            if (bitmap != null && !bitmap.isRecycled()) {
                                int bmpW = bitmap.getWidth();
                                int bmpH = bitmap.getHeight();
                                // Crop top 2/3 containing the high-res piece
                                int intrinsicHeight = (bmpH * 2) / 3;
                                android.graphics.Rect srcRect = new android.graphics.Rect(0, 0, bmpW, intrinsicHeight);
                                android.graphics.Rect dstRect = new android.graphics.Rect(left, top, right, bottom);
                                
                                Paint piecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                piecePaint.setAlpha(255);
                                piecePaint.setColorFilter(null);
                                canvas.drawBitmap(bitmap, srcRect, dstRect, piecePaint);
                            } else {
                                pawn.setBounds(left, top, right, bottom);
                                pawn.draw(canvas);
                            }
                        } else {
                            pawn.setBounds(left, top, right, bottom);
                            pawn.draw(canvas);
                        }
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
