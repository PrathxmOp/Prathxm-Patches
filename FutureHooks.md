# Chess.com Hook Documentation

A curated list of all hookable methods and useful entry points found in the decompiled Chess.com app, organized by functionality to speed up future patch development.

## Important Note
- **Jetpack Compose Screens**: Theme patches (like AMOLED) **only work for traditional XML layouts**. Attempting to modify Compose screens via Morphe/bytecode patching is not feasible and failed in previous attempts.

---

## Ad & Premium Related Hooks
Located in: `com/chess/net/model/LoginData.java`, `com/chess/net/model/UserData.java`
- [x] `getShow_ads()` → `Z` (boolean, primitive, from LoginData)
- [x] `getShow_interstitial_ads()` → `Ljava/lang/Boolean;` (boxed, from LoginData)
- [x] `getShow_ads()` → `Ljava/lang/Boolean;` (boxed, from UserData)
- [x] `getShow_interstitial_ads()` → `Ljava/lang/Boolean;` (boxed, from UserData)
- [x] `getPremium_status()` → `I` (int, from LoginData)
- [x] `getPremium_status()` → `Lcom/chess/entities/PremiumStatus;` (from UserData)

---

## Puzzle Related Hooks
Located in:
- `com/chess/home/play/PuzzlePaywallGate.java`
- `com/chess/features/puzzles/daily/net/NewDailyPuzzleServiceImpl.java`
- `com/chess/internal/puzzles/PuzzleOfflineLimit.java`
- `com/chess/net/v1/users/SessionStore.java`

### Puzzle Entry Point
- [x] `PuzzlePaywallGate.d(Lcom/chess/navigationinterface/PathPuzzlesMode; Lcom/google/android/g74;)Ljava/lang/Object;` - Launches puzzle flow
### Puzzle Data Interceptors
- [x] `NewDailyPuzzleServiceImpl.a(Ljava/lang/String; Lcom/google/android/g74;)Ljava/lang/Object;` - Get daily puzzle data
- [x] `NewDailyPuzzleServiceImpl.b(ILchesscom/puzzles/v2alpha/DailyPuzzleAction; Lchesscom/puzzles/v2alpha/DailyPuzzleHintState; Lcom/google/android/g74;)Ljava/lang/Object;` - Submit puzzle action
### Premium Bypass Hooks
- [x] `PuzzlePaywallGate.b(Lcom/chess/navigationinterface/PathPuzzlesMode;)Z` - Mode check
- [x] `PuzzlePaywallGate.c(Ljava/lang/Throwable;)Z` - Error check
- [x] `SessionStore.i()Z` - Premium check 1
- [x] `SessionStore.t()Z` - Premium check 2
- [x] `PuzzleOfflineLimit.f(ILcom/google/android/g74;)Ljava/lang/Object;` - Set offline limit

---

## Chessboard & Analysis Related Hooks
Located in:
- `com/chess/chessboard/vm/movesinput/CBViewModelStateImpl.java`
- `com/chess/gamereview/v2/v0.java`
- `com/chess/gamereview/repository/GameAnalysisRepositoryImpl.java`
- `com/chess/entities/GameAnalysisPermissions.java`

### Chessboard State Hooks
- [x] `CBViewModelStateImpl.m(Lcom/chess/chessboard/variants/d;)V` - Position setter
- [x] `CBViewModelStateImpl.a2(Ljava/util/List;)V` - Set move arrows
- [x] `CBViewModelStateImpl.getPosition()Lcom/chess/chessboard/variants/d;` - Get position
### Analysis Permissions
- [x] `GameAnalysisPermissions.getCanCreate()Z`
- [x] `GameAnalysisPermissions.getCanMoveFeedback()Z`
- [x] `GameAnalysisPermissions.getCanMoveStrength()Z`
- [x] `GameAnalysisPermissions.getCanViewAccuracyAndMoves()Z`
- [x] `GameAnalysisPermissions.getCanViewCoachCommentary()Z`
### Game Analysis Interceptors
- [x] `GameAnalysisRepositoryImpl.b(7 params)Lcom/google/android/g74;` - Get game analysis flow
- [x] `com/chess/gamereview/v2/v0.D(Lcom/chess/chessboard/variants/d; Lcom/chess/gamereview/repository/AnalyzedGameData$AnalyzedPosition$Eval;)Lcom/chess/gamereview/api/n;`
- [x] `com/chess/gamereview/v2/v0.J(Lcom/chess/gamereview/repository/AnalyzedGameData$AnalyzedPosition; Lcom/chess/chessboard/history/i; Lcom/chess/entities/GameAnalysisPermissions; Z)Lcom/chess/gamereview/api/d;`
### Optional Painters
- [x] `ChessBoardViewOptionalPainterType.companion.b(7 params)` - Inject KEY_MOVE_HINTS

---

## App Startup Hooks
Located in: `com/chess/MainApplication.java`
- [x] `onCreate()V` - Early initialization point

---

## Potential Future Hook Ideas (From Decompiled App)
1. **Coach Hooks**: Modify coach behavior, coach lines, etc.
2. **Theme Hooks**: Modify themes beyond AMOLED (add custom themes)
3. **Game Mode Hooks**: Unlock premium game modes
4. **Puzzle Mode Hooks**: Unlock premium puzzle packs
5. **Board Theme Hooks**: Add custom board/ piece themes
6. **Notification Hooks**: Modify notifications
7. **Settings Hooks**: Add custom settings
