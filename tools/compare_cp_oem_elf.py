#!/usr/bin/env python3
"""Compare file-backed segments of two Qualcomm 32-bit little-endian ELFs.

Samsung's qdsp6_oemsw images have no section table, so section-based diff
tools lose the address relationship. This script pairs program headers by
type, virtual address and flags, then reports byte differences at runtime VAs.
It never modifies either input.
"""

from __future__ import annotations

import argparse
import hashlib
import struct
from dataclasses import dataclass
from pathlib import Path


ELF_HEADER = struct.Struct("<16sHHIIIIIHHHHHH")
PROGRAM_HEADER = struct.Struct("<IIIIIIII")


@dataclass(frozen=True)
class Segment:
    index: int
    kind: int
    offset: int
    virtual_address: int
    file_size: int
    memory_size: int
    flags: int
    data: bytes

    @property
    def key(self) -> tuple[int, int, int]:
        return self.kind, self.virtual_address, self.flags


def read_segments(path: Path) -> list[Segment]:
    image = path.read_bytes()
    if len(image) < ELF_HEADER.size:
        raise ValueError(f"{path}: truncated ELF header")
    header = ELF_HEADER.unpack_from(image)
    ident = header[0]
    if ident[:4] != b"\x7fELF" or ident[4] != 1 or ident[5] != 1:
        raise ValueError(f"{path}: expected ELF32 little-endian image")
    program_offset = header[5]
    program_entry_size = header[9]
    program_count = header[10]
    if program_entry_size != PROGRAM_HEADER.size:
        raise ValueError(f"{path}: unexpected program-header size {program_entry_size}")

    segments: list[Segment] = []
    for index in range(program_count):
        offset = program_offset + index * program_entry_size
        kind, file_offset, virtual_address, _, file_size, memory_size, flags, _ = (
            PROGRAM_HEADER.unpack_from(image, offset)
        )
        end = file_offset + file_size
        if end > len(image):
            raise ValueError(f"{path}: segment {index} extends past EOF")
        if file_size:
            segments.append(
                Segment(
                    index,
                    kind,
                    file_offset,
                    virtual_address,
                    file_size,
                    memory_size,
                    flags,
                    image[file_offset:end],
                )
            )
    return segments


def difference_runs(left: bytes, right: bytes, base: int) -> list[tuple[int, int]]:
    limit = min(len(left), len(right))
    runs: list[tuple[int, int]] = []
    start: int | None = None
    for index in range(limit):
        different = left[index] != right[index]
        if different and start is None:
            start = index
        elif not different and start is not None:
            runs.append((base + start, base + index))
            start = None
    if start is not None:
        runs.append((base + start, base + limit))
    if len(left) != len(right):
        runs.append((base + limit, base + max(len(left), len(right))))
    return runs


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("left", type=Path)
    parser.add_argument("right", type=Path)
    parser.add_argument("--max-runs", type=int, default=12)
    arguments = parser.parse_args()

    left_segments = read_segments(arguments.left)
    right_segments = read_segments(arguments.right)
    left_by_key = {segment.key: segment for segment in left_segments}
    right_by_key = {segment.key: segment for segment in right_segments}

    print(f"left_sha256={hashlib.sha256(arguments.left.read_bytes()).hexdigest()}")
    print(f"right_sha256={hashlib.sha256(arguments.right.read_bytes()).hexdigest()}")
    all_keys = sorted(set(left_by_key) | set(right_by_key), key=lambda key: key[1])
    for key in all_keys:
        left = left_by_key.get(key)
        right = right_by_key.get(key)
        kind, virtual_address, flags = key
        if left is None or right is None:
            side = "right-only" if left is None else "left-only"
            segment = right if left is None else left
            assert segment is not None
            print(
                f"0x{virtual_address:08x} type={kind} flags=0x{flags:x} "
                f"{side} size=0x{segment.file_size:x}"
            )
            continue

        limit = min(len(left.data), len(right.data))
        different_bytes = sum(
            left.data[index] != right.data[index] for index in range(limit)
        ) + abs(len(left.data) - len(right.data))
        state = "identical" if different_bytes == 0 else "different"
        print(
            f"0x{virtual_address:08x} type={kind} flags=0x{flags:x} {state} "
            f"left=0x{left.file_size:x} right=0x{right.file_size:x} "
            f"different_bytes={different_bytes}"
        )
        if different_bytes:
            runs = difference_runs(left.data, right.data, virtual_address)
            rendered = ", ".join(
                f"0x{start:08x}-0x{end - 1:08x}" for start, end in runs[: arguments.max_runs]
            )
            suffix = " ..." if len(runs) > arguments.max_runs else ""
            print(f"  runs={rendered}{suffix}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
