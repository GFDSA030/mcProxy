package org.example;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class Command {

    Terminal terminal;
    LineReader reader;
    // String Log;
    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    public void init() throws IOException {
        terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();

        POOL.execute(() -> {
            try {
                CommandLoop();
            } catch (IOException ex) {
                System.getLogger(Command.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        });
    }

    public void out(String s) {
        reader.printAbove(s);
    }

    // public String in() {
    //     return reader.readLine("> ");
    // }
    public void CommandLoop() throws IOException {

        while (true) {
            String command = reader.readLine("> ");

            if (command.equals("exit")) {
                break;
            }

            terminal.writer().println("入力されたコマンド: " + command);
            terminal.writer().flush();
        }

        terminal.close();
    }
}
