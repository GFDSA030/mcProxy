package org.cf_t.mc;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
// import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class Command {

    private static Terminal terminal;
    private static LineReader reader;

    private static final CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();

    public static void init() throws IOException {

        terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        Completer completer = new Completer() {

            @Override
            public void complete(
                    LineReader reader,
                    ParsedLine line,
                    List<Candidate> candidates) {

                String input = line.line();
                int cursor = line.cursor();

                // SuggestionsBuilder builder = new SuggestionsBuilder(
                //         input,
                //         cursor);

                ParseResults<Object> parse = dispatcher.parse(
                        input,
                        null);

                CompletableFuture<Suggestions> future = dispatcher.getCompletionSuggestions(
                        parse,
                        cursor);

                future.thenAccept(suggestions -> {

                    suggestions.getList().forEach(suggestion -> {

                        String value = suggestion.getText();

                        candidates.add(
                                new Candidate(value));
                    });
                }).join();
            }
        };

        reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .build();
    }

    public static void register(
            LiteralArgumentBuilder<Object> command) {

        dispatcher.register(command);
    }

    public static void out(Object s) {

        if (reader != null) {
            reader.printAbove(s.toString());
        }
    }

    public static String in() {

        return reader.readLine("> ");
    }

    public static void execute(String input) {

        try {
            dispatcher.execute(input, null);

        } catch (CommandSyntaxException e) {
            out("Unknown command");
        } catch (Exception e) {
            out(e.getMessage());
        }
    }

    public static void close() throws IOException {

        if (terminal != null) {
            terminal.close();
        }
    }
}
