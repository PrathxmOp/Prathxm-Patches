package app.prathxm.chess.extension.lichesspuzzle;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

public class LichessBoardView extends View {
    private int COLOR_LIGHT = Color.parseColor("#EEEED2");
    private int COLOR_DARK = Color.parseColor("#769656");
    private final int COLOR_SELECTED = Color.argb(130, 247, 247, 105);
    private final int COLOR_LAST_MOVE = Color.argb(120, 186, 202, 68); // #baca44 with opacity
    private final int COLOR_HINT = Color.argb(150, 243, 156, 18); // soft orange for hints
    private final int COLOR_ARROW = Color.argb(180, 243, 156, 18); // orange for arrows

    public void setBoardThemeColors(int lightColor, int darkColor) {
        this.COLOR_LIGHT = lightColor;
        this.COLOR_DARK = darkColor;
        invalidate();
    }
    
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint piecePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean isFlipped = false;
    
    // Board state: 8x8 matrix of pieces (e.g. 'P', 'p', 'N', etc. or ' ')
    private final char[][] board = new char[8][8];
    private int selectedFile = -1;
    private int selectedRank = -1;

    // Last move tracking for visual highlights
    private int lastFromRank = -1;
    private int lastFromFile = -1;
    private int lastToRank = -1;
    private int lastToFile = -1;

    // Hint square tracking
    private int hintFile = -1;
    private int hintRank = -1;

    // Arrow drawing tracking
    private int arrowFromFile = -1;
    private int arrowFromRank = -1;
    private int arrowToFile = -1;
    private int arrowToRank = -1;
    
    private boolean isInteractable = false;
    private char playerColor = 'w'; // 'w' or 'b'
    
    public void setInteractable(boolean interactable) {
        this.isInteractable = interactable;
    }
    
    public void setPlayerColor(char color) {
        this.playerColor = color;
    }
    
    public interface BoardListener {
        void onMove(String fromSquare, String toSquare);
    }
    
    private BoardListener listener;

    // Piece movement animation variables
    private boolean isAnimating = false;
    private char animatingPiece = ' ';
    private float animProgress = 0.0f; // 0.0f to 1.0f
    private int animFromRank = -1;
    private int animFromFile = -1;
    private int animToRank = -1;
    private int animToFile = -1;
    private long animStartTime = 0;
    private static final long ANIM_DURATION_MS = 250;

    public LichessBoardView(Context context) {
        super(context);
        paint.setFilterBitmap(true);
        piecePaint.setFilterBitmap(true);
    }

    public void setBoardListener(BoardListener listener) {
        this.listener = listener;
    }

    public void setFlipped(boolean flipped) {
        this.isFlipped = flipped;
        invalidate();
    }

    public void showHint(String square) {
        if (square == null || square.length() < 2) return;
        this.hintFile = square.charAt(0) - 'a';
        this.hintRank = '8' - square.charAt(1);
        invalidate();
    }

    public void showArrow(String from, String to) {
        if (from == null || from.length() < 2 || to == null || to.length() < 2) return;
        this.arrowFromFile = from.charAt(0) - 'a';
        this.arrowFromRank = '8' - from.charAt(1);
        this.arrowToFile = to.charAt(0) - 'a';
        this.arrowToRank = '8' - to.charAt(1);
        invalidate();
    }

    public void clearArrow() {
        this.arrowFromFile = -1;
        this.arrowFromRank = -1;
        this.arrowToFile = -1;
        this.arrowToRank = -1;
        invalidate();
    }

    public char getPieceAt(String square) {
        if (square == null || square.length() < 2) return ' ';
        int f = square.charAt(0) - 'a';
        int r = '8' - square.charAt(1);
        if (f >= 0 && f < 8 && r >= 0 && r < 8) {
            return board[r][f];
        }
        return ' ';
    }

    public void setFEN(String fen) {
        isAnimating = false;
        animatingPiece = ' ';

        // Clear board
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                board[r][f] = ' ';
            }
        }
        
        // Reset move/hint/arrow highlights
        lastFromFile = -1;
        lastFromRank = -1;
        lastToFile = -1;
        lastToRank = -1;
        hintFile = -1;
        hintRank = -1;
        clearArrow();
        
        if (fen == null || fen.isEmpty()) return;
        
        try {
            String[] parts = fen.split(" ");
            String position = parts[0];
            String[] ranks = position.split("/");
            for (int r = 0; r < 8; r++) {
                String rankStr = ranks[r];
                int fileIdx = 0;
                for (int i = 0; i < rankStr.length(); i++) {
                    char c = rankStr.charAt(i);
                    if (Character.isDigit(c)) {
                        fileIdx += Character.getNumericValue(c);
                    } else {
                        board[r][fileIdx] = c;
                        fileIdx++;
                    }
                }
            }
            selectedFile = -1;
            selectedRank = -1;
            invalidate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Direct move application for local UI updates
    public void makeMove(String from, String to) {
        makeMove(from, to, true);
    }

    public void makeMove(String from, String to, boolean animate) {
        try {
            int fromFile = from.charAt(0) - 'a';
            int fromRank = '8' - from.charAt(1);
            int toFile = to.charAt(0) - 'a';
            int toRank = '8' - to.charAt(1);
            
            char piece = board[fromRank][fromFile];
            
            if (piece != ' ' && animate) {
                isAnimating = true;
                animatingPiece = piece;
                animFromFile = fromFile;
                animFromRank = fromRank;
                animToFile = toFile;
                animToRank = toRank;
                animStartTime = System.currentTimeMillis();
                animProgress = 0.0f;
            }
            
            // Check castling: King moves 2 squares horizontally
            if ((piece == 'K' || piece == 'k') && Math.abs(fromFile - toFile) == 2) {
                // White short castle
                if (piece == 'K' && toFile == 6) {
                    board[7][7] = ' ';
                    board[7][5] = 'R';
                }
                // White long castle
                else if (piece == 'K' && toFile == 2) {
                    board[7][0] = ' ';
                    board[7][3] = 'R';
                }
                // Black short castle
                else if (piece == 'k' && toFile == 6) {
                    board[0][7] = ' ';
                    board[0][5] = 'r';
                }
                // Black long castle
                else if (piece == 'k' && toFile == 2) {
                    board[0][0] = ' ';
                    board[0][3] = 'r';
                }
            }
            
            // Check en passant: Pawn moves diagonally to an empty square
            if ((piece == 'P' || piece == 'p') && fromFile != toFile && board[toRank][toFile] == ' ') {
                // Clear the captured pawn
                board[fromRank][toFile] = ' ';
            }
            
            board[fromRank][fromFile] = ' ';
            board[toRank][toFile] = piece;

            // Check promotion: if destination has a 3rd character or if pawn reaches back rank
            if (to.length() > 2) {
                char promo = Character.toLowerCase(to.charAt(2));
                boolean isWhite = Character.isUpperCase(piece);
                if (promo == 'q') piece = isWhite ? 'Q' : 'q';
                else if (promo == 'r') piece = isWhite ? 'R' : 'r';
                else if (promo == 'b') piece = isWhite ? 'B' : 'b';
                else if (promo == 'n') piece = isWhite ? 'N' : 'n';
                board[toRank][toFile] = piece;
            } else if ((piece == 'P' && toRank == 0) || (piece == 'p' && toRank == 7)) {
                // Auto promote to queen if no promo char is explicitly given
                board[toRank][toFile] = Character.isUpperCase(piece) ? 'Q' : 'q';
            }

            // Update last move highlights
            lastFromFile = fromFile;
            lastFromRank = fromRank;
            lastToFile = toFile;
            lastToRank = toRank;

            // Reset hint highlight/arrow on any move
            hintFile = -1;
            hintRank = -1;
            clearArrow();
            
            invalidate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, width);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float squareSize = getWidth() / 8.0f;
        
        java.util.List<int[]> legalDests = null;
        if (selectedFile != -1 && selectedRank != -1) {
            legalDests = getLegalDestinations(selectedFile, selectedRank);
        }

        // Draw squares, highlights, indicators, and pieces
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int drawRank = isFlipped ? 7 - r : r;
                int drawFile = isFlipped ? 7 - f : f;
                
                // Draw base square color
                paint.setStyle(Paint.Style.FILL);
                paint.setStrokeWidth(0);
                paint.setTextAlign(Paint.Align.LEFT);
                paint.setColor((r + f) % 2 == 0 ? COLOR_LIGHT : COLOR_DARK);
                canvas.drawRect(
                        drawFile * squareSize,
                        drawRank * squareSize,
                        (drawFile + 1) * squareSize,
                        (drawRank + 1) * squareSize,
                        paint
                );

                // Draw last move highlight
                if ((f == lastFromFile && r == lastFromRank) || (f == lastToFile && r == lastToRank)) {
                    paint.setColor(COLOR_LAST_MOVE);
                    canvas.drawRect(
                            drawFile * squareSize,
                            drawRank * squareSize,
                            (drawFile + 1) * squareSize,
                            (drawRank + 1) * squareSize,
                            paint
                    );
                }

                // Draw hint square highlight
                if (f == hintFile && r == hintRank) {
                    paint.setColor(COLOR_HINT);
                    canvas.drawRect(
                            drawFile * squareSize,
                            drawRank * squareSize,
                            (drawFile + 1) * squareSize,
                            (drawRank + 1) * squareSize,
                            paint
                    );
                }
                
                // Draw selection highlight
                if (f == selectedFile && r == selectedRank) {
                    paint.setColor(COLOR_SELECTED);
                    canvas.drawRect(
                            drawFile * squareSize,
                            drawRank * squareSize,
                            (drawFile + 1) * squareSize,
                            (drawRank + 1) * squareSize,
                            paint
                    );
                }

                // Draw legal move indicator if this square is a legal destination
                if (legalDests != null) {
                    boolean isLegal = false;
                    for (int[] d : legalDests) {
                        if (d[0] == f && d[1] == r) {
                            isLegal = true;
                            break;
                        }
                    }
                    if (isLegal) {
                        float centerX = drawFile * squareSize + squareSize / 2.0f;
                        float centerY = drawRank * squareSize + squareSize / 2.0f;
                        char target = board[r][f];
                        if (target == ' ') {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(Color.argb(45, 0, 0, 0));
                            canvas.drawCircle(centerX, centerY, squareSize * 0.14f, paint);
                        } else {
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setStrokeWidth(squareSize * 0.08f);
                            paint.setColor(Color.argb(45, 0, 0, 0));
                            canvas.drawCircle(centerX, centerY, squareSize * 0.43f, paint);
                        }
                    }
                }
                
                // Draw piece drawable from host application assets
                char piece = board[r][f];
                if (piece != ' ') {
                    if (isAnimating && r == animToRank && f == animToFile) {
                        // Skip drawing the piece statically while animating
                    } else {
                        drawPieceAt(canvas, piece, drawFile * squareSize, drawRank * squareSize, squareSize);
                    }
                }
            }
        }

        // Draw coordinate text over everything else
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int drawRank = isFlipped ? 7 - r : r;
                int drawFile = isFlipped ? 7 - f : f;

                paint.setTextSize(squareSize * 0.18f);
                paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));

                // Draw rank numbers (1-8) on left-most visible file
                if (drawFile == 0) {
                    int rankNum = isFlipped ? (r + 1) : (8 - r);
                    paint.setColor((r + f) % 2 == 0 ? COLOR_DARK : COLOR_LIGHT);
                    paint.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText(
                            String.valueOf(rankNum),
                            drawFile * squareSize + (squareSize * 0.06f),
                            drawRank * squareSize + (squareSize * 0.20f),
                            paint
                    );
                }
                
                // Draw file letters (a-h) on bottom-most visible rank
                if (drawRank == 7) {
                    char fileChar = isFlipped ? (char) ('h' - f) : (char) ('a' + f);
                    paint.setColor((r + f) % 2 == 0 ? COLOR_DARK : COLOR_LIGHT);
                    paint.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(
                            String.valueOf(fileChar),
                            (drawFile + 1) * squareSize - (squareSize * 0.06f),
                            (drawRank + 1) * squareSize - (squareSize * 0.06f),
                            paint
                    );
                }
            }
        }

        // Draw arrow if any
        if (arrowFromFile != -1) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLOR_ARROW);
            
            int drawFromRank = isFlipped ? 7 - arrowFromRank : arrowFromRank;
            int drawFromFile = isFlipped ? 7 - arrowFromFile : arrowFromFile;
            int drawToRank = isFlipped ? 7 - arrowToRank : arrowToRank;
            int drawToFile = isFlipped ? 7 - arrowToFile : arrowToFile;
            
            float startX = (drawFromFile + 0.5f) * squareSize;
            float startY = (drawFromRank + 0.5f) * squareSize;
            float endX = (drawToFile + 0.5f) * squareSize;
            float endY = (drawToRank + 0.5f) * squareSize;
            
            float dx = endX - startX;
            float dy = endY - startY;
            float length = (float) Math.hypot(dx, dy);
            
            if (length > 0) {
                float angle = (float) Math.atan2(dy, dx);
                
                // Pull arrowhead back slightly from the center of target square
                float pullBack = squareSize * 0.15f;
                float headEndX = endX - (float) Math.cos(angle) * pullBack;
                float headEndY = endY - (float) Math.sin(angle) * pullBack;
                
                float arrowLength = squareSize * 0.35f;
                float arrowAngle = (float) Math.toRadians(30);
                
                float x1 = headEndX - arrowLength * (float) Math.cos(angle - arrowAngle);
                float y1 = headEndY - arrowLength * (float) Math.sin(angle - arrowAngle);
                float x2 = headEndX - arrowLength * (float) Math.cos(angle + arrowAngle);
                float y2 = headEndY - arrowLength * (float) Math.sin(angle + arrowAngle);
                
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(headEndX, headEndY);
                path.lineTo(x1, y1);
                path.lineTo(x2, y2);
                path.close();
                
                // Draw shaft
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(squareSize * 0.12f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(startX, startY, headEndX - (float) Math.cos(angle) * (squareSize * 0.1f), headEndY - (float) Math.sin(angle) * (squareSize * 0.1f), paint);
                
                // Draw arrowhead
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(path, paint);
            }
        }

        // Draw animating piece if active
        if (isAnimating && animatingPiece != ' ') {
            long elapsed = System.currentTimeMillis() - animStartTime;
            if (elapsed >= ANIM_DURATION_MS) {
                isAnimating = false;
                animatingPiece = ' ';
                invalidate(); // Redraw final static layout
            } else {
                animProgress = (float) elapsed / ANIM_DURATION_MS;
                float t = animProgress;
                // Cubic ease-in-out easing
                float easeProgress = t < 0.5f ? 4.0f * t * t * t : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 3.0f) / 2.0f;
                
                int drawFromRank = isFlipped ? 7 - animFromRank : animFromRank;
                int drawFromFile = isFlipped ? 7 - animFromFile : animFromFile;
                int drawToRank = isFlipped ? 7 - animToRank : animToRank;
                int drawToFile = isFlipped ? 7 - animToFile : animToFile;
                
                float currentX = drawFromFile * squareSize + (drawToFile - drawFromFile) * squareSize * easeProgress;
                float currentY = drawFromRank * squareSize + (drawToRank - drawFromRank) * squareSize * easeProgress;
                
                drawPieceAt(canvas, animatingPiece, currentX, currentY, squareSize);
                
                postInvalidateOnAnimation();
            }
        }
    }

    private String getPieceDrawableName(char piece) {
        switch (piece) {
            case 'K': return "wk"; // White King
            case 'Q': return "wq"; // White Queen
            case 'R': return "wr"; // White Rook
            case 'B': return "wb"; // White Bishop
            case 'N': return "wn"; // White Knight
            case 'P': return "wp"; // White Pawn
            case 'k': return "bk"; // Black King
            case 'q': return "bq"; // Black Queen
            case 'r': return "br"; // Black Rook
            case 'b': return "bb"; // Black Bishop
            case 'n': return "bn"; // Black Knight
            case 'p': return "bp"; // Black Pawn
            default: return null;
        }
    }

    private void drawPieceAt(Canvas canvas, char piece, float x, float y, float squareSize) {
        String resName = getPieceDrawableName(piece);
        if (resName != null) {
            String hostPackage = getContext().getPackageName();
            int resId = getContext().getResources().getIdentifier(resName, "drawable", hostPackage);
            if (resId != 0) {
                Drawable drawable = getContext().getResources().getDrawable(resId, null);
                if (drawable != null) {
                    drawable = drawable.mutate();
                    drawable.setAlpha(255);
                    drawable.setColorFilter(null);

                    int left = (int) x;
                    int top = (int) y;
                    int right = (int) (x + squareSize);
                    int bottom = (int) (y + squareSize);
                    
                    int pad = (int) (squareSize * 0.05f);
                    
                    if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                        android.graphics.Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                        if (bitmap != null && !bitmap.isRecycled()) {
                            int bmpW = bitmap.getWidth();
                            int bmpH = bitmap.getHeight();
                            int intrinsicHeight = (bmpH * 2) / 3;
                            android.graphics.Rect srcRect = new android.graphics.Rect(0, 0, bmpW, intrinsicHeight);
                            android.graphics.Rect dstRect = new android.graphics.Rect(left + pad, top + pad, right - pad, bottom - pad);
                            piecePaint.setAlpha(255);
                            piecePaint.setColorFilter(null);
                            canvas.drawBitmap(bitmap, srcRect, dstRect, piecePaint);
                        } else {
                            drawable.setBounds(left + pad, top + pad, right - pad, bottom - pad);
                            drawable.draw(canvas);
                        }
                    } else {
                        drawable.setBounds(left + pad, top + pad, right - pad, bottom - pad);
                        drawable.draw(canvas);
                    }
                }
            }
        }
    }

    private boolean isSameColor(char p1, char p2) {
        if (p1 == ' ' || p2 == ' ') return false;
        return Character.isUpperCase(p1) == Character.isUpperCase(p2);
    }

    private java.util.List<int[]> getLegalDestinations(int f, int r) {
        java.util.List<int[]> dests = new java.util.ArrayList<>();
        if (f < 0 || f >= 8 || r < 0 || r >= 8) return dests;
        char piece = board[r][f];
        if (piece == ' ') return dests;

        boolean isWhite = Character.isUpperCase(piece);
        char type = Character.toLowerCase(piece);

        switch (type) {
            case 'p': {
                int dir = isWhite ? -1 : 1;
                int nextR = r + dir;
                if (nextR >= 0 && nextR < 8 && board[nextR][f] == ' ') {
                    dests.add(new int[]{f, nextR});
                    int startRow = isWhite ? 6 : 1;
                    if (r == startRow) {
                        int nextR2 = r + 2 * dir;
                        if (board[nextR2][f] == ' ') {
                            dests.add(new int[]{f, nextR2});
                        }
                    }
                }
                int[] capFiles = {f - 1, f + 1};
                for (int cf : capFiles) {
                    if (cf >= 0 && cf < 8 && nextR >= 0 && nextR < 8) {
                        char target = board[nextR][cf];
                        if (target != ' ' && Character.isUpperCase(target) != isWhite) {
                            dests.add(new int[]{cf, nextR});
                        }
                    }
                }
                break;
            }
            case 'n': {
                int[][] jumps = {
                    {1, 2}, {1, -2}, {-1, 2}, {-1, -2},
                    {2, 1}, {2, -1}, {-2, 1}, {-2, -1}
                };
                for (int[] j : jumps) {
                    int nf = f + j[0];
                    int nr = r + j[1];
                    if (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
                        char target = board[nr][nf];
                        if (target == ' ' || Character.isUpperCase(target) != isWhite) {
                            dests.add(new int[]{nf, nr});
                        }
                    }
                }
                break;
            }
            case 'k': {
                for (int dr = -1; dr <= 1; dr++) {
                    for (int df = -1; df <= 1; df++) {
                        if (dr == 0 && df == 0) continue;
                        int nf = f + df;
                        int nr = r + dr;
                        if (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
                            char target = board[nr][nf];
                            if (target == ' ' || Character.isUpperCase(target) != isWhite) {
                                dests.add(new int[]{nf, nr});
                            }
                        }
                    }
                }
                break;
            }
            case 'r': {
                int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] d : dirs) {
                    int nf = f;
                    int nr = r;
                    while (true) {
                        nf += d[0];
                        nr += d[1];
                        if (nf < 0 || nf >= 8 || nr < 0 || nr >= 8) break;
                        char target = board[nr][nf];
                        if (target == ' ') {
                            dests.add(new int[]{nf, nr});
                        } else {
                            if (Character.isUpperCase(target) != isWhite) {
                                dests.add(new int[]{nf, nr});
                            }
                            break;
                        }
                    }
                }
                break;
            }
            case 'b': {
                int[][] dirs = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
                for (int[] d : dirs) {
                    int nf = f;
                    int nr = r;
                    while (true) {
                        nf += d[0];
                        nr += d[1];
                        if (nf < 0 || nf >= 8 || nr < 0 || nr >= 8) break;
                        char target = board[nr][nf];
                        if (target == ' ') {
                            dests.add(new int[]{nf, nr});
                        } else {
                            if (Character.isUpperCase(target) != isWhite) {
                                dests.add(new int[]{nf, nr});
                            }
                            break;
                        }
                    }
                }
                break;
            }
            case 'q': {
                int[][] dirs = {
                    {0, 1}, {0, -1}, {1, 0}, {-1, 0},
                    {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
                };
                for (int[] d : dirs) {
                    int nf = f;
                    int nr = r;
                    while (true) {
                        nf += d[0];
                        nr += d[1];
                        if (nf < 0 || nf >= 8 || nr < 0 || nr >= 8) break;
                        char target = board[nr][nf];
                        if (target == ' ') {
                            dests.add(new int[]{nf, nr});
                        } else {
                            if (Character.isUpperCase(target) != isWhite) {
                                dests.add(new int[]{nf, nr});
                            }
                            break;
                        }
                    }
                }
                break;
            }
        }
        return dests;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isInteractable) return false;
        
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float squareSize = getWidth() / 8.0f;
            int drawFile = (int) (event.getX() / squareSize);
            int drawRank = (int) (event.getY() / squareSize);
            
            if (drawFile >= 0 && drawFile < 8 && drawRank >= 0 && drawRank < 8) {
                int f = isFlipped ? 7 - drawFile : drawFile;
                int r = isFlipped ? 7 - drawRank : drawRank;
                
                if (selectedFile == -1 && selectedRank == -1) {
                    // Selecting a piece
                    char piece = board[r][f];
                    if (piece != ' ') {
                        boolean isPieceWhite = Character.isUpperCase(piece);
                        boolean isPlayerWhite = (playerColor == 'w' || playerColor == 'a');
                        if (isPieceWhite == isPlayerWhite || playerColor == 'a') {
                            selectedFile = f;
                            selectedRank = r;
                            invalidate();
                        }
                    }
                } else {
                    // Moving a piece
                    if (selectedFile == f && selectedRank == r) {
                        // Deselect
                        selectedFile = -1;
                        selectedRank = -1;
                        invalidate();
                    } else if (board[r][f] != ' ' && isSameColor(board[selectedRank][selectedFile], board[r][f])) {
                        // Switch selection to another piece of same color
                        selectedFile = f;
                        selectedRank = r;
                        invalidate();
                    } else {
                        // Move validation
                        java.util.List<int[]> legal = getLegalDestinations(selectedFile, selectedRank);
                        boolean isLegal = false;
                        for (int[] d : legal) {
                            if (d[0] == f && d[1] == r) {
                                isLegal = true;
                                break;
                            }
                        }
                        if (isLegal) {
                            String fromSquare = toAlgebraic(selectedFile, selectedRank);
                            String toSquare = toAlgebraic(f, r);
                            if (listener != null) {
                                listener.onMove(fromSquare, toSquare);
                            }
                        }
                        selectedFile = -1;
                        selectedRank = -1;
                        invalidate();
                    }
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    private String toAlgebraic(int file, int rank) {
        char fileChar = (char) ('a' + file);
        char rankChar = (char) ('8' - rank);
        return "" + fileChar + rankChar;
    }
}
