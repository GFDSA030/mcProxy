package org.cf_t.mc;

import java.io.IOException;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class Command {

    private static Terminal terminal;
    private static LineReader reader;

    public static void init() throws IOException {
        terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }

    public static void out(Object s) {
        if (reader != null) {
            reader.printAbove(s.toString());
        }
    }

    public static String in() {
        return reader.readLine("> ");
    }

    public static void close() throws IOException {
        if (terminal != null) {
            terminal.close();
        }
    }
}
