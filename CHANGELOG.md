## [1.4.0](https://github.com/PrathxmOp/Prathxm-Patches/compare/v1.3.1...v1.4.0) (2026-07-01)

### 🐛 Bug Fixes

* **lichess-puzzle:** fix closed cursor crash and update download_offset incrementally ([ed3ae1f](https://github.com/PrathxmOp/Prathxm-Patches/commit/ed3ae1fc17c69a476d429d1da8e7d4f40cc83db8))

### ✨ New Features

* **ci:** auto-release datasets via release.yml and point downloads to database release ([fbca65a](https://github.com/PrathxmOp/Prathxm-Patches/commit/fbca65a23535ba548b38b851d2be7c13d325a8e2))
* initial lichess puzzle implementation (fallback point) ([c1feff5](https://github.com/PrathxmOp/Prathxm-Patches/commit/c1feff563967a5c73b8d789cbb3653d4f10ffc0e))
* **lichess-puzzle:** add 5 download sizes and GitHub Action database auto-updater ([6693872](https://github.com/PrathxmOp/Prathxm-Patches/commit/6693872628ea9f1f0238fa7f9c473b52d1895b42))
* **lichess-puzzle:** add download size selector, fix milestones and display active theme ([098b703](https://github.com/PrathxmOp/Prathxm-Patches/commit/098b70367716a056177d06cfc000528a730ad4e1))
* **lichess-puzzle:** add support for thematic puzzle categories and category progress tracking ([542c278](https://github.com/PrathxmOp/Prathxm-Patches/commit/542c278d9487cdce3054048805f5e07624fd43e1))
* **lichess-puzzle:** integrate gamified streak progress bar and premium UI elements into journey map activity ([7215f1a](https://github.com/PrathxmOp/Prathxm-Patches/commit/7215f1ac030103c9f0fc2ce176e7293674170e2c))
* **lichess-puzzle:** migrate offline puzzle storage to persistent SQLite database ([bf85d05](https://github.com/PrathxmOp/Prathxm-Patches/commit/bf85d0546e4ca1306d3898262c8e9b2087a84081))
* **lichess-puzzle:** refine standalone puzzle journey, board rendering, game sounds, and daily puzzle completions ([f1a1a8c](https://github.com/PrathxmOp/Prathxm-Patches/commit/f1a1a8c7986959ba84bcc0c4fd5588344f574d89))
* **puzzles:** align LichessPuzzleJourney UI with premium Chess.com standards ([634ff66](https://github.com/PrathxmOp/Prathxm-Patches/commit/634ff6675d55ea0b3deb78c3dcc0bb6b4719a9f9))

## [1.3.1](https://github.com/PrathxmOp/Prathxm-Patches/compare/v1.3.0...v1.3.1) (2026-06-29)

### 🐛 Bug Fixes

* rename discussion template to match category slug ([35569a2](https://github.com/PrathxmOp/Prathxm-Patches/commit/35569a28ac3a2dc93245bb4b97064e9d4197a76a))

## [1.3.0](https://github.com/PrathxmOp/Prathxm-Patches/compare/v1.2.0...v1.3.0) (2026-06-29)

### ✨ New Features

* refine settings UI, add Clone App patch, and stabilize fair play logic ([909e529](https://github.com/PrathxmOp/Prathxm-Patches/commit/909e529b5c61a57f408f8da6177df019111b4486))

## [1.2.0](https://github.com/PrathxmOp/Prathxm-Patches/compare/v1.1.0...v1.2.0) (2026-06-29)

### ✨ New Features

* **fair-play:** implement Fair Play Analysis Controls, warnings, silent mode, and post-game summary ([901170b](https://github.com/PrathxmOp/Prathxm-Patches/commit/901170bf73da5b840629d50988163305550cd27b))
* **stockfish:** dynamic reflection for local analysis flow and offline review integration ([3a1ded5](https://github.com/PrathxmOp/Prathxm-Patches/commit/3a1ded5b9e6d205a0da00a27ccd632f3237beb98))
* **stockfish:** stabilize game review, add dynamic rating logic, and update roadmap ([2bd605a](https://github.com/PrathxmOp/Prathxm-Patches/commit/2bd605a079c19e9b5db76423b997711dd44c3860))

## [1.1.0](https://github.com/PrathxmOp/Prathxm-Patches/compare/v1.0.0...v1.1.0) (2026-06-29)

### 🐛 Bug Fixes

* **classification:** enable move classification for user moves when My Side Only is active ([dbbb8c0](https://github.com/PrathxmOp/Prathxm-Patches/commit/dbbb8c0ea73091050a9c20d2f7ff77a0f1306494))
* **eval-bar:** stabilize eval bar and move WDL to horizontal strip below board ([c500beb](https://github.com/PrathxmOp/Prathxm-Patches/commit/c500bebccd5b191d4bca622d50e9a0c56ae67b39))
* **stockfish:** correct move classification priority and update settings defaults ([1f2db25](https://github.com/PrathxmOp/Prathxm-Patches/commit/1f2db25a6d90b67833b2138ed40a21f88c9c05ca))
* **ui:** reposition WDL bar and Mate announcement above the board to prevent overlapping ([b0887d1](https://github.com/PrathxmOp/Prathxm-Patches/commit/b0887d1362905c2bc671c6d315a8e5e987fe64f8))

### ✨ New Features

* **gestures:** hide all overlays on double-tap and fix WDL percentage scale ([673a589](https://github.com/PrathxmOp/Prathxm-Patches/commit/673a589c55898bbaf0c3e2b0fa73b7379483a9c4))
* **stockfish:** add move classification, blunder alerts, custom settings UI, and workflows ([6a7e3d9](https://github.com/PrathxmOp/Prathxm-Patches/commit/6a7e3d921f2053a2b866995cc72e78bf1715f502))

## 1.0.0 (2026-06-29)

### ✨ New Features

* integrate Stockfish engine, settings menu, and migrate packages to app.prathxm.chess ([494efad](https://github.com/PrathxmOp/Prathxm-Patches/commit/494efad01ad3bed69d5837bca062d7a9302b3ad1))
