package ru.ifmo.ctddev.maltsev.implementor;


import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * The class implements two interfaces: {@link Impler} and {@link JarImpler}
 * It can create class and/or jar file with implementation of given interface
 *
 * @author Alexander Maltsev
 */
public class Implementor implements Impler, JarImpler {
    /**
     * Entry point of {@link Implementor}
     * <p>
     * Parses arguments and calls necessary subroutine: {@link #implement(Class, Path)} or {@link #implementJar(Class, Path)}
     *
     * @param args arguments to be parsed
     * @throws ImplerException if something goes wrong during implementation
     */
    public static void main(String[] args) throws ImplerException {
        if (args == null || (args.length != 1 && args.length != 3) || Arrays.stream(args).filter(x -> x == null).count() != 0) {
            System.err.print("Usage: Implementor.jar  <Interface name> or Implementor.jar -jar <Interface name> <Output file>");
            return;
        }
        boolean jar = false;
        String className;
        String outputFile = "";
        if ("-jar".equals(args[0])) {
            jar = true;
            className = args[1];
            outputFile = args[2];
        } else {
            className = args[0];
        }
        try {
            Class c = Class.forName(className);
            if (jar) {
                new Implementor().implementJar(c, Paths.get(outputFile));
            } else {
                new Implementor().implement(c, null);
            }
        } catch (ClassNotFoundException e) {
            throw new ImplerException(e.getMessage(), e);
        }
    }

    /**
     * Implements given interface as .class file
     *
     * @param token the interface to implement
     * @param root  the location to place output file
     * @throws ImplerException if a problem occurs during implementation
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        if (token == null) {
            throw new ImplerException("Token is null");
        }
        if (root == null) {
            root = Paths.get(".");
        }
        if (!token.isInterface()) {
            throw new ImplerException("Token is not an interface");
        }

        StringBuilder builder = new StringBuilder();
        Package pack = token.getPackage();

        Path endPoint = root.resolve(pack != null ? pack.getName().replace(".", File.separator) : "");

        try {
            Files.createDirectories(endPoint);
        } catch (IOException ex) {
            throw new ImplerException(String.format("Error while creating subdirectories: %s", ex.getMessage()), ex);
        }

        if (pack != null) {
            builder.append(String.format("package %s;%n%n", pack.getName()));
        }
        final String className = token.getSimpleName() + "Impl";

        builder.append(String.format("public class %s implements %s {%n", className, token.getCanonicalName()));
        implementInterface(builder, token);
        Path endFile = endPoint.resolve(className + ".java");
        try (PrintWriter p = new PrintWriter(Files.createFile(endFile).toFile())) {
            p.print(builder.toString());
        } catch (IOException ex) {
            throw new ImplerException(String.format("Error while writing into output file %s", ex.getMessage()), ex);
        }

    }

    /**
     * congregates interface implementation as a text
     *
     * @param builder a {@link StringBuilder} to store text
     * @param token   an interface to implement
     */
    private void implementInterface(StringBuilder builder, Class<?> token) {
        Method[] methods = token.getMethods();
        for (Method m : methods) {
            int modifiers = m.getModifiers();
            String paramString = generateSignature(m);
            if (Modifier.isStatic(modifiers)) {
                continue;
            }
            builder.append("    @Override\n    ");
            builder.append(Modifier.toString(modifiers & ~Modifier.ABSTRACT & ~Modifier.TRANSIENT));
            builder.append(" ");
            builder.append(m.getReturnType().getCanonicalName());
            builder.append(" ");
            builder.append(paramString);
            builder.append("{\n");
            builder.append("        return");
            if (Object.class.isAssignableFrom(m.getReturnType())) {
                builder.append(" null");
            } else if (m.getReturnType().equals(boolean.class)) {
                builder.append(" false");
            } else if (!m.getReturnType().equals(void.class)) {
                builder.append(" 0");
            }
            builder.append("; \n    } \n\n");

        }
        builder.append("}\n   ");
    }

    /**
     * Generates signature of a method (without modifiers and return type) using {@link StringBuilder}
     * <p>
     * An example: get(int arg0, java.Lang.String arg1)
     *
     * @param method method to implement
     * @return the signature generated
     */
    private String generateSignature(Method method) {
        StringBuilder ans = new StringBuilder();
        ans.append(method.getName());
        ans.append("(");
        int id = 0;
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            ans.append(p.getType().getCanonicalName());
            ans.append(" arg");
            ans.append(id++);
            if (i != parameters.length - 1) {
                ans.append(", ");
            }
        }
        ans.append(")");
        return ans.toString();
    }

    /**
     * Compiles implementation of interface, created by {@link #implement(Class, Path)} to jar file
     * using {@link JavaCompiler}, {@link JarOutputStream}
     *
     * @param token   interface to be implemented and its implementation  compiled
     * @param jarFile output file
     * @throws ImplerException if something goes wrong during generation and compilation
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        implement(token, null);
        String endFileName = token.getName().replace(".", "/") + "Impl";
        String javaFile = endFileName + ".java";
        String classFile = endFileName + ".class";
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        List<String> args = new ArrayList<>();
        args.add(javaFile);
        args.add("-cp");
        args.add(System.getProperty("java.class.path"));
        final int exitCode = compiler.run(null, null, null, args.toArray(new String[args.size()]));
        if (exitCode != 0) {
            throw new ImplerException(String.format("Compilation error, code: %d", exitCode));
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream jarOutput = new JarOutputStream(new FileOutputStream(jarFile.toFile()))) {
            jarOutput.putNextEntry(new JarEntry(classFile));
            Files.copy(Paths.get(classFile), jarOutput);
        } catch (IOException ex) {
            throw new ImplerException("Error while writing jar", ex);
        }
    }
}
