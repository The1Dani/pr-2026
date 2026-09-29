import os
import random

NUM_AMOUNT = 50_000_000
MIN = -1_000_000
MAX = 1_000_000

FILE_PATH = ""

def main():
    with open(FILE_PATH, "w") as f:
        for _ in range(NUM_AMOUNT):
            print(random.randrange(MIN, MAX),file=f)
            f.buffer.flush()

if __name__ == "__main__":

    # Read FILE_PATH env variable and put it in a variable
    FILE_PATH = os.getenv("FILE_PATH", "numbers.txt")

    main()
