from pathlib import Path
import os
import random

NUM_AMOUNT = 50_000_000
MIN = -1_000_000
MAX = 1_000_000

FILE_NAME = ""

def generate_numbers_file(file, num_amount):
    with open(file, "w") as f:
        for _ in range(num_amount):
            print(random.randrange(MIN, MAX),file=f)
            f.buffer.flush()

def main(FILE_AMOUNT):
    num_amount_per_file = NUM_AMOUNT // FILE_AMOUNT
    for i in range(FILE_AMOUNT):
        f = Path(FILE_NAME).parent / f"{Path(FILE_NAME).name}.{i+1}.txt"
        print(f"Creating {f.name} with {num_amount_per_file:_} lines")
        generate_numbers_file(f, num_amount_per_file)
    
if __name__ == "__main__":

    # Read FILE_NAME env variable and put it in a variable
    FILE_NAME   = os.getenv("FILE_NAME", "numbers")
    FILE_AMOUNT = int(os.getenv("FILE_AMOUNT", "1"))

    main(FILE_AMOUNT)
