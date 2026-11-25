// Ghidra headless post-script for the Samsung/Qualcomm DIAG firmware audit.
// It locates the exact DIAG policy strings, reports all code references, and
// decompiles the containing functions. Run after normal auto-analysis.

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSetView;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.CodeUnit;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.symbol.Reference;

public class DiagStringXrefs extends GhidraScript {
    private static final List<String> TARGETS = Arrays.asList(
        "/nv/item_files/diag/diag_encrypt",
        "diag_encryption_config_init",
        "diag_services_enabled",
        "oemapi_misc_user_log_set",
        "ORANGE authorized flag",
        "Enabling wrapped key log"
    );

    @Override
    protected void run() throws Exception {
        DecompInterface decompiler = new DecompInterface();
        decompiler.toggleCCode(true);
        decompiler.toggleSyntaxTree(true);
        if (!decompiler.openProgram(currentProgram)) {
            printerr("Decompiler initialization failed: " + decompiler.getLastMessage());
            return;
        }

        Memory memory = currentProgram.getMemory();
        AddressSetView initialized = memory.getAllInitializedAddressSet();
        List<Address> doneFunctions = new ArrayList<>();

        for (String target : TARGETS) {
            byte[] needle = (target + "\0").getBytes(StandardCharsets.US_ASCII);
            Address cursor = initialized.getMinAddress();
            println("\n=== STRING: " + target + " ===");
            while (cursor != null) {
                Address hit = memory.findBytes(cursor, needle, null, true, monitor);
                if (hit == null || !initialized.contains(hit)) {
                    break;
                }
                println("string_address=" + hit);
                Iterator<Reference> references = currentProgram.getReferenceManager()
                    .getReferencesTo(hit).iterator();
                int count = 0;
                while (references.hasNext()) {
                    Reference reference = references.next();
                    count++;
                    Address from = reference.getFromAddress();
                    Function function = currentProgram.getFunctionManager().getFunctionContaining(from);
                    println("xref=" + from + " type=" + reference.getReferenceType()
                        + " function=" + (function == null ? "<none>" : function.getName())
                        + " entry=" + (function == null ? "<none>" : function.getEntryPoint()));
                    if (function != null && !doneFunctions.contains(function.getEntryPoint())) {
                        doneFunctions.add(function.getEntryPoint());
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
                println("xref_count=" + count);
                try {
                    cursor = hit.add(needle.length);
                } catch (Exception end) {
                    cursor = null;
                }
            }

            // QShrink debug descriptors are annotated by QDig as comments at
            // their owning code/data address. They can recover a source-line
            // association even when the firmware never loads the format
            // string through a normal pointer reference.
            Iterator<Address> commentAddresses = currentProgram.getListing()
                .getCommentAddressIterator(initialized, true);
            while (commentAddresses.hasNext()) {
                Address address = commentAddresses.next();
                for (int commentType : new int[] {
                    CodeUnit.PLATE_COMMENT,
                    CodeUnit.PRE_COMMENT,
                    CodeUnit.POST_COMMENT,
                    CodeUnit.EOL_COMMENT,
                    CodeUnit.REPEATABLE_COMMENT
                }) {
                    String comment = currentProgram.getListing().getComment(commentType, address);
                    if (comment == null || !comment.contains(target)) {
                        continue;
                    }
                    Function function = currentProgram.getFunctionManager().getFunctionContaining(address);
                    println("comment_match=" + address + " type=" + commentType
                        + " function=" + (function == null ? "<none>" : function.getName())
                        + " entry=" + (function == null ? "<none>" : function.getEntryPoint())
                        + " comment=" + comment.replace('\n', ' '));
                    if (function != null && !doneFunctions.contains(function.getEntryPoint())) {
                        doneFunctions.add(function.getEntryPoint());
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
            }
        }
        decompiler.dispose();
    }
}
