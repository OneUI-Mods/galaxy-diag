// Map embedded executable payloads that Qualcomm marks PT_NULL rather than
// PT_LOAD.  Ghidra's stock ELF loader correctly handles ordinary segments but
// deliberately ignores these two modem code ranges.
//
// Run as a pre-script so auto-analysis can follow branches into the new blocks:
//   -preScript MapEmbeddedExecutableSegments.java image.mbn \
//       0xc2fc80:0x38000000:0x7a3ab4 \
//       0x13d3734:0x3c000000:0x3d16fc

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;

public class MapEmbeddedExecutableSegments extends GhidraScript {
    @Override
    protected void run() throws Exception {
        String[] arguments = getScriptArgs();
        if (arguments.length < 2) {
            printerr("usage: MapEmbeddedExecutableSegments.java <image> <fileoff:va:size>...");
            return;
        }

        File image = new File(arguments[0]);
        if (!image.isFile()) {
            printerr("image not found: " + image);
            return;
        }

        Memory memory = currentProgram.getMemory();
        for (int i = 1; i < arguments.length; i++) {
            String[] fields = arguments[i].split(":");
            if (fields.length != 3) {
                printerr("invalid segment: " + arguments[i]);
                continue;
            }
            long fileOffset = Long.decode(fields[0]);
            long virtualAddress = Long.decode(fields[1]);
            long size = Long.decode(fields[2]);
            Address start = currentProgram.getAddressFactory()
                .getDefaultAddressSpace().getAddress(virtualAddress);
            if (memory.getBlock(start) != null) {
                println("already_mapped=" + start + " size=0x" + Long.toHexString(size));
                continue;
            }

            try (InputStream input = new FileInputStream(image)) {
                input.skipNBytes(fileOffset);
                MemoryBlock block = memory.createInitializedBlock(
                    "qcom_exec_" + Long.toHexString(virtualAddress),
                    start,
                    input,
                    size,
                    monitor,
                    false
                );
                block.setRead(true);
                block.setWrite(false);
                block.setExecute(true);
                println("mapped=" + start
                    + " file_offset=0x" + Long.toHexString(fileOffset)
                    + " size=0x" + Long.toHexString(size));
            }
        }
    }
}
