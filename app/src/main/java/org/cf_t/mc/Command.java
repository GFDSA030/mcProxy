package org.cf_t.mc;

import java.io.IOException;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class Command {

    static Terminal terminal;
    static LineReader reader;

    public static void init() throws IOException {
        terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

    }

    public static void out(String s) {
        reader.printAbove(s);
        // terminal.writer().println(s);
        // terminal.writer().flush();
    }

    public static String in() {
        return reader.readLine("> ");
    }

}
