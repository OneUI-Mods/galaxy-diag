// Decompile selected addresses from an already analysed Ghidra program.
// Pass addresses as script arguments, for example:
//   -postScript DecompileAddresses.java bcdd7c60

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;

public class DecompileAddresses extends GhidraScript {
    @Override
    protected void run() throws Exception {
        DecompInterface decompiler = new DecompInterface();
        decompiler.toggleCCode(true);
        decompiler.toggleSyntaxTree(true);
        if (!decompiler.openProgram(currentProgram)) {
            printerr("Decompiler initialization failed: " + decompiler.getLastMessage());
            return;
        }

        for (String argument : getScriptArgs()) {
            Address address = currentProgram.getAddressFactory()
                .getDefaultAddressSpace().getAddress(argument);
            Function function = currentProgram.getFunctionManager().getFunctionContaining(address);
            println("\n=== " + argument + " ===");
            if (function == null) {
                println("<no containing function>");
                continue;
            }
            println("function=" + function.getName() + " entry=" + function.getEntryPoint());
            DecompileResults result = decompiler.decompileFunction(function, 180, monitor);
            if (result.decompileCompleted() && result.getDecompiledFunction() != null) {
                println(result.getDecompiledFunction().getC());
            } else {
                println("<decompile failed: " + result.getErrorMessage() + ">");
            }
        }
        decompiler.dispose();
    }
}
