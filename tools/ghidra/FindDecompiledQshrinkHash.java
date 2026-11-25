// Find QShrink hashes after the decompiler has propagated Hexagon immediates.
// Instruction operands often contain only fragments of the 32-bit argument,
// so FindQshrinkHash.java can miss them.  This slower pass checks constants in
// the decompiler's high P-code instead.

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;
import ghidra.program.model.pcode.HighFunction;
import ghidra.program.model.pcode.PcodeOpAST;
import ghidra.program.model.pcode.Varnode;

public class FindDecompiledQshrinkHash extends GhidraScript {
    private static final long HASH_MASK = 0xfffffL;

    @Override
    protected void run() throws Exception {
        String[] arguments = getScriptArgs();
        if (arguments.length != 1) {
            printerr("usage: FindDecompiledQshrinkHash.java <decimal-or-hex-hash>");
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

        int checked = 0;
        int hits = 0;
        FunctionIterator functions = currentProgram.getFunctionManager().getFunctions(true);
        while (functions.hasNext() && !monitor.isCancelled()) {
            Function function = functions.next();
            checked++;
            DecompileResults result = decompiler.decompileFunction(function, 20, monitor);
            if (!result.decompileCompleted()) continue;
            HighFunction high = result.getHighFunction();
            if (high == null) continue;

            boolean matchedFunction = false;
            java.util.Iterator<PcodeOpAST> operations = high.getPcodeOps();
            while (operations.hasNext()) {
                PcodeOpAST operation = operations.next();
                for (int i = 0; i < operation.getNumInputs(); i++) {
                    Varnode input = operation.getInput(i);
                    if (input == null || !input.isConstant()) continue;
                    long value = input.getOffset() & 0xffffffffL;
                    if (value < 0x10000000L || ((value >>> 3) & HASH_MASK) != target)
                        continue;
                    hits++;
                    matchedFunction = true;
                    println("hit_function=" + function.getName()
                        + " entry=" + function.getEntryPoint()
                        + " op=" + operation.getMnemonic()
                        + " op_address=" + operation.getSeqnum().getTarget()
                        + " input=" + i
                        + " scalar=0x" + Long.toHexString(value));
                }
            }
            if (matchedFunction && result.getDecompiledFunction() != null) {
                println("--- DECOMPILE " + function.getName() + " @ "
                    + function.getEntryPoint() + " ---");
                println(result.getDecompiledFunction().getC());
            }
            if (checked % 1000 == 0) println("progress_functions=" + checked);
        }
        println("qshrink_hash=" + target + " functions_checked=" + checked
            + " hit_count=" + hits);
        decompiler.dispose();
    }
}
