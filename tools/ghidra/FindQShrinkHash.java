// Locate instruction scalars that encode a QShrink message hash.
//
// QShrink4 encodes its 20-bit database key in bits 3..22 of a message
// argument.  This script searches decoded instructions rather than raw bytes,
// which is important for Hexagon's split/immediate instruction encoding.
//
// Example:
//   -postScript FindQShrinkHash.java 124124


import java.util.HashSet;
import java.util.Set;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.listing.InstructionIterator;
import ghidra.program.model.scalar.Scalar;

public class FindQShrinkHash extends GhidraScript {
    private static final long HASH_MASK = 0xfffffL;

    @Override
    protected void run() throws Exception {
        String[] arguments = getScriptArgs();
        if (arguments.length != 1) {
            printerr("usage: FindQShrinkHash.java <decimal-or-hex-hash>");
            return;
        }

        long target = Long.decode(arguments[0]) & HASH_MASK;
        DecompInterface decompiler = new DecompInterface();
        decompiler.toggleCCode(true);
        decompiler.toggleSyntaxTree(true);
        if (!decompiler.openProgram(currentProgram)) {
            printerr("Decompiler initialization failed: " + decompiler.getLastMessage());
            return;
        }

        Set<String> decompiledEntries = new HashSet<>();
        InstructionIterator instructions = currentProgram.getListing().getInstructions(true);
        int hits = 0;
        while (instructions.hasNext() && !monitor.isCancelled()) {
            Instruction instruction = instructions.next();
            for (int operand = 0; operand < instruction.getNumOperands(); operand++) {
                Scalar scalar = instruction.getScalar(operand);
                if (scalar == null) {
                    continue;
                }

                long value = scalar.getUnsignedValue() & 0xffffffffL;
                if (((value >>> 3) & HASH_MASK) != target) {
                    continue;
                }

                hits++;
                Function function = currentProgram.getFunctionManager()
                    .getFunctionContaining(instruction.getAddress());
                println("hit=" + instruction.getAddress()
                    + " scalar=0x" + Long.toHexString(value)
                    + " operand=" + operand
                    + " instruction=" + instruction
                    + " function=" + (function == null ? "<none>" : function.getName())
                    + " entry=" + (function == null ? "<none>" : function.getEntryPoint()));

                if (function == null || !decompiledEntries.add(function.getEntryPoint().toString())) {
                    continue;
                }

                DecompileResults result = decompiler.decompileFunction(function, 180, monitor);
                println("--- DECOMPILE " + function.getName() + " @ "
                    + function.getEntryPoint() + " ---");
                if (result.decompileCompleted() && result.getDecompiledFunction() != null) {
                    println(result.getDecompiledFunction().getC());
                } else {
                    println("<decompile failed: " + result.getErrorMessage() + ">");
                }
            }
        }

        println("qshrink_hash=" + target + " hit_count=" + hits);
        decompiler.dispose();
    }
}
