#!/usr/bin/env python3
"""Extract selected logical partitions from a raw or Android sparse super image.

This intentionally reads only the extents for requested partitions.  It is useful
for large Samsung firmware archives where fully expanding super.img would waste
both time and disk space.
"""

from __future__ import annotations

import argparse
import struct
from pathlib import Path


SPARSE_MAGIC = 0xED26FF3A
LP_GEOMETRY_MAGIC = 0x616C4467
LP_HEADER_MAGIC = 0x414C5030


class SparseReader:
    def __init__(self, path: Path) -> None:
        self.file = path.open("rb")
        header = self.file.read(28)
        magic, _, _, file_header_size, chunk_header_size, block_size, _, chunk_count, _ = (
            struct.unpack("<IHHHHIIII", header)
        )
        self.sparse = magic == SPARSE_MAGIC
        self.block_size = block_size
        self.chunks: list[tuple[int, int, str, int | bytes | None]] = []
        if not self.sparse:
            return

        self.file.seek(file_header_size)
        output_block = 0
        for _ in range(chunk_count):
            chunk_header = self.file.read(chunk_header_size)
            if len(chunk_header) != chunk_header_size or chunk_header_size < 12:
                raise ValueError("truncated sparse chunk header")
            chunk_type, _, chunk_blocks, total_size = struct.unpack(
                "<HHII", chunk_header[:12]
            )
            data_offset = self.file.tell()
            if chunk_type == 0xCAC1:
                value: int | bytes | None = data_offset
                kind = "raw"
            elif chunk_type == 0xCAC2:
                value = self.file.read(4)
                kind = "fill"
            elif chunk_type == 0xCAC3:
                value = None
                kind = "zero"
            else:
                value = None
                kind = "skip"
            self.chunks.append((output_block, chunk_blocks, kind, value))
            self.file.seek(data_offset + total_size - chunk_header_size)
            output_block += chunk_blocks

    def read(self, offset: int, size: int) -> bytes:
        if not self.sparse:
            self.file.seek(offset)
            return self.file.read(size)

        output = bytearray()
        while size:
            block = offset // self.block_size
            chunk = next(
                item for item in self.chunks if item[0] <= block < item[0] + item[1]
            )
            within = offset - chunk[0] * self.block_size
            take = min(size, chunk[1] * self.block_size - within)
            if chunk[2] == "raw":
                self.file.seek(int(chunk[3]) + within)
                output.extend(self.file.read(take))
            elif chunk[2] == "fill":
                fill = bytes(chunk[3])
                output.extend((fill * (take // 4 + 2))[within % 4 : within % 4 + take])
            else:
                output.extend(bytes(take))
            offset += take
            size -= take
        return bytes(output)


def extract(super_image: Path, output_dir: Path, requested: set[str]) -> None:
    reader = SparseReader(super_image)
    geometry = reader.read(4096, 4096)
    if struct.unpack("<I", geometry[:4])[0] != LP_GEOMETRY_MAGIC:
        raise ValueError("LP geometry not found")

    metadata = reader.read(4096 * 3, 4096 * 64)
    if struct.unpack("<I", metadata[:4])[0] != LP_HEADER_MAGIC:
        raise ValueError("LP metadata header not found")

    header_size = struct.unpack("<I", metadata[8:12])[0]
    descriptors = struct.unpack("<12I", metadata[0x50:0x80])
    partition_offset, partition_count, partition_size = descriptors[0:3]
    extent_offset, extent_count, extent_size = descriptors[3:6]
    tables = metadata[header_size:]

    extents = [
        struct.unpack(
            "<QIQI",
            tables[extent_offset + index * extent_size :][
                :extent_size
            ],
        )
        for index in range(extent_count)
    ]

    output_dir.mkdir(parents=True, exist_ok=True)
    found: set[str] = set()
    for index in range(partition_count):
        entry = tables[
            partition_offset + index * partition_size :
            partition_offset + (index + 1) * partition_size
        ]
        name = entry[:36].split(b"\0", 1)[0].decode()
        first_extent, extent_count_for_partition = struct.unpack("<II", entry[40:48])
        if name not in requested:
            continue
        found.add(name)
        output_path = output_dir / f"{name}.img"
        with output_path.open("wb") as output:
            for sectors, target_type, target_data, _ in extents[
                first_extent : first_extent + extent_count_for_partition
            ]:
                remaining = sectors * 512
                if target_type == 0:
                    # LP linear extents store an absolute sector in the target
                    # block device.  first_logical_sector is an allocation
                    # boundary, not an extra offset to add here.
                    source_offset = target_data * 512
                    while remaining:
                        chunk_size = min(remaining, 64 << 20)
                        output.write(reader.read(source_offset, chunk_size))
                        source_offset += chunk_size
                        remaining -= chunk_size
                else:
                    output.write(bytes(remaining))
        print(f"{name}\t{output_path.stat().st_size}\t{output_path}")

    missing = requested - found
    if missing:
        raise ValueError(f"partitions not found: {', '.join(sorted(missing))}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("super_image", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("partitions", nargs="+")
    args = parser.parse_args()
    extract(args.super_image, args.output_dir, set(args.partitions))


if __name__ == "__main__":
    main()
