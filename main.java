import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {

    private static final int MAX_REGISTERS = 32;

    public static int[] registers = new int[MAX_REGISTERS];
    public static ArrayList<Integer> dataAddresses = new ArrayList<>();
    public static Map<Integer, Integer> data = new HashMap<>();
    public static Map<Integer, Instruction> instructions = new HashMap<>();

    public static void main(String[] args) {
        // ARGS: -i, "filename.bin", -o, "out_name"
        String inputFile = args[1];
        String outputFilePrefix = args[3];

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

        System.out.println("======================");
        System.out.println("      Simulation      ");
        System.out.println("======================");

        FileWriter simFileWriter = getFileWriter(outputFilePrefix + "_sim.txt");

        boolean isJumping = false;
        boolean endLoop = false;

        int simMemoryAddress = 96;
        int cycle = 1;

        // i is just for checking for infinite loops
        int HARD_STOP_CYCLE_LIMIT = 1000;
        int i = 0;

        while (!endLoop){

            if (i >= HARD_STOP_CYCLE_LIMIT){
                System.out.println("----------ENDLESS LOOP: SHUTTING DOWN---------");
                System.exit(-1);
            }

            Instruction inst = instructions.get(simMemoryAddress);

            if (inst == null){
                i++;
                simMemoryAddress += 4;
                continue;
            }

            printAndWrite(simFileWriter, "====================\n");
            printAndWrite(simFileWriter, String.format("cycle: %s %s\t", cycle, inst.memoryAddress));

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

            printAndWrite(simFileWriter, "registers:\n");
            printAndWrite(simFileWriter, createRegisterString());
            printAndWrite(simFileWriter, "\n");

            printAndWrite(simFileWriter, "data:");
            printAndWrite(simFileWriter, createDataString());
            printAndWrite(simFileWriter, "\n");



            cycle++;
            i++;

            // we don't want to add 4 to the jump address and mess stuff up
            if (!isJumping){
                simMemoryAddress += 4;
            } else {
                isJumping = false;
            }


        }

        try {
            disFileWriter.close();
            simFileWriter.close();
        } catch (IOException e){
            System.out.println("Error closing file");
            System.exit(-1);
        }
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
}


