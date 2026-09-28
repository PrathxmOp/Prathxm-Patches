/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 *
 * Derived from / ported from https://github.com/VenusIsJaded/Prathxm-Patches (GPL-3.0)
 */

package app.prathxm.chess.extension.stockfish;

import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Offline opening book for the local Game Review (Lichess chess-openings, CC0, ~3,600 named
 * lines, see {@link OpeningBookData}).
 *
 * <p>Chess.com's server review names the opening and marks the theory moves as "Book". The
 * local review had neither: the opening row stayed empty and every opening move was rated by
 * the engine, so normal theory moves were shown as "Excellent" or "Inaccuracy".
 *
 * <p>Lines are stored as move sequences in UCI with standard castling notation (the same form
 * as {@link BoardUtil#normalizeCastling}). Lookup is by move-sequence prefix, so only games
 * from the standard starting position are matched.
 */
public final class OpeningBook {
    private static final String TAG = "OpeningBook";

    /** Standard start position (placement + side to move), the only one the book applies to. */
    public static final String START_PLACEMENT = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";

    private OpeningBook() {}

    /** Result of a book lookup. */
    public static final class Match {
        /** ECO code, e.g. "C60". */
        public final String eco;
        /** Opening name, e.g. "Ruy Lopez: Morphy Defense". */
        public final String name;
        /** Number of plies from the start of the game that are book moves. */
        public final int bookPlies;

        Match(String eco, String name, int bookPlies) {
            this.eco = eco;
            this.name = name;
            this.bookPlies = bookPlies;
        }
    }

    /** "e2e4 e7e5 ..." -> index into NAMES/ECOS. */
    private static volatile Map<String, Integer> lines;
    /** Every prefix of every book line, so theory moves before the named position also count. */
    private static volatile java.util.Set<String> prefixes;
    private static String[] names;
    private static String[] ecos;

    private static synchronized void load() {
        if (lines != null) return;
        Map<String, Integer> map = new HashMap<>(8192);
        java.util.Set<String> pre = new java.util.HashSet<>(16384);
        java.util.ArrayList<String> n = new java.util.ArrayList<>(4096);
        java.util.ArrayList<String> e = new java.util.ArrayList<>(4096);
        try {
            StringBuilder b64 = new StringBuilder();
            for (String chunk : OpeningBookData.GZIP_BASE64) b64.append(chunk);
            byte[] gz = java.util.Base64.getDecoder().decode(b64.toString());
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(new ByteArrayInputStream(gz)), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int t1 = line.indexOf('\t');
                    int t2 = t1 < 0 ? -1 : line.indexOf('\t', t1 + 1);
                    if (t2 < 0) continue;
                    String moves = line.substring(t2 + 1);
                    if (!map.containsKey(moves)) {
                        map.put(moves, n.size());
                        e.add(line.substring(0, t1));
                        n.add(line.substring(t1 + 1, t2));
                    }
                    int sp = moves.indexOf(' ');
                    while (sp > 0) {
                        pre.add(moves.substring(0, sp));
                        sp = moves.indexOf(' ', sp + 1);
                    }
                    pre.add(moves);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load the opening book", t);
        }
        names = n.toArray(new String[0]);
        ecos = e.toArray(new String[0]);
        prefixes = pre;
        lines = map;
        Log.i(TAG, "Opening book: " + map.size() + " lines");
    }

    /** Number of named openings (0 if the book failed to load). */
    public static int size() {
        load();
        return lines.size();
    }

    /**
     * Longest named opening that the game follows, and how many leading plies are theory.
     *
     * @param startFen FEN the game starts from (non-standard starts never match)
     * @param moves    the game's moves in UCI with standard castling notation
     * @return the match, or null if the game does not start with a known opening
     */
    public static Match lookup(String startFen, List<String> moves) {
        if (moves == null || moves.isEmpty()) return null;
        if (startFen == null || !startFen.startsWith(START_PLACEMENT + " w ")) return null;
        load();
        StringBuilder key = new StringBuilder(moves.size() * 5);
        int bestIdx = -1;
        int bookPlies = 0;
        for (int i = 0; i < moves.size(); i++) {
            String m = moves.get(i);
            if (m == null) break;
            if (i > 0) key.append(' ');
            key.append(m);
            String k = key.toString();
            if (!prefixes.contains(k)) break;
            bookPlies = i + 1;
            Integer idx = lines.get(k);
            if (idx != null) bestIdx = idx;
        }
        if (bestIdx < 0) return null;
        return new Match(ecos[bestIdx], names[bestIdx], bookPlies);
    }
}
