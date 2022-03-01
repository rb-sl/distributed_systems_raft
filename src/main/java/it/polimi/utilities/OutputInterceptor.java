package it.polimi.utilities;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Class used to intercept output and print logs of multiple server on separated columns
 */
public class OutputInterceptor extends PrintStream {
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    
    private final String color;
    private final Integer tabSize = 15;
    private final Integer tabs;

    public OutputInterceptor(OutputStream out, boolean err, Integer tabs) {
        super(out, true);
        this.color = err ? ANSI_RED : "";
        this.tabs = tabs;
    }

    /**
     * Prints the String s but add a padding at the start
     * @param s The string to print
     */
    @Override
    public void println(String s) {
        super.print(color + ("\t".repeat(tabSize) + "| ").repeat(tabs) + s + "\n" + ANSI_RESET);
    }
}
