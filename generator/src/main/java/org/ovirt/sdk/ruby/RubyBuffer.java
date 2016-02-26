/*
Copyright (c) 2015 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.ovirt.sdk.ruby;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;

import org.apache.commons.io.FileUtils;

/**
 * This class is a buffer intended to simplify generation of Ruby source code. It stores the name of the module, the
 * list of requires and the rest of the source separately, so that requires can be added on demand while generating the
 * rest of the source.
 */
public class RubyBuffer {
    // Reference to the object used to generate Ruby names:
    @Inject private RubyNames rubyNames;

    // The name of the file:
    private String fileName;

    // The things to be required, without the "required" keyword and quotes.
    private Set<String> requires = new HashSet<>();

    // The stack of module names:
    private Deque<String> moduleStack = new ArrayDeque<>();

    // The lines of the body of the class:
    private List<String> lines = new ArrayList<>();

    // The current indentation level:
    private int level;

    /**
     * Sets the file name.
     */
    public void setFileName(String newFileName) {
        fileName = newFileName;
    }

    /**
     * Begins the given module name, which may be separated with {@code ::}, and writes the corresponding {@code module}
     * statements.
     */
    public void beginModule(String moduleName) {
        Arrays.stream(moduleName.split("::")).forEach(x -> {
            moduleStack.push(x);
            addLine("module %1$s", x);
        });
    }

    /**
     * Ends the given module name, which may be separated with {@code ::}, and writes the corresponding {@code end}
     * statements.
     */
    public void endModule(String moduleName) {
        Arrays.stream(moduleName.split("::")).forEach(x -> {
            addLine("end");
            moduleStack.pop();
        });
    }

    /**
     * Adds a line to the body of the file.
     */
    public void addLine(String line) {
        // Check of the line is the begin or end of a block:
        boolean isBegin =
            line.endsWith("(") ||
            line.endsWith("[") ||
            line.endsWith("|") ||
            line.equals("begin") ||
            line.equals("else") ||
            line.equals("ensure") ||
            line.startsWith("case ") ||
            line.startsWith("class ") ||
            line.startsWith("def ") ||
            line.startsWith("if ") ||
            line.startsWith("loop ") ||
            line.startsWith("module ") ||
            line.startsWith("unless ") ||
            line.startsWith("when ") ||
            line.startsWith("while ");
        boolean isEnd =
            line.equals(")") ||
            line.equals("]") ||
            line.equals("else") ||
            line.equals("else") ||
            line.equals("end") ||
            line.equals("ensure") ||
            line.startsWith("when ");

        // Decrease the indentation if the line is the end of a block:
        if (isEnd) {
            if (level > 0) {
                level--;
            }
        }

        // Indent the line and add it to the list:
        StringBuilder buffer = new StringBuilder(level * 2 + line.length());
        for (int i = 0; i < level; i++) {
            buffer.append("  ");
        }
        buffer.append(line);
        line = buffer.toString();
        lines.add(line);

        // Increase the indentation if the line is the begin of a block:
        if (isBegin) {
            level++;
        }
    }

    /**
     * Adds an empty line to the body of the class.
     */
    public void addLine() {
        addLine("");
    }

    /**
     * Adds a formatted line to the body of the class. The given {@code args} are formatted using the
     * provided {@code format} using the {@link String#format(String, Object...)} method.
     */
    public void addLine(String format, Object ... args) {
        StringBuilder buffer = new StringBuilder();
        Formatter formatter = new Formatter(buffer);
        formatter.format(format, args);
        String line = buffer.toString();
        addLine(line);
    }

    /**
     * Generates the complete source code of the class.
     */
    public String toString() {
        StringBuilder buffer = new StringBuilder();

        // License:
        buffer.append("#--\n");
        buffer.append("# Copyright (c) 2015-2016 Red Hat, Inc.\n");
        buffer.append("#\n");
        buffer.append("# Licensed under the Apache License, Version 2.0 (the \"License\");\n");
        buffer.append("# you may not use this file except in compliance with the License.\n");
        buffer.append("# You may obtain a copy of the License at\n");
        buffer.append("#\n");
        buffer.append("#   http://www.apache.org/licenses/LICENSE-2.0\n");
        buffer.append("#\n");
        buffer.append("# Unless required by applicable law or agreed to in writing, software\n");
        buffer.append("# distributed under the License is distributed on an \"AS IS\" BASIS,\n");
        buffer.append("# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n");
        buffer.append("# See the License for the specific language governing permissions and\n");
        buffer.append("# limitations under the License.\n");
        buffer.append("#++\n");
        buffer.append("\n");

        // Require:
        List<String> requiresList = new ArrayList<>(requires);
        Collections.sort(requiresList);
        for (String requiresItem : requiresList) {
            buffer.append("require '");
            buffer.append(requiresItem);
            buffer.append("'\n");
        }
        buffer.append("\n");

        // Body:
        for (String line : lines) {
            buffer.append(line);
            buffer.append("\n");
        }

        return buffer.toString();
    }

    /**
     * Creates a {@code .rb} source file and writes the source. The required intermediate directories will be created
     * if they don't exist.
     *
     * @param dir the base directory for the source code
     * @throws IOException if something fails while creating or writing the file
     */
    public void write(File dir) throws IOException {
        // Calculate the complete fille name:
        File file = new File(dir, fileName.replace('/', File.separatorChar) + ".rb");

        // Create the directory and all its parent if needed:
        File parent = file.getParentFile();
        FileUtils.forceMkdir(parent);

        // Write the file:
        System.out.println("Writing file \"" + file.getAbsolutePath() + "\".");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(toString());
        }
    }
}
