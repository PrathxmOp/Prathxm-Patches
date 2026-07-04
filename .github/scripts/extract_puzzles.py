import subprocess
import gzip
import csv
import sys

def extract_puzzles():
    url = "https://database.lichess.org/lichess_db_puzzle.csv.zst"
    print(f"Streaming from {url}...")

    # Start curl to download and pipe to zstd for decompression
    curl_proc = subprocess.Popen(["curl", "-sL", url], stdout=subprocess.PIPE)
    zstd_proc = subprocess.Popen(["zstd", "-d"], stdin=curl_proc.stdout, stdout=subprocess.PIPE, text=True, bufsize=1)
    
    # Close curl_proc stdout in parent so it receives SIGPIPE if zstd exits
    curl_proc.stdout.close()

    max_limit = float('inf')
    if len(sys.argv) > 1:
        try:
            max_limit = int(sys.argv[1])
            print(f"Capping extraction at {max_limit} puzzles.")
        except ValueError:
            pass

    targets = [
        {"limit": 20000, "filename": "lichess_offline_puzzles_20k.csv.gz"},
        {"limit": 50000, "filename": "lichess_offline_puzzles_50k.csv.gz"},
        {"limit": 100000, "filename": "lichess_offline_puzzles_100k.csv.gz"},
        {"limit": 500000, "filename": "lichess_offline_puzzles_500k.csv.gz"},
        {"limit": max_limit, "filename": "lichess_offline_puzzles_all.csv.gz"}
    ]

    try:
        # Open all gzip files
        open_files = []
        writers = []
        for t in targets:
            f = gzip.open(t["filename"], "wt", encoding="utf-8", newline="")
            w = csv.writer(f)
            # Write header
            w.writerow(["PuzzleId", "FEN", "Moves", "Rating", "Themes"])
            open_files.append(f)
            writers.append(w)

        # Read first line from stream (header) to skip it
        header = zstd_proc.stdout.readline()
        if not header:
            print("Error: Could not read header from stream.")
            return

        count = 0
        for line in zstd_proc.stdout:
            line = line.strip()
            if not line:
                continue

            parts = line.split(",")
            if len(parts) >= 8:
                puzzle_id = parts[0]
                fen = parts[1]
                moves = parts[2]
                rating = parts[3]
                themes = parts[7]
                
                row = [puzzle_id, fen, moves, rating, themes]
                count += 1
                
                # Write to appropriate writers based on limit
                for i, t in enumerate(targets):
                    if count <= t["limit"]:
                        writers[i].writerow(row)

                # Stop early if we have reached the maximum needed limit
                if max_limit != float('inf') and count >= max_limit:
                    print(f"Reached max limit of {max_limit} puzzles. Stopping.")
                    break

                if count % 50000 == 0:
                    print(f"Processed {count} puzzles...")

        # Close all files
        for f in open_files:
            f.close()
        
        print(f"Successfully processed all {count} puzzles and generated database options.")

    finally:
        # Clean up subprocesses
        zstd_proc.terminate()
        curl_proc.terminate()

if __name__ == "__main__":
    extract_puzzles()
