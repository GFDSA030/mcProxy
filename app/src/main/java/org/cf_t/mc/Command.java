package org.cf_t.mc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jline.builtins.Completers.Completer;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class Command {

    private static Terminal terminal;
    private static LineReader reader;
    // private static List<String> commands = List.of("");
     private static   ArrayList<String> commands = new ArrayList<>();

    public static void init() throws IOException {
        terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

        Completer completer = new Completer(null) {
            @Override
            public void complete(LineReader reader, ParsedLine line,
                    List<Candidate> candidates) {

                String word = line.word();

                for (String command : commands) {
                    if (command.startsWith(word)) {
                        candidates.add(new Candidate(command));
                    }
                }
            }
        };

        reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .build();
    }

    public static void addPathComp(String c) {
        commands.add(c);
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
