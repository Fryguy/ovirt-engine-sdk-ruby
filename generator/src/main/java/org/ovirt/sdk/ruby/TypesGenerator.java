/*
Copyright (c) 2015-2016 Red Hat, Inc.

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

import static java.util.stream.Collectors.toCollection;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Stream;
import javax.inject.Inject;

import org.ovirt.api.metamodel.concepts.EnumType;
import org.ovirt.api.metamodel.concepts.EnumValue;
import org.ovirt.api.metamodel.concepts.ListType;
import org.ovirt.api.metamodel.concepts.Model;
import org.ovirt.api.metamodel.concepts.Name;
import org.ovirt.api.metamodel.concepts.PrimitiveType;
import org.ovirt.api.metamodel.concepts.StructMember;
import org.ovirt.api.metamodel.concepts.StructType;
import org.ovirt.api.metamodel.concepts.Type;

/**
 * This class is responsible for generating the classes that represent the types of the model.
 */
public class TypesGenerator implements RubyGenerator {
    // The directory were the output will be generated:
    protected File out;

    // Reference to the objects used to generate the code:
    @Inject private RubyNames rubyNames;

    // The buffer used to generate the Ruby code:
    private RubyBuffer buffer;

    public void setOut(File newOut) {
        out = newOut;
    }

    public void generate(Model model) {
        // Calculate the file name:
        String fileName = rubyNames.getModulePath() + "/types";
        buffer = new RubyBuffer();
        buffer.setFileName(fileName);

        // Generate the source:
        generateSource(model);

        // Write the file:
        try {
            buffer.write(out);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Error writing types file \"" + fileName + "\"", exception);
        }
    }

    private void generateSource(Model model) {
        // Begin module:
        buffer.addLine("##");
        buffer.addLine("# These forward declarations are required in order to avoid circular dependencies.");
        buffer.addLine("#");
        buffer.beginModule(rubyNames.getModuleName());
        buffer.addLine();

        // The declarations of the types need to appear in inheritance order, otherwise some symbols won't be
        // defined and that will produce errors. To order them correctly we need first to sort them by name, and
        // then sort again so that bases are before extensions.
        Deque<StructType> pending = model.types()
            .filter(StructType.class::isInstance)
            .map(StructType.class::cast)
            .sorted()
            .collect(toCollection(ArrayDeque::new));
        Deque<StructType> sorted = new ArrayDeque<>(pending.size());
        while (!pending.isEmpty()) {
            StructType current = pending.removeFirst();
            StructType base = (StructType) current.getBase();
            if (base == null || sorted.contains(base)) {
                sorted.addLast(current);
            }
            else {
                pending.addLast(current);
            }
        }

        // Generate the forward declarations using the order calculated in the previous step:
        sorted.forEach(x -> {
            generateClassDeclaration(x);
            buffer.addLine("end");
            buffer.addLine();
        });

        // Generate the complete declarations, using the same order:
        sorted.forEach(this::generateType);

        // End module:
        buffer.endModule(rubyNames.getModuleName());
        buffer.addLine();

    }

    private void generateType(Type type) {
        if (type instanceof StructType) {
            generateStruct((StructType) type);
        }
        if (type instanceof EnumType) {
            generateEnum((EnumType) type);
        }
    }

    private void generateStruct(StructType type) {
        // Begin class:
        generateClassDeclaration(type);
        buffer.addLine();

        // Attributes and links:
        type.declaredAttributes().sorted().forEach(this::generateMember);
        type.declaredLinks().sorted().forEach(this::generateMember);

        // Constructor with a named parameter for each attribute:
        buffer.addLine("def initialize(opts = {})");
        buffer.addLine(  "super(opts)");
        Stream.concat(type.declaredAttributes(), type.declaredLinks())
             .map(StructMember::getName)
             .map(rubyNames::getMemberStyleName)
             .sorted()
             .map(x -> String.format("self.%1$s = opts[:%1$s]", x))
             .forEach(buffer::addLine);
        buffer.addLine("end");
        buffer.addLine();

        // End class:
        buffer.addLine("end");
        buffer.addLine();
    }

    private void generateMember(StructMember member) {
        generateGetter(member);
        generateSetter(member);
    }

    private void generateGetter(StructMember member) {
        Name name = member.getName();
        Type type = member.getType();
        String property = rubyNames.getMemberStyleName(name);
        if (type instanceof PrimitiveType) {
            Model model = type.getModel();
            if (type == model.getBooleanType()) {
                buffer.addLine("##");
                buffer.addLine("# Returns the boolean value.");
                buffer.addLine("#");
            }
            else if (type == model.getStringType()) {
                buffer.addLine("##");
                buffer.addLine("# Returns the string value.");
                buffer.addLine("#");
            }
            else if (type == model.getIntegerType()) {
                buffer.addLine("##");
                buffer.addLine("# Returns the Integer value.");
                buffer.addLine("#");
            }
            else if (type == model.getDecimalType()) {
                buffer.addLine("##");
                buffer.addLine("# Returns the float value.");
                buffer.addLine("#");
            }
            else if (type == model.getDateType()) {
                buffer.addLine("##");
                buffer.addLine("# Returns the DateTime value.");
                buffer.addLine("#");
            }
        }
        else if (type instanceof ListType) {
            ListType listType = (ListType) type;
            Type elementType = listType.getElementType();
            RubyName elementTypeName = rubyNames.getTypeName(elementType);
            buffer.addLine("##");
            buffer.addLine("# Returns an array of objects of type %1$s.", elementTypeName);
            buffer.addLine("#");
        }
        else if (type instanceof EnumType || type instanceof StructType) {
            RubyName typeName = rubyNames.getTypeName(type);
            buffer.addLine("##");
            buffer.addLine("# Returns the %1$s value.", typeName);
            buffer.addLine("#");
        }
        buffer.addLine("def %1$s", property);
        buffer.addLine(  "return @%1$s", property);
        buffer.addLine("end");
        buffer.addLine();
    }

    private void generateSetter(StructMember member) {
        Name name = member.getName();
        Type type = member.getType();
        String property = rubyNames.getMemberStyleName(name);
        if (type instanceof PrimitiveType) {
            Model model = type.getModel();
            if (type == model.getBooleanType()) {
                buffer.addLine("##");
                buffer.addLine("# Sets the boolean value.");
                buffer.addLine("#");
            }
            else if (type == model.getStringType()) {
                buffer.addLine("##");
                buffer.addLine("# Sets the string value.");
                buffer.addLine("#");
            }
            else if (type == model.getIntegerType()) {
                buffer.addLine("##");
                buffer.addLine("# Sets the Integer value.");
                buffer.addLine("#");
            }
            else if (type == model.getDecimalType()) {
                buffer.addLine("##");
                buffer.addLine("# Sets the float value.");
                buffer.addLine("#");
            }
            else if (type == model.getDateType()) {
                buffer.addLine("##");
                buffer.addLine("# Sets the DateTime value.");
                buffer.addLine("#");
            }
            buffer.addLine("def %1$s=(value)", property);
            buffer.addLine(  "@%1$s = value", property);
            buffer.addLine("end");
        }
        else if (type instanceof EnumType) {
            RubyName typeName = rubyNames.getTypeName(type);
            buffer.addLine("##");
            buffer.addLine("# Sets the %1$s value.", typeName);
            buffer.addLine("#");
            buffer.addLine("attr_writer :%1$s", property);
        }
        else if (type instanceof StructType) {
            RubyName typeName = rubyNames.getTypeName(type);
            buffer.addLine("##");
            buffer.addLine("# Sets the %1$s value.", typeName);
            buffer.addLine("#");
            buffer.addLine("# The `object` can be an instance of %1$s or a hash.", typeName);
            buffer.addLine("# If it is a hash then a new instance will be created passing the hash as the ");
            buffer.addLine("# `opts` parameter to the constructor.");
            buffer.addLine("#");
            buffer.addLine("def %1$s=(object)", property);
            buffer.addLine(  "if object.is_a?(Hash)");
            buffer.addLine(    "object = %1$s.new(object)", typeName.getClassName());
            buffer.addLine(  "end");
            buffer.addLine(  "@%1$s = object", property);
            buffer.addLine("end");
        }
        else if (type instanceof ListType) {
            ListType listType = (ListType) type;
            Type elementType = listType.getElementType();
            if (elementType instanceof PrimitiveType) {
                Model model = elementType.getModel();
                if (elementType == model.getBooleanType()) {
                    buffer.addLine("##");
                    buffer.addLine("# Sets the boolean values.");
                    buffer.addLine("#");
                }
                else if (elementType == model.getStringType()) {
                    buffer.addLine("##");
                    buffer.addLine("# Sets the string values.");
                    buffer.addLine("#");
                }
                else if (elementType == model.getIntegerType()) {
                    buffer.addLine("##");
                    buffer.addLine("# Sets the Integer values.");
                    buffer.addLine("#");
                }
                else if (elementType == model.getDecimalType()) {
                    buffer.addLine("##");
                    buffer.addLine("# Sets the float values.");
                    buffer.addLine("#");
                }
                else if (elementType == model.getDateType()) {
                    buffer.addLine("##");
                    buffer.addLine("# Sets the DateTime values.");
                    buffer.addLine("#");
                }
                buffer.addLine("def %1$s=(list)", property);
                buffer.addLine(  "@%1$s = list", property);
                buffer.addLine("end");
            }
            else if (elementType instanceof EnumType) {
                RubyName elementTypeName = rubyNames.getTypeName(elementType);
                buffer.addLine("##");
                buffer.addLine("# Sets the %1$s values.", elementTypeName);
                buffer.addLine("#");
                buffer.addLine("def %1$s=(list)", property);
                buffer.addLine(  "@%1$s = list", property);
                buffer.addLine("end");
            }
            else if (elementType instanceof StructType) {
                RubyName elementTypeName = rubyNames.getTypeName(elementType);
                buffer.addLine("##");
                buffer.addLine("# Sets the values from a list or array of objects of type %1$s.", elementTypeName);
                buffer.addLine("#");
                buffer.addLine("def %1$s=(list)", property);
                buffer.addLine(  "if list.class == Array");
                buffer.addLine(    "list = List.new(list)");
                buffer.addLine(    "list.each_with_index do |value, index|");
                buffer.addLine(      "if value.is_a?(Hash)");
                buffer.addLine(        "list[index] = %1$s.new(value)", elementTypeName.getClassName());
                buffer.addLine(      "end");
                buffer.addLine(    "end");
                buffer.addLine(  "end");
                buffer.addLine(  "@%1$s = list", property);
                buffer.addLine("end");
            }
        }
        buffer.addLine();
    }

    private void generateEnum(EnumType type) {
        // Begin module:
        RubyName typeName = rubyNames.getTypeName(type);
        buffer.beginModule(typeName.getClassName());

        // Values:
        type.values().sorted().forEach(this::generateEnumValue);

        // End module:
        buffer.endModule(typeName.getClassName());
    }

    private void generateEnumValue(EnumValue value) {
        String constantName = rubyNames.getConstantStyleName(value.getName());
        String constantValue = rubyNames.getMemberStyleName(value.getName());
        buffer.addLine("%s = '%s'", constantName, constantValue);
    }

    private void generateClassDeclaration(StructType type) {
        RubyName typeName = rubyNames.getTypeName(type);
        Type base = type.getBase();
        RubyName baseName = base != null? rubyNames.getTypeName(base): rubyNames.getBaseStructName();
        buffer.addLine("class %1$s < %2$s", typeName.getClassName(), baseName.getClassName());
    }
}

