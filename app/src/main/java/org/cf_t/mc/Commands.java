package org.cf_t.mc;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class Commands {

    public static LiteralArgumentBuilder<Object> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static <T> RequiredArgumentBuilder<Object, T> argument(
            String name,
            com.mojang.brigadier.arguments.ArgumentType<T> type) {

        return RequiredArgumentBuilder.argument(name, type);
    }
}
