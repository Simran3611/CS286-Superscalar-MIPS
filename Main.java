import java.io.*;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class Main {

    private static final int MAX_REGISTERS = 32;
    private static final int PRE_ISSUE_SIZE = 4;
    private static final int PRE_SIZE = 2;
    private static final int POST_SIZE = 1;

    public static int[] registers = new int[MAX_REGISTERS];
    public static ArrayList<Integer> dataAddresses = new ArrayList<>();
    public static Map<Integer, Integer> data = new HashMap<>();
    public static Map<Integer, Instruction> instructions = new HashMap<>();
    public static Queue<Instruction> preIssueBuffer = new LinkedBlockingQueue<>(PRE_ISSUE_SIZE);
    public static Queue<Instruction> preALU = new LinkedBlockingQueue<>(PRE_SIZE);
    public static Queue<Instruction> preMem = new LinkedBlockingQueue<>(PRE_SIZE);
    public static Queue<Instruction> postMem = new LinkedBlockingQueue<>(POST_SIZE);
    public static Queue<Instruction> postALU = new LinkedBlockingQueue<>(POST_SIZE);

    public static int programCounter = 96;
    public static boolean procStalled = false;
    public static boolean programBreaked = false;

    public static void main(String[] args) {
        // ARGS: -i, "filename.bin", -o, "out_name"
//        String inputFile = args[1];
//        String outputFilePrefix = args[3];
        String inputFile = "t1.bin";
        String outputFilePrefix = "t1.pipeline";

        disassembly(inputFile, outputFilePrefix);
        pipeline(outputFilePrefix);
    }

    public static void disassembly(String inputFile, String outputFilePrefix){
        byte[] bytes = readBinaryFile(inputFile);
        int memoryAddress = 96;

        String[] bytes32 = getBytesAs32Bits(bytes);

        boolean reachedBreak = false;

        FileWriter disFileWriter = getFileWriter(outputFilePrefix  + "_dis.txt");

        System.out.println("======================");
        System.out.println("      Disassembly     ");
        System.out.println("======================");

        // first loop (disassembly)
        for (String word : bytes32) {
            boolean isInvalid = false;

            if (reachedBreak){
                int dataValue = Instruction.binToDec(word, true);
                System.out.printf("%s\t    %s\t %s", word, memoryAddress, dataValue);
                writeToFile(disFileWriter, String.format("%s\t    %s\t %s", word, memoryAddress, dataValue));

                data.put(memoryAddress, dataValue);
                dataAddresses.add(memoryAddress);
            } else {
                Instruction inst = new Instruction(word, memoryAddress);

                printAndWrite(disFileWriter, createMipsCommandString(inst.sepStrings));
                printAndWrite(disFileWriter, String.format(" %s\t", memoryAddress));

                if (inst.valid == 0) {
                    printAndWrite(disFileWriter, " Invalid Instruction");
                    isInvalid = true;
                }
                // nop will look like this: 10000000 00000000 00000000 00000000 which equals the min integer value
                else if (inst.asInt == Integer.MIN_VALUE){
                    printAndWrite(disFileWriter, " NOP");
                    inst.opcodeType = Opcode.NOP;
                }
                else if (inst.opcode == 40) {
                    printAndWrite(disFileWriter, String.format(" ADDI\t R%s, R%s, #%s", inst.rt, inst.rs, inst.immd));
                    inst.opcodeType = Opcode.ADDI;
                }
                else if (inst.opcode == 43) {
                    printAndWrite(disFileWriter, String.format(" SW  \t R%s, %s(R%s)", inst.rt, inst.immd, inst.rs));
                    inst.opcodeType = Opcode.SW;
                }
                else if (inst.opcode == 32 && inst.func == 0) {
                    // SLL Command
                    printAndWrite(disFileWriter, String.format(" SLL\t R%s, R%s, #%s", inst.rd, inst.rt, inst.sa));
                    inst.opcodeType = Opcode.SLL;
                    //SLL	R10, R1, #2
                }
                else if (inst.opcode == 32 && inst.func == 2){
                    printAndWrite(disFileWriter, String.format(" SRL\t R%s, R%s, #%s", inst.rd, inst.rt, inst.sa));
                    inst.opcodeType = Opcode.SRL;
                }
                else if (inst.opcode == 32 && inst.func == 34){
                    printAndWrite(disFileWriter, String.format(" SUB \t R%s, R%s, R%s", inst.rd, inst.rs, inst.rt));
                    inst.opcodeType = Opcode.SUB;
                }
                else if (inst.opcode == 32 && inst.func == 32){
                    printAndWrite(disFileWriter, String.format(" ADD \t R%s, R%s, R%s", inst.rd, inst.rs, inst.rt));
                    inst.opcodeType = Opcode.ADD;
                }
                else if (inst.opcode == 35) {
                    printAndWrite(disFileWriter, String.format(" LW  \t R%s, %s(R%s)", inst.rt, inst.immd, inst.rs));
                    inst.opcodeType = Opcode.LW;
                }
                else if (inst.opcode == 34) {
                    printAndWrite(disFileWriter, String.format(" J  \t #%s", inst.j));
                    inst.opcodeType = Opcode.J;
                }
                else if (inst.opcode == 33) {
                    inst.immd = inst.immd << 2;
                    printAndWrite(disFileWriter, String.format(" BLTZ\t R%s, #%s", inst.rs, inst.immd));
                    inst.opcodeType = Opcode.BLTZ;
                }
                else if (inst.opcode == 32 && inst.func == 8){
                    printAndWrite(disFileWriter, String.format(" JR  \t R%s", inst.rs));
                    inst.opcodeType = Opcode.JR;
                }
                else if (inst.opcode == 32 && inst.func == 13){
                    printAndWrite(disFileWriter," BREAK");
                    inst.opcodeType = Opcode.BREAK;
                    reachedBreak = true;
                }
                else if (inst.opcode == 60){
                    printAndWrite(disFileWriter, String.format(" MUL \t R%s, R%s, R%s", inst.rd, inst.rs, inst.rt));
                    inst.opcodeType = Opcode.MUL;
                }
                else if (inst.opcode == 32 && inst.func == 10){
                    printAndWrite(disFileWriter, String.format(" MOVZ\t R%s, R%S, R%s", inst.rd, inst.rs, inst.rt));
                    inst.opcodeType = Opcode.MOVZ;
                }

                if (inst.opcodeType == null){
                    inst.opcodeType = Opcode.ERROR;
                }

                if (!isInvalid){
                    instructions.put(memoryAddress, inst);
                }
            }

            System.out.println();
            writeToFile(disFileWriter, "\n");

            memoryAddress += 4;
        }

        try {
            disFileWriter.close();
        } catch (IOException e){
            System.out.println("Error closing disassembly output file");
            System.exit(-1);
        }
    }

    public static void pipeline(String outputFilePrefix){
        FileWriter pipelineWriter = getFileWriter(outputFilePrefix + "_pipeline.txt");

        boolean isJumping = false;
        boolean endLoop = false;

        int cycle = 1;

        // i is just for checking for infinite loops
        int HARD_STOP_CYCLE_LIMIT = 1000;
        int i = 0;

        while (!endLoop) {

            if (i >= HARD_STOP_CYCLE_LIMIT) {
                System.out.println("----------ENDLESS LOOP: SHUTTING DOWN---------");
                System.exit(-1);
            }

            /*
            Instruction inst = instructions.get(programCounter);

            if (inst == null){
                i++;
                programCounter += 4;
                continue;
            }*/

            printAndWrite(pipelineWriter, "--------------------");
            printAndWrite(pipelineWriter, String.format("\nCycle: %s\t", cycle));

            WB();
            Mem();
            ALU();
            Issue();
            InstructionFetch();

            printAndWrite(pipelineWriter, "\nPre-Issue Buffer:\n");
            printAndWrite(pipelineWriter, createBufferString(preIssueBuffer, PRE_ISSUE_SIZE));
            printAndWrite(pipelineWriter, "Pre_ALU Queue:\n");
            printAndWrite(pipelineWriter, createBufferString(preALU, PRE_SIZE));
            printAndWrite(pipelineWriter, "Post_ALU Queue:\n");
            printAndWrite(pipelineWriter, createBufferString(postALU, POST_SIZE));
            printAndWrite(pipelineWriter, "Pre_MEM Queue:\n");
            printAndWrite(pipelineWriter, createBufferString(preMem, PRE_SIZE));
            printAndWrite(pipelineWriter, "Post_MEM Queue:\n");
            printAndWrite(pipelineWriter, createBufferString(postMem, POST_SIZE));

            /*
            switch (inst.opcodeType){
                case ADD:
                    printAndWrite(simFileWriter, String.format(" ADD \t R%s, R%s, R%s", inst.rd, inst.rs, inst.rt));
                    registers[inst.rd] = registers[inst.rs] + registers[inst.rt];
                    break;
                case SUB:
                    printAndWrite(simFileWriter, String.format(" SUB \t R%s, R%s, R%s", inst.rd, inst.rs, inst.rt));
                    registers[inst.rd] = registers[inst.rs] - registers[inst.rt];
                    break;
                case ADDI:
                    printAndWrite(simFileWriter, String.format(" ADDI\t R%s, R%s, #%s", inst.rt, inst.rs, inst.immd));
                    registers[inst.rt] = registers[inst.rs] + inst.immd;
                    break;
                case SW:
                    printAndWrite(simFileWriter, String.format(" SW  \t R%s, %s(R%s)", inst.rt, inst.immd, inst.rs));
                    // offset + base (in a register)
                    int dataAddress = inst.immd + registers[inst.rs];
                    data.replace(dataAddress, registers[inst.rt]);
                    break;
                case LW:
                    printAndWrite(simFileWriter, String.format(" LW  \t R%s, %s(R%s)", inst.rt, inst.immd, inst.rs));
                    int lwDataAddress = inst.immd + registers[inst.rs];
                    registers[inst.rt] = data.get(lwDataAddress);
                    break;
                case SLL:
                    printAndWrite(simFileWriter, String.format(" SLL\t R%s, R%s, #%s", inst.rd, inst.rt, inst.sa));
                    registers[inst.rd] = registers[inst.rt] << inst.sa;
                    break;
                case SRL:
                    printAndWrite(simFileWriter, String.format(" SRL\t R%s, R%s, #%s", inst.rd, inst.rt, inst.sa));
                    registers[inst.rd] = registers[inst.rt] >> inst.sa;
                    break;
                case J:
                    printAndWrite(simFileWriter, String.format(" J  \t #%s", inst.j));
                    isJumping = true;
                    simMemoryAddress = inst.j;
                    break;
                case JR:
                    printAndWrite(simFileWriter, String.format(" JR  \t R%s", inst.rs));
                    isJumping = true;
                    simMemoryAddress = registers[inst.rs];
                    break;
                case MUL:
                    printAndWrite(simFileWriter, String.format(" MUL \t R%s, R%s, R%s", inst.rd, inst.rs, inst.rt));
                    registers[inst.rd] = registers[inst.rs] * registers[inst.rt];
                    break;
                case BLTZ:
                    printAndWrite(simFileWriter, String.format(" BLTZ\t R%s, #%s", inst.rs, inst.immd));
                    if (registers[inst.rs] < 0){
                        isJumping = true;
                        simMemoryAddress = (simMemoryAddress + 4) + inst.immd;
                    }
                    break;
                case MOVZ:
                    printAndWrite(simFileWriter, String.format(" MOVZ\t R%s, R%S, R%s", inst.rd, inst.rs, inst.rt));
                    if (registers[inst.rt] == 0){
                        registers[inst.rd] = registers[inst.rs];
                    }
                    break;
                case NOP:
                    printAndWrite(simFileWriter," NOP");
                    break;
                case BREAK:
                    printAndWrite(simFileWriter," BREAK");
                    endLoop = true;
                    break;
            }

            printAndWrite(simFileWriter, "\n");
            printAndWrite(simFileWriter, "\n");


            */

            printAndWrite(pipelineWriter, "Registers:\n");
            printAndWrite(pipelineWriter, createRegisterString());
            printAndWrite(pipelineWriter, "\n");

            printAndWrite(pipelineWriter, "Data:");
            printAndWrite(pipelineWriter, createDataString());
            printAndWrite(pipelineWriter, "\n");

            if(programBreaked && (getAllIssuedInstructions().size() == 0)) {
                endLoop = true;
            }
            cycle++;
            i++;

            /*
            // we don't want to add 4 to the jump address and mess stuff up
            if (!isJumping){
                simMemoryAddress += 4;
            } else {
                isJumping = false;
            }*/
        }

        try {
            pipelineWriter.close();
        } catch (IOException e){
            System.out.println("Error closing pipeline output file");
            System.exit(-1);
        }
    }

    private static String createBufferString(Queue<Instruction> buffer, int maxSize) {
        String temp = "";
        Instruction[] bufferedInstructions = buffer.toArray(new Instruction[0]);
        for (int i = 0; i < maxSize; i++){
            try {
                Instruction instruction = bufferedInstructions[i];
                temp += String.format("\tEntry %s:\t[%s]", i, createReadableMipsInstruction(instruction));
            } catch (IndexOutOfBoundsException e){
                temp += String.format("\tEntry %s:", i);
            }
            temp += "\n";
        }
        return temp;
    }

    public static byte[] readBinaryFile(String filename) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(filename));
            return bytes;
        } catch (IOException ex) {
            System.out.println("Could not read file: " + filename);
            System.exit(-1);
        }

        return null;
    }

    public static FileWriter getFileWriter(String filename){
        try {
            FileWriter writer = new FileWriter(filename);
            return writer;
        } catch (IOException exception){
            System.out.println("Could not create FileWriter for: " + filename);
            System.exit(-1);
        }

        return null;
    }

    public static void writeToFile(FileWriter writer, String writeString){
        try {
            writer.write(writeString);
        } catch (IOException exception){
            System.out.println("Could not write string: " + writeString);
            System.exit(-1);
        }
    }

    public static void printAndWrite(FileWriter writer, String writeString){
        System.out.print(writeString);
        writeToFile(writer, writeString);
    }

    public static String getByteAsBinaryString(byte b) {
        return String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
    }

    public static String[] getBytesAs32Bits(byte[] bytes) {
        String[] bytes32 = new String[bytes.length / 4];
        String temp = "";
        int j = 0;
        int stringArrayIndex = 0;
        for (byte b : bytes) {
            temp += getByteAsBinaryString(b);
            if (j < 3) {
                j++;
            } else {
                bytes32[stringArrayIndex] = temp;
                temp = "";
                j = 0;
                stringArrayIndex++;
            }
        }

        return bytes32;
    }

    public static String createMipsCommandString(String[] mips) {
        String temp = "";
        for (int i = 0; i < 7; i++) {
            temp += (mips[i] + " ");
        }

        return temp;
    }

    public static String createRegisterString(){
        String temp = "";

        temp += String.format("r00:\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t\n",
                registers[0], registers[1], registers[2], registers[3],
                registers[4], registers[5], registers[6], registers[7]
        );

        temp += String.format("r08:\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t\n",
                registers[8], registers[9], registers[10], registers[11],
                registers[12], registers[13], registers[14], registers[15]
        );

        temp += String.format("r16:\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t\n",
                registers[16], registers[17], registers[18], registers[19],
                registers[20], registers[21], registers[22], registers[23]
        );

        temp += String.format("r24:\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t\n",
                registers[24], registers[25], registers[26], registers[27],
                registers[28], registers[29], registers[30], registers[31]
        );

        return temp;
    }

    public static String createDataString(){
        int maxColumns = 8;
        String temp = "";

        for (int i = 0; i < data.size(); i++){
            int dataAddress = dataAddresses.get(i);
            int datum = data.get(dataAddress);

            if (i % maxColumns == 0){
                temp += "\n";
                temp += String.format("%s:\t", dataAddress);
            }

            temp += String.format("%s\t", datum);
        }

        return temp;
    }

    public static void InstructionFetch() {
        // before we fetch an instruction, we have to meet 2 criteria
        // 1. We must not be stalling
        // 2. There must be room in the pre issue buffer
        // 2a. If there is only one slot in pre issue open, we can only fetch one instruction

        if (procStalled){
            return;
        } else if (programBreaked){
            return;
        }

        // fetch as many instructions as we can
        // if we want to implement a cache, this must be restructured

        int instructionsToFetch = Math.min(PRE_ISSUE_SIZE - preIssueBuffer.size(), 2);

        for (int i = 0; i < instructionsToFetch; i++){
            Instruction instruction = instructions.get(programCounter);

            if (instruction == null){
                programCounter = programCounter + 4;
                continue;
            }
            else if (instruction.opcodeType == Opcode.BREAK){
                programBreaked = true;
                return;
            } else if (instruction.opcodeType == Opcode.BLTZ
                    || instruction.opcodeType == Opcode.BEQ
                    || instruction.opcodeType == Opcode.J
                    || instruction.opcodeType == Opcode.JR){
                    procStalled = true;
                // branch logic
            }

            preIssueBuffer.add(instruction);
            programCounter += 4;
        }
    }

    public static void Issue() {
        int instructionsToIssue = Math.min(preIssueBuffer.size(), 2);
        /*
        1. No structural hazards exist (there is room in the pre-mem/pre-ALU destination buffer)
        if (instructionsToIssue != 0) {
            continue;
        }
        else {
            exit;
        }

        2. No WBW hazards exist active instructions (issued but not finished, or
        earlier no-issued instructions)
            output dependency write before write
            Check the previous two instructions and see if their rd register matches the current instructions rd register


        3. No WBR hazards exist with earlier not-issued instructions (do not check
        for WBR hazards with instructions that have already been issued. In
        other words, you only need to check the earlier instructions in the preissue buffer and not in later buffers in the pipeline)
            Antidependency write before read
            Look at the previous 2 instructions and see if rs or rt registers match the rd register of the current instruction

        4. No RBW hazards (true data dependencies) exist with active instructions
        (all operands are ready)
            true data dependency read before write
            Look at the previous 2 registers and see if their rd register matches the current instructions rt or rs register

        5. A load instruction must wait for all previous stores to be issued
            bool isSW = false;
        6. Store instructions must be issued in order
         */
        int instructionsIssued = 0;

        for (int i = 0; i < 4; i++){
            if (instructionsIssued >= instructionsToIssue){
                break;
            }

            Instruction instruction = preIssueBuffer.peek();

            // nothing left in pre-issue
            if (instruction == null){
                break;
            }

            if(isRType(instruction)){
                if (isThereRBWHazard(getAllIssuedInstructions(), new int[]{instruction.rs, instruction.rt})) break;
            }

            if (isIType(instruction)){
                if (isThereRBWHazard(getAllIssuedInstructions(), new int[]{instruction.rs})) break;
            }

            // assuming we are not at the first buffered instructions, check all instructions before this one
            // in the pre issue buffer
            if (i != 0){
                Instruction[] preIssueInstructions = preIssueBuffer.toArray(new Instruction[0]);
                for (int j = 0; j < i; j++){
                    Instruction currentPreIssueInstruction = preIssueInstructions[j];

                    if (isRType(instruction)){
                        // true data dependency in pre-issue
                        if (isThereRBWHazard(currentPreIssueInstruction, new int[]{instruction.rs, instruction.rt})) break;

                        // write before read
                        if (isThereWBRHazard(currentPreIssueInstruction, instruction.rd)) break;

                        // write before write
                        if (isThereWBWHazard(currentPreIssueInstruction, instruction.rd)) break;
                    } else {
                        if (isThereRBWHazard(currentPreIssueInstruction, new int[]{instruction.rs})) break;

                        if (isThereWBWHazard(currentPreIssueInstruction, instruction.rt)) break;

                        if (isThereWBWHazard(currentPreIssueInstruction, instruction.rt)) break;
                    }
                }
            }

        /*
        1. No structural hazards exist (there is room in the pre-mem/pre-ALU destination buffer)
        if (instructionsToIssue != 0) {
            continue;
        }
        else {
            exit;
        }


         */
            boolean isSW = false;
            // We need to also check for LW and SW
            // LWs cannot be issued until all SWs before it have been issue
            // SWs must be sequential
            switch (instruction.opcodeType){
                case SW:
                    //checks whether SW happened;
                    isSW = true;
                case LW:
                    if (isSW) {
                        isSW = false;
                        continue;
                    }
                    else if (preMem.size() < PRE_SIZE){
                        preMem.add(instruction);
                        preIssueBuffer.poll();
                        instructionsIssued++;
                    }
                    break;
                default:
                    if (preALU.size() < PRE_SIZE){
                        preALU.add(instruction);
                        preIssueBuffer.poll();

                        instructionsIssued++;
                    }
                    break;
            }
        }
    }

    public static void Mem() {
        if(preMem.peek() != null) {
            Instruction preMemValue = preMem.poll();
            switch (preMemValue.opcodeType) {
                case SW:
                    // cache logic possibly
                    break;
                case LW:
                    postMem.add(preMemValue);
                    break;
            }
        }
    }

    public static void ALU() {
        if(preALU.peek() != null) {
            Instruction preALUValue = preALU.poll();
            postALU.add(preALUValue);
        }
    }

    public static void WB() {
        if(postALU.peek() != null) {
            Instruction postALUValue = postALU.poll();
            switch (postALUValue.opcodeType) {
                //fix the changing of the register values as they probably will be changed in the issue stage.
                case ADD:
                    registers[postALUValue.rd] = registers[postALUValue.rs] + registers[postALUValue.rt];
                    break;
                case SUB:
                    registers[postALUValue.rd] = registers[postALUValue.rs] - registers[postALUValue.rt];
                    break;
                case ADDI:
                    registers[postALUValue.rt] = registers[postALUValue.rs] + postALUValue.immd;
                    break;
                case SLL:
                    registers[postALUValue.rd] = registers[postALUValue.rt] << postALUValue.sa;
                    break;
                case SRL:
                    registers[postALUValue.rd] = registers[postALUValue.rt] >> postALUValue.sa;
                    break;
                case MUL:
                    registers[postALUValue.rd] = registers[postALUValue.rs] * registers[postALUValue.rt];
                    break;
                case MOVZ:
                    if (registers[postALUValue.rt] == 0) {
                        registers[postALUValue.rd] = registers[postALUValue.rs];
                    }
                    break;
            }
        }
        if(postMem.peek() != null) {
            Instruction postMemValue = postMem.poll();

            if (postMemValue.opcodeType == Opcode.LW){
                registers[postMemValue.rt] = postMemValue.immd + postMemValue.rs;
            }
        }
    }

    public static boolean isRType(Instruction instruction){
        return instruction.opcodeType == Opcode.ADD
                || instruction.opcodeType == Opcode.SUB
                || instruction.opcodeType == Opcode.MUL
                || instruction.opcodeType == Opcode.MOVZ
                || instruction.opcodeType == Opcode.SLL
                || instruction.opcodeType == Opcode.SRL;
    }

    // I think i got everything
    // SW should be ignored as it does not write to a register
    private static boolean isIType(Instruction instruction) {
        return instruction.opcodeType == Opcode.ADDI
                || instruction.opcodeType == Opcode.LW;
    }

    public static List<Instruction> getAllIssuedInstructions(){
        Instruction[] preA = preALU.toArray(new Instruction[0]);
        Instruction[] preM = preMem.toArray(new Instruction[0]);
        Instruction[] postA = postALU.toArray(new Instruction[0]);
        Instruction[] postM = postMem.toArray(new Instruction[0]);

        List<Instruction> issuedInstructions = new ArrayList<>();

        for (Instruction i : preA){
            issuedInstructions.add(i);
        }

        for (Instruction i : preM){
            issuedInstructions.add(i);
        }

        for (Instruction i : postA){
            issuedInstructions.add(i);
        }

        for (Instruction i : postM){
            issuedInstructions.add(i);
        }

        return issuedInstructions;
    }

    public static String createReadableMipsInstruction(Instruction instruction){
        if (instruction.valid == 0){
            return " Invalid Instruction";
        }

        switch (instruction.opcodeType){
            case ADDI:
                return String.format(" ADDI\t R%s, R%s, #%s", instruction.rt, instruction.rs, instruction.immd);
            case NOP:
                return " NOP";
            case SW:
                return String.format(" SW  \t R%s, %s(R%s)", instruction.rt, instruction.immd, instruction.rs);
            case SLL:
                return String.format(" SLL\t R%s, R%s, #%s", instruction.rd, instruction.rt, instruction.sa);
            case SRL:
                return String.format(" SRL\t R%s, R%s, #%s", instruction.rd, instruction.rt, instruction.sa);
            case SUB:
                return String.format(" SUB \t R%s, R%s, R%s", instruction.rd, instruction.rs, instruction.rt);
            case ADD:
                return String.format(" ADD \t R%s, R%s, R%s", instruction.rd, instruction.rs, instruction.rt);
            case LW:
                return String.format(" LW  \t R%s, %s(R%s)", instruction.rt, instruction.immd, instruction.rs);
            case J:
                return String.format(" J  \t #%s", instruction.j);
            case BLTZ:
                return String.format(" BLTZ\t R%s, #%s", instruction.rs, instruction.immd);
            case JR:
                return String.format(" JR  \t R%s", instruction.rs);
            case BREAK:
                return " BREAK";
            case MUL:
                return String.format(" MUL \t R%s, R%s, R%s", instruction.rd, instruction.rs, instruction.rt);
            case MOVZ:
                return String.format(" MOVZ\t R%s, R%S, R%s", instruction.rd, instruction.rs, instruction.rt);
            default:
                return " ERROR";
        }
    }

    public static boolean isThereRBWHazard(List<Instruction> instructionsToCheck, int[] argumentRegisters){
        return isThereRBWHazard(instructionsToCheck.toArray(new Instruction[0]), argumentRegisters);
    }

    public static boolean isThereRBWHazard(Instruction[] instructionsToCheck, int[] argumentRegisters){
        for (Instruction issuedInstructions : instructionsToCheck) {
            // true data dependency (read before write)
            for (int argumentRegister : argumentRegisters){
                if (argumentRegister == issuedInstructions.rd){
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isThereRBWHazard(Instruction instruction, int[] argumentRegisters){
        return isThereRBWHazard(new Instruction[]{instruction}, argumentRegisters);
    }

    public static boolean isThereWBRHazard(Instruction[] instructionsToCheck, int writeRegister){
        for (Instruction instructionToCheck : instructionsToCheck){
            if (isRType(instructionToCheck) || instructionToCheck.opcodeType == Opcode.SW){
                return writeRegister == instructionToCheck.rs || writeRegister == instructionToCheck.rt;
            } else if (isIType(instructionToCheck)){
                return writeRegister == instructionToCheck.rs;
            } else {
                /*
                switch (instructionToCheck.opcodeType){
                    case LW:
                        // load word, rs is an argument register, rt is the write register
                        return writeRegister == instructionToCheck.rs;
                    case SW:
                        // store word, no write register, rs and rt are argument registers
                        return writeRegister == instructionToCheck.rs || writeRegister == instructionToCheck.rt;
                }*/
            }
        }

        return false;
    }

    public static boolean isThereWBRHazard(Instruction instruction, int writeRegister){
        return isThereWBRHazard(new Instruction[]{instruction}, writeRegister);
    }

    public static boolean isThereWBWHazard(Instruction instruction, int writeRegister){
        if (isRType(instruction)){
            return writeRegister == instruction.rd;
        } else if (isIType(instruction) || instruction.opcodeType == Opcode.LW){
            return writeRegister == instruction.rt;
        }

        return false;
    }
}


