/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.extension.stockfish;

import java.util.List;

/**
 * Minimal, allocation-light board model used by the game review.
 *
 * It does NOT generate moves or check legality – it only applies moves that are already
 * known to be legal (moves from the game or from Stockfish's principal variation). That is
 * enough to follow the engine's line to detect real sacrifices, detect recaptures,
 * normalise castling notation before it is sent to Stockfish, and classify the game phase.
 *
 * Squares are indexed 0..63 with 0 = a8 and 63 = h1 (FEN order).
 */
public final class BoardUtil {

    private BoardUtil() {}

    /** Expand the piece-placement field of a FEN into a 64 char array ('.' = empty). */
    public static char[] parseBoard(String fen) {
        char[] b = new char[64];
        java.util.Arrays.fill(b, '.');
        if (fen == null) return b;
        int sp = fen.indexOf(' ');
        String placement = sp >= 0 ? fen.substring(0, sp) : fen;
        int idx = 0;
        for (int i = 0; i < placement.length() && idx < 64; i++) {
            char c = placement.charAt(i);
            if (c == '/') continue;
            if (c >= '1' && c <= '8') {
                idx += c - '0';
            } else {
                b[idx++] = c;
            }
        }
        return b;
    }

    /** Piece placement field only. */
    public static String placement(String fen) {
        if (fen == null) return null;
        int sp = fen.indexOf(' ');
        return sp >= 0 ? fen.substring(0, sp) : fen;
    }

    public static String placement(char[] b) {
        StringBuilder sb = new StringBuilder(72);
        for (int r = 0; r < 8; r++) {
            int empty = 0;
            for (int f = 0; f < 8; f++) {
                char c = b[r * 8 + f];
                if (c == '.') {
                    empty++;
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(c);
                }
            }
            if (empty > 0) sb.append(empty);
            if (r < 7) sb.append('/');
        }
        return sb.toString();
    }

    public static int square(String uci, int offset) {
        if (uci == null || uci.length() < offset + 2) return -1;
        int file = uci.charAt(offset) - 'a';
        int rank = uci.charAt(offset + 1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) return -1;
        return (7 - rank) * 8 + file;
    }

    public static String squareName(int sq) {
        return "" + (char) ('a' + (sq % 8)) + (char) ('1' + (7 - sq / 8));
    }

    /**
     * Convert "king takes own rook" castling notation (e1h1 / e1a1) into standard notation
     * (e1g1 / e1c1). Stockfish 19 terminates on illegal moves, so this must be done before a
     * move list is sent to the engine.
     */
    public static String normalizeCastling(char[] b, String uci) {
        if (uci == null || uci.length() < 4) return uci;
        int from = square(uci, 0);
        int to = square(uci, 2);
        if (from < 0 || to < 0) return uci;
        char p = b[from];
        if (p != 'K' && p != 'k') return uci;
        char ownRook = p == 'K' ? 'R' : 'r';
        if (b[to] == ownRook && from / 8 == to / 8) {
            int fromFile = from % 8;
            int toFile = to % 8;
            int dest = (from / 8) * 8 + (toFile > fromFile ? 6 : 2);
            return squareName(from) + squareName(dest);
        }
        return uci;
    }

    /**
     * Apply a legal UCI move in place.
     *
     * @return the captured piece ('.' if none)
     */
    public static char apply(char[] b, String uci) {
        int from = square(uci, 0);
        int to = square(uci, 2);
        if (from < 0 || to < 0) return '.';
        char p = b[from];
        if (p == '.') return '.';
        char captured = b[to];

        boolean white = Character.isUpperCase(p);
        char lower = Character.toLowerCase(p);

        // Castling written as king-takes-rook
        if (lower == 'k' && captured == (white ? 'R' : 'r')) {
            int row = from / 8;
            boolean kingSide = (to % 8) > (from % 8);
            b[from] = '.';
            b[to] = '.';
            b[row * 8 + (kingSide ? 6 : 2)] = p;
            b[row * 8 + (kingSide ? 5 : 3)] = white ? 'R' : 'r';
            return '.';
        }

        // Standard castling (king moves two files)
        if (lower == 'k' && Math.abs((to % 8) - (from % 8)) == 2 && from / 8 == to / 8) {
            int row = from / 8;
            boolean kingSide = (to % 8) > (from % 8);
            int rookFrom = row * 8 + (kingSide ? 7 : 0);
            int rookTo = row * 8 + (kingSide ? 5 : 3);
            b[rookTo] = b[rookFrom];
            b[rookFrom] = '.';
        }

        // En passant: pawn moves diagonally onto an empty square
        if (lower == 'p' && (from % 8) != (to % 8) && captured == '.') {
            int capSq = (from / 8) * 8 + (to % 8);
            captured = b[capSq];
            b[capSq] = '.';
        }

        b[to] = p;
        b[from] = '.';

        // Promotion
        if (uci.length() >= 5 && lower == 'p') {
            char promo = uci.charAt(4);
            b[to] = white ? Character.toUpperCase(promo) : Character.toLowerCase(promo);
        }
        return captured;
    }

    public static int pieceValue(char c) {
        switch (Character.toLowerCase(c)) {
            case 'p': return 1;
            case 'n':
            case 'b': return 3;
            case 'r': return 5;
            case 'q': return 9;
            default: return 0;
        }
    }

    /** Material of one side (pawn = 1). */
    public static int material(char[] b, boolean white) {
        int s = 0;
        for (char c : b) {
            if (c != '.' && Character.isUpperCase(c) == white) s += pieceValue(c);
        }
        return s;
    }

    /** Non-pawn material of one side. */
    public static int nonPawnMaterial(char[] b, boolean white) {
        int s = 0;
        for (char c : b) {
            if (c != '.' && c != 'p' && c != 'P' && Character.isUpperCase(c) == white) s += pieceValue(c);
        }
        return s;
    }

    /** 0 = opening, 1 = middlegame, 2 = endgame. */
    public static int phase(char[] b, int ply) {
        int npm = nonPawnMaterial(b, true) + nonPawnMaterial(b, false);
        boolean queens = false;
        for (char c : b) if (c == 'Q' || c == 'q') { queens = true; break; }
        if (npm <= 26 || (!queens && npm <= 32)) return 2;
        if (ply < 24) return 0;
        return 1;
    }

    /** Does the move land on the square where the opponent just captured? */
    public static boolean isRecapture(String prevMove, boolean prevWasCapture, String move) {
        if (!prevWasCapture || prevMove == null || move == null) return false;
        if (prevMove.length() < 4 || move.length() < 4) return false;
        return prevMove.regionMatches(2, move, 2, 2);
    }

    /**
     * Detect a genuine sacrifice by following Stockfish's best line after the played move.
     *
     * The move is a sacrifice if, after the last opponent move inside the engine line window,
     * the mover is down at least two points compared with before the move and has lost
     * non-pawn material. Ordinary trades (material comes back) are therefore ignored.
     *
     * @param before     board before the played move (not modified)
     * @param moverWhite side that played the move
     * @param played     the played move
     * @param replyPv    engine PV from the position after the played move (opponent to move)
     */
    public static boolean isSacrifice(char[] before, boolean moverWhite, String played,
                                      List<String> replyPv, int window) {
        if (before == null || played == null || replyPv == null || replyPv.isEmpty()) return false;
        char[] b = before.clone();
        int diff0 = material(b, moverWhite) - material(b, !moverWhite);
        int pieces0 = nonPawnMaterial(b, moverWhite);

        apply(b, played);

        boolean endDown = false;
        int plies = Math.min(window, replyPv.size());
        for (int i = 0; i < plies; i++) {
            apply(b, replyPv.get(i));
            if ((i & 1) == 0) { // after an opponent move
                int diff = material(b, moverWhite) - material(b, !moverWhite);
                endDown = diff <= diff0 - 2 && nonPawnMaterial(b, moverWhite) < pieces0;
            }
        }
        return endDown;
    }
}
