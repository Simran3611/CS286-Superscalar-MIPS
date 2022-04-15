all:
	javac -d . Main.java Instruction.java Opcode.java
	jar --create --file mipssim.jar --main-class=Main Main.class Main\$$1.class Instruction.class Opcode.class