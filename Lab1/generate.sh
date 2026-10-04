#!/usr/bin/env bash

FILE_AMOUNT=8 python3 ./Scripts/generate_numbers.py
cat numbers.*.txt > numbers.txt
