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

    public LichessBoardView(Context context) {
        super(context);
        paint.setFilterBitmap(true);
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
        try {
            int fromFile = from.charAt(0) - 'a';
            int fromRank = '8' - from.charAt(1);
            int toFile = to.charAt(0) - 'a';
            int toRank = '8' - to.charAt(1);
            
            char piece = board[fromRank][fromFile];
            
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
        
        // Draw squares
        for (int r = 0; r < 8; r++) {
            for (int f = 0; f < 8; f++) {
                int drawRank = isFlipped ? 7 - r : r;
                int drawFile = isFlipped ? 7 - f : f;
                
                // Draw base square color
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

                // Draw coordinate numbers (ranks 1-8) on left-most visible file
                if (drawFile == 0) {
                    int rankNum = isFlipped ? (r + 1) : (8 - r);
                    paint.setColor((r + f) % 2 == 0 ? COLOR_DARK : COLOR_LIGHT);
                    paint.setTextSize(squareSize * 0.18f);
                    paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
                    paint.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText(
                            String.valueOf(rankNum),
                            drawFile * squareSize + (squareSize * 0.06f),
                            drawRank * squareSize + (squareSize * 0.20f),
                            paint
                    );
                }
                
                // Draw coordinate letters (files a-h) on bottom-most visible rank
                if (drawRank == 7) {
                    char fileChar = isFlipped ? (char) ('h' - f) : (char) ('a' + f);
                    paint.setColor((r + f) % 2 == 0 ? COLOR_DARK : COLOR_LIGHT);
                    paint.setTextSize(squareSize * 0.18f);
                    paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
                    paint.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(
                            String.valueOf(fileChar),
                            (drawFile + 1) * squareSize - (squareSize * 0.06f),
                            (drawRank + 1) * squareSize - (squareSize * 0.06f),
                            paint
                    );
                }
                
                // Draw piece drawable from host application assets
                char piece = board[r][f];
                if (piece != ' ') {
                    String resName = getPieceDrawableName(piece);
                    if (resName != null) {
                        // Get host (Chess.com) package name
                        String hostPackage = getContext().getPackageName();
                        int resId = getContext().getResources().getIdentifier(
                                resName, "drawable", hostPackage);
                        if (resId != 0) {
                            Drawable drawable = getContext().getResources().getDrawable(resId, null);
                            if (drawable != null) {
                                int left = (int) (drawFile * squareSize);
                                int top = (int) (drawRank * squareSize);
                                int right = (int) ((drawFile + 1) * squareSize);
                                int bottom = (int) ((drawRank + 1) * squareSize);
                                
                                // Padding around piece for perfect spacing
                                int pad = (int) (squareSize * 0.05f);
                                
                                if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                                    android.graphics.Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) drawable).getBitmap();
                                    if (bitmap != null) {
                                        int bmpW = bitmap.getWidth();
                                        int bmpH = bitmap.getHeight();
                                        int intrinsicHeight = (bmpH * 2) / 3;
                                        
                                        android.graphics.Rect srcRect = new android.graphics.Rect();
                                        int destWidth = right - left - 2 * pad;
                                        
                                        if (destWidth < bmpW / 8) {
                                            srcRect.left = (bmpW / 2) + (bmpW / 4);
                                            srcRect.right = (bmpW / 2) + (bmpW / 4) + (bmpW / 8);
                                            srcRect.top = intrinsicHeight;
                                            srcRect.bottom = intrinsicHeight + (intrinsicHeight / 8);
                                        } else if (destWidth < bmpW / 4) {
                                            srcRect.left = bmpW / 2;
                                            srcRect.right = (bmpW / 2) + (bmpW / 4);
                                            srcRect.top = intrinsicHeight;
                                            srcRect.bottom = intrinsicHeight + (intrinsicHeight / 4);
                                        } else if (destWidth < bmpW / 2) {
                                            srcRect.left = 0;
                                            srcRect.right = bmpW / 2;
                                            srcRect.top = intrinsicHeight;
                                            srcRect.bottom = intrinsicHeight + (intrinsicHeight / 2);
                                        } else {
                                            srcRect.left = 0;
                                            srcRect.right = bmpW;
                                            srcRect.top = 0;
                                            srcRect.bottom = intrinsicHeight;
                                        }
                                        
                                        android.graphics.Rect dstRect = new android.graphics.Rect(left + pad, top + pad, right - pad, bottom - pad);
                                        canvas.drawBitmap(bitmap, srcRect, dstRect, paint);
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

    private boolean isSameColor(char p1, char p2) {
        if (p1 == ' ' || p2 == ' ') return false;
        return Character.isUpperCase(p1) == Character.isUpperCase(p2);
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
                        boolean isPlayerWhite = (playerColor == 'w');
                        if (isPieceWhite == isPlayerWhite) {
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
                        String fromSquare = toAlgebraic(selectedFile, selectedRank);
                        String toSquare = toAlgebraic(f, r);
                        if (listener != null) {
                            listener.onMove(fromSquare, toSquare);
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
