

public class Instruction {
    public String binString;
    public String[] sepStrings;

    public int valid;
    public int asInt;
    public int opcode;
    public int rs;
    public int rt;
    public int rd;
    public int sa;
    public int func;
    public int immd;
    public int j;
    public int memoryAddress;

    public Opcode opcodeType;

    public Instruction(String binString, int memoryAddress) {
        this.binString = binString;
        this.sepStrings = splitMipsCommand(binString);
        this.memoryAddress = memoryAddress;

        this.valid = binToDec(sepStrings[0]);
        this.asInt = binToDec(binString, true);
        this.opcode = binToDec(sepStrings[0] +sepStrings[1]);
        this.rs = binToDec(sepStrings[2]);
        this.rt = binToDec(sepStrings[3]);
        this.rd = binToDec(sepStrings[4]);
        this.sa = binToDec(sepStrings[5]);
        this.func = binToDec(sepStrings[6]);
        this.immd = binToDec(sepStrings[7], true);
        this.j = binToDec(sepStrings[8]) << 2;
    }

    public static int binToDec(String binstr, boolean canBeNegative) {
        boolean isNegative = false;

        // Two's Complement
        if (binstr.charAt(0) == '1' && canBeNegative){
            binstr = binstr.replaceAll("0", "x");
            binstr = binstr.replaceAll("1", "0");
            binstr = binstr.replaceAll("x", "1");
            isNegative = true;
        }

        double temp = 0;

        for (int i = 0; i < binstr.length(); i++){
            if (binstr.charAt(i) == '1'){
                int len = binstr.length() - 1 - i;
                temp += Math.pow(2, len);
            }
        }

        // Finish Two's Complement
        if (isNegative) {
            temp = -(temp + 1);
        }

        return (int) temp;
    }

    public static int binToDec(String binString){
        return binToDec(binString, false);
    }

    public static String[] splitMipsCommand(String byteString) {
        String validInstruction = byteString.substring(0, 1);
        String opCode = byteString.substring(1, 6);
        String rs = byteString.substring(6, 11);
        String rt = byteString.substring(11, 16);
        String rd = byteString.substring(16, 21);
        String sa = byteString.substring(21, 26);
        String funct = byteString.substring(26, 32);
        String immd = byteString.substring(16);
        String j = byteString.substring(5);

        return new String[]{validInstruction, opCode, rs, rt, rd, sa, funct, immd, j};
    }
}
