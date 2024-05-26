package org.yamcs.maven;

import java.beans.Introspector;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.File;

public class ServiceGenerator {

    private static Map<String, DescriptorProto> messageTypes = new HashMap<>();
    private static Map<DescriptorProto, FileDescriptorProto> fileForMessage = new HashMap<>();
    private static Map<String, String> javaPackages = new HashMap<>();

    private static Map<ServiceDescriptorProto, String> serviceComments = new HashMap<>();
    private static Map<MethodDescriptorProto, String> methodComments = new HashMap<>();

    private static void scanComments(FileDescriptorProto file) {
        var services = file.getServiceList();

        for (var location : file.getSourceCodeInfo().getLocationList()) {
            if (location.hasLeadingComments()) {
                if (location.getPath(0) == FileDescriptorProto.SERVICE_FIELD_NUMBER) {
                    var service = services.get(location.getPath(1));
                    if (location.getPathCount() == 2) {
                        serviceComments.put(service, location.getLeadingComments());
                    } else if (location.getPathCount() == 4) {
                        if (location.getPath(2) == ServiceDescriptorProto.METHOD_FIELD_NUMBER) {
                            var method = service.getMethod(location.getPath(3));
                            methodComments.put(method, location.getLeadingComments());
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        var request = CodeGeneratorRequest.parseFrom(System.in);
        var responseb = CodeGeneratorResponse.newBuilder();

        // Index all messages by fully-qualified protobuf name
        for (var file : request.getProtoFileList()) {
            scanComments(file);

            var javaPackage = file.getOptions().getJavaPackage();
            javaPackages.put(file.getName(), javaPackage);

            for (var messageType : file.getMessageTypeList()) {
                var qname = file.getPackage() + "." + messageType.getName();
                messageTypes.put(qname, messageType);
                fileForMessage.put(messageType, file);
            }
        }

        for (var file : request.getProtoFileList()) {
            for (int i = 0; i < file.getServiceCount(); i++) {
                responseb.addFile(generateService(file, i));
                responseb.addFile(generateServiceClient(file, i));
            }
        }

        responseb.build().writeTo(System.out);
    }

    private static File.Builder generateService(FileDescriptorProto file, int serviceIndex) {
        var service = file.getService(serviceIndex);
        var javaPackage = file.getOptions().getJavaPackage();
        var javaName = "Abstract" + service.getName();

        var jsource = new SourceBuilder(javaName + "<T>");
        jsource.setAbstract(true);
        jsource.setJavadoc(serviceComments.get(service));
        jsource.setPackage(javaPackage);
        jsource.setImplements("Api<T>");
        var className = ServiceGenerator.class.getName();
        jsource.addAnnotation("@javax.annotation.processing.Generated(value = \"" + className + "\", date = \""
                + Instant.now() + "\")");
        jsource.addAnnotation("@SuppressWarnings(\"unchecked\")");
        jsource.addImport("com.google.protobuf.Message");
        jsource.addImport("com.google.protobuf.Descriptors.MethodDescriptor");
        jsource.addImport("com.google.protobuf.Descriptors.ServiceDescriptor");
        jsource.addImport("org.yamcs.api.Api");
        jsource.addImport("org.yamcs.api.Observer");

        for (var method : service.getMethodList()) {
            var javaMethodName = Introspector.decapitalize(method.getName());
            var inputType = messageTypes.get(method.getInputType().substring(1));
            var outputType = messageTypes.get(method.getOutputType().substring(1));

            var inputTypeJavaPackage = getJavaPackage(inputType);
            if (!inputTypeJavaPackage.equals(javaPackage)) {
                jsource.addImport(getJavaClassname(inputType));
            }

            var outputTypeJavaPackage = getJavaPackage(outputType);
            if (!outputTypeJavaPackage.equals(javaPackage)) {
                jsource.addImport(getJavaClassname(outputType));
            }

            var msource = jsource.addMethod(javaMethodName);
            msource.setJavadoc(methodComments.get(method));
            msource.setAbstract(true);
            if (method.getClientStreaming()) {
                msource.setReturn("Observer<" + inputType.getName() + ">");
                msource.addArg("T", "ctx");
                msource.addArg("Observer<" + outputType.getName() + ">", "observer");
            } else {
                msource.addArg("T", "ctx");
                msource.addArg(inputType.getName(), "request");
                msource.addArg("Observer<" + outputType.getName() + ">", "observer");
            }
        }

        // Implement "ServiceDescriptor getDescriptorForType();"
        var msource = jsource.addMethod("getDescriptorForType");
        msource.setReturn("ServiceDescriptor");
        msource.addAnnotation("@Override");
        msource.setFinal(true);
        msource.body().append("return ").append(getOuterClassname(file))
                .append(".getDescriptor().getServices().get(").append(serviceIndex).append(");\n");

        // Implement "Message getRequestPrototype(MethodDescriptor method);"
        msource = jsource.addMethod("getRequestPrototype");
        msource.setReturn("Message");
        msource.addAnnotation("@Override");
        msource.setFinal(true);
        msource.addArg("MethodDescriptor", "method");
        msource.body().append("if (method.getService() != getDescriptorForType()) {\n");
        msource.body().append("    throw new IllegalArgumentException(\"Method not contained by this service.\");\n");
        msource.body().append("}\n");
        msource.body().append("switch (method.getIndex()) {\n");
        for (int i = 0; i < service.getMethodCount(); i++) {
            var method = service.getMethod(i);
            var inputType = messageTypes.get(method.getInputType().substring(1));
            msource.body().append("case ").append(i).append(":\n");
            msource.body().append("    return ").append(inputType.getName()).append(".getDefaultInstance();\n");
        }
        msource.body().append("default:\n");
        msource.body().append("    throw new IllegalStateException();\n");
        msource.body().append("}\n");

        // Implement "Message getResponsePrototype(MethodDescriptor method);"
        msource = jsource.addMethod("getResponsePrototype");
        msource.setReturn("Message");
        msource.setFinal(true);
        msource.addAnnotation("@Override");
        msource.addArg("MethodDescriptor", "method");
        msource.body().append("if (method.getService() != getDescriptorForType()) {\n");
        msource.body().append("    throw new IllegalArgumentException(\"Method not contained by this service.\");\n");
        msource.body().append("}\n");
        msource.body().append("switch (method.getIndex()) {\n");
        for (int i = 0; i < service.getMethodCount(); i++) {
            var method = service.getMethod(i);
            var outputType = messageTypes.get(method.getOutputType().substring(1));
            msource.body().append("case ").append(i).append(":\n");
            msource.body().append("    return ").append(outputType.getName()).append(".getDefaultInstance();\n");
        }
        msource.body().append("default:\n");
        msource.body().append("    throw new IllegalStateException();\n");
        msource.body().append("}\n");

        // Implement "void callMethod(MethodDescriptor method, Message request,
        // Observer<Message> observer)"
        msource = jsource.addMethod("callMethod");
        msource.setFinal(true);
        msource.addAnnotation("@Override");
        msource.addArg("MethodDescriptor", "method");
        msource.addArg("T", "ctx");
        msource.addArg("Message", "request");
        msource.addArg("Observer<Message>", "future");
        msource.body().append("if (method.getService() != getDescriptorForType()) {\n");
        msource.body().append("    throw new IllegalArgumentException(\"Method not contained by this service.\");\n");
        msource.body().append("}\n");
        msource.body().append("switch (method.getIndex()) {\n");
        for (int i = 0; i < service.getMethodCount(); i++) {
            var method = service.getMethod(i);
            if (!method.getClientStreaming()) {
                var javaMethodName = Introspector.decapitalize(method.getName());
                var inputType = messageTypes.get(method.getInputType().substring(1));
                var outputType = messageTypes.get(method.getOutputType().substring(1));
                var callArgs = "ctx, (" + inputType.getName() + ") request";
                callArgs += ", (Observer<" + outputType.getName() + ">)(Object) future";
                msource.body().append("case ").append(i).append(":\n");
                msource.body().append("    ").append(javaMethodName).append("(").append(callArgs).append(");\n");
                msource.body().append("    return;\n");
            }
        }
        msource.body().append("default:\n");
        msource.body().append("    throw new IllegalStateException();\n");
        msource.body().append("}\n");

        // Implement "Observer<Message> callMethod(MethodDescriptor method,
        // Observer<Message> observer)"
        msource = jsource.addMethod("callMethod");
        msource.setFinal(true);
        msource.addAnnotation("@Override");
        msource.setReturn("Observer<Message>");
        msource.addArg("MethodDescriptor", "method");
        msource.addArg("T", "ctx");
        msource.addArg("Observer<Message>", "future");
        msource.body().append("if (method.getService() != getDescriptorForType()) {\n");
        msource.body().append("    throw new IllegalArgumentException(\"Method not contained by this service.\");\n");
        msource.body().append("}\n");
        msource.body().append("switch (method.getIndex()) {\n");
        for (int i = 0; i < service.getMethodCount(); i++) {
            var method = service.getMethod(i);
            if (method.getClientStreaming()) {
                var javaMethodName = Introspector.decapitalize(method.getName());
                var outputType = messageTypes.get(method.getOutputType().substring(1));
                var callArgs = "ctx, (Observer<" + outputType.getName() + ">)(Object) future";
                msource.body().append("case ").append(i).append(":\n");
                msource.body().append("    return (Observer<Message>)(Object) ").append(javaMethodName).append("(")
                        .append(callArgs).append(");\n");
            }
        }
        msource.body().append("default:\n");
        msource.body().append("    throw new IllegalStateException();\n");
        msource.body().append("}\n");

        var filename = javaPackage.replace('.', '/') + "/" + javaName + ".java";
        return File.newBuilder().setName(filename).setContent(jsource.toString());
    }

    private static File.Builder generateServiceClient(FileDescriptorProto file, int serviceIndex) {
        var service = file.getService(serviceIndex);
        var javaPackage = file.getOptions().getJavaPackage();
        var javaName = service.getName() + "Client";

        var jsource = new SourceBuilder(javaName);
        jsource.setJavadoc(serviceComments.get(service));
        jsource.setPackage(javaPackage);
        jsource.setExtends("Abstract" + service.getName() + "<Void>");
        jsource.addImport("org.yamcs.api.MethodHandler");
        jsource.addImport("org.yamcs.api.Observer");
        var className = ServiceGenerator.class.getName();
        jsource.addAnnotation("@javax.annotation.processing.Generated(value = \"" + className + "\", date = \""
                + Instant.now() + "\")");

        jsource.addField("MethodHandler", "handler");

        var csource = jsource.addConstructor();
        csource.addArg("MethodHandler", "handler");
        csource.body().append("this.handler = handler;");

        for (int i = 0; i < service.getMethodCount(); i++) {
            var method = service.getMethod(i);
            var javaMethodName = Introspector.decapitalize(method.getName());
            var inputType = messageTypes.get(method.getInputType().substring(1));
            var outputType = messageTypes.get(method.getOutputType().substring(1));

            var inputTypeJavaPackage = getJavaPackage(inputType);
            if (!inputTypeJavaPackage.equals(javaPackage)) {
                jsource.addImport(getJavaClassname(inputType));
            }

            var outputTypeJavaPackage = getJavaPackage(outputType);
            if (!outputTypeJavaPackage.equals(javaPackage)) {
                jsource.addImport(getJavaClassname(outputType));
            }

            var msource = jsource.addMethod(javaMethodName);
            msource.setJavadoc(methodComments.get(method));
            msource.addAnnotation("@Override");
            msource.setFinal(true);

            if (method.getClientStreaming()) {
                msource.addAnnotation("@SuppressWarnings(\"unchecked\")");
                msource.setReturn("Observer<" + inputType.getName() + ">");
                msource.addArg("Void", "ctx");
                msource.addArg("Observer<" + outputType.getName() + ">", "observer");
                msource.body()
                        .append("return (Observer<" + inputType.getName() + ">)(Object) handler.streamingCall(\n");
                msource.body().append("    getDescriptorForType().getMethods().get(").append(i).append("),\n");
                msource.body().append("    ").append(inputType.getName()).append(".getDefaultInstance(),\n");
                msource.body().append("    ").append(outputType.getName()).append(".getDefaultInstance(),\n");
                msource.body().append("    observer);");
            } else {
                msource.addArg("Void", "ctx");
                msource.addArg(inputType.getName(), "request");
                msource.addArg("Observer<" + outputType.getName() + ">", "observer");

                msource.body().append("handler.call(\n");
                msource.body().append("    getDescriptorForType().getMethods().get(").append(i).append("),\n");
                msource.body().append("    request,\n");
                msource.body().append("    ").append(outputType.getName()).append(".getDefaultInstance(),\n");
                msource.body().append("    observer);");
            }
        }

        var filename = javaPackage.replace('.', '/') + "/" + javaName + ".java";
        return File.newBuilder().setName(filename).setContent(jsource.toString());
    }

    private static String getJavaPackage(DescriptorProto messageType) {
        var file = fileForMessage.get(messageType);
        if (file.getOptions().getJavaMultipleFiles()) {
            return file.getOptions().getJavaPackage();
        } else {
            var outerClassname = getOuterClassname(file);
            return file.getOptions().getJavaPackage() + "." + outerClassname;
        }
    }

    private static String getOuterClassname(FileDescriptorProto file) {
        if (file.getOptions().hasJavaOuterClassname()) {
            return file.getOptions().getJavaOuterClassname();
        } else {
            var name = new java.io.File(file.getName()).toPath().getFileName().toString().replace(".proto", "");
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
    }

    private static String getJavaClassname(DescriptorProto messageType) {
        return getJavaPackage(messageType) + "." + messageType.getName();
    }

    public static class SourceBuilder {

        private String package_;
        private Set<String> imports = new HashSet<>();
        private List<String> annotations = new ArrayList<>();
        private boolean abstract_;
        private String javadoc;
        private String class_;
        private String extends_;
        private String implements_;
        private List<String> fieldTypes = new ArrayList<>();
        private List<String> fieldNames = new ArrayList<>();
        private List<ConstructorBuilder> constructors = new ArrayList<>();
        private List<MethodBuilder> methods = new ArrayList<>();

        public SourceBuilder(String class_) {
            this.class_ = class_;
        }

        /**
         * Sets Javadoc. But unlike Javadoc, this does not expect HTML input and so the
         * input will be surrounded with
         * &lt;pre&gt;&lt;/pre&gt; tags and escaped as necessary.
         */
        public void setJavadoc(String javadoc) {
            this.javadoc = javadoc;
        }

        public void addAnnotation(String annotation) {
            annotations.add(annotation);
        }

        public void setPackage(String package_) {
            this.package_ = package_;
        }

        public void setAbstract(boolean abstract_) {
            this.abstract_ = abstract_;
        }

        public void setExtends(String extends_) {
            this.extends_ = extends_;
        }

        public void setImplements(String implements_) {
            this.implements_ = implements_;
        }

        public void addImport(String import_) {
            imports.add(import_);
        }

        public void addField(String type, String name) {
            fieldTypes.add(type);
            fieldNames.add(name);
        }

        public ConstructorBuilder addConstructor() {
            var constructor = new ConstructorBuilder();
            constructors.add(constructor);
            return constructor;
        }

        public MethodBuilder addMethod(String name) {
            var method = new MethodBuilder(name);
            methods.add(method);
            return method;
        }

        public static class ConstructorBuilder {

            private List<String> argTypes = new ArrayList<>();
            private List<String> argNames = new ArrayList<>();
            private StringBuilder body = new StringBuilder();

            public void addArg(String type, String name) {
                argTypes.add(type);
                argNames.add(name);
            }

            public StringBuilder body() {
                return body;
            }
        }

        public static class MethodBuilder {

            private String return_ = "void";
            private String name;
            private boolean abstract_;
            private boolean final_;
            private String javadoc;
            private List<String> argTypes = new ArrayList<>();
            private List<String> argNames = new ArrayList<>();
            private List<String> annotations = new ArrayList<>();
            private StringBuilder body = new StringBuilder();

            public MethodBuilder(String name) {
                this.name = name;
            }

            public void setReturn(String return_) {
                this.return_ = return_;
            }

            public void setJavadoc(String javadoc) {
                this.javadoc = javadoc;
            }

            public void setAbstract(boolean abstract_) {
                this.abstract_ = abstract_;
            }

            public void setFinal(boolean final_) {
                this.final_ = final_;
            }

            public void addArg(String type, String name) {
                argTypes.add(type);
                argNames.add(name);
            }

            public void addAnnotation(String annotation) {
                annotations.add(annotation);
            }

            public StringBuilder body() {
                return body;
            }
        }

        @Override
        public String toString() {
            var buf = new StringBuilder();
            buf.append("package ").append(package_).append(";\n\n");

            var sortedImports = new ArrayList<>(imports);
            Collections.sort(sortedImports);
            for (var import_ : sortedImports) {
                if (!import_.equals(package_)) {
                    buf.append("import ").append(import_).append(";\n");
                }
            }
            buf.append("\n");

            if (javadoc != null) {
                buf.append("/**\n");
                buf.append(generateJavadocBody(javadoc, " * "));
                buf.append(" */\n");
            }

            for (var annotation : annotations) {
                buf.append(annotation).append("\n");
            }

            var modifiers = "public";
            if (abstract_) {
                modifiers += " abstract";
            }

            buf.append(modifiers).append(" class ").append(class_);
            if (extends_ != null) {
                buf.append(" extends ").append(extends_);
            }
            if (implements_ != null) {
                buf.append(" implements ").append(implements_);
            }
            buf.append(" {\n");

            for (int i = 0; i < fieldTypes.size(); i++) {
                buf.append("\n    private final ").append(fieldTypes.get(i)).append(" ").append(fieldNames.get(i))
                        .append(";");
            }
            if (!fieldTypes.isEmpty()) {
                buf.append("\n");
            }

            for (var constructor : constructors) {
                buf.append("\n");
                modifiers = "public";
                buf.append("    ").append(modifiers).append(" ").append(class_);
                buf.append("(");
                for (int i = 0; i < constructor.argTypes.size(); i++) {
                    if (i > 0) {
                        buf.append(", ");
                    }
                    buf.append(constructor.argTypes.get(i)).append(" ").append(constructor.argNames.get(i));
                }
                buf.append(") {\n");
                var lines = constructor.body.toString().trim().split("\n");
                for (int i = 0; i < lines.length; i++) {
                    buf.append("        ").append(lines[i]).append("\n");
                }
                buf.append("    }\n");
            }

            for (var method : methods) {
                buf.append("\n");
                if (method.javadoc != null) {
                    buf.append("    /**\n");
                    buf.append(generateJavadocBody(method.javadoc, "     * "));
                    buf.append("     */\n");
                }
                for (var annotation : method.annotations) {
                    buf.append("    ").append(annotation).append("\n");
                }
                modifiers = "public";
                if (method.abstract_) {
                    modifiers += " abstract";
                }
                if (method.final_) {
                    modifiers += " final";
                }
                if (method.abstract_) {
                    buf.append("    ").append(modifiers).append(" ").append(method.return_).append(" ")
                            .append(method.name);
                    buf.append("(");
                    for (int i = 0; i < method.argTypes.size(); i++) {
                        if (i > 0) {
                            buf.append(", ");
                        }
                        buf.append(method.argTypes.get(i)).append(" ").append(method.argNames.get(i));
                    }
                    buf.append(");\n");
                } else {
                    buf.append("    ").append(modifiers).append(" ").append(method.return_).append(" ")
                            .append(method.name);
                    buf.append("(");
                    for (int i = 0; i < method.argTypes.size(); i++) {
                        if (i > 0) {
                            buf.append(", ");
                        }
                        buf.append(method.argTypes.get(i)).append(" ").append(method.argNames.get(i));
                    }
                    buf.append(") {\n");
                    var lines = method.body.toString().trim().split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        buf.append("        ").append(lines[i]).append("\n");
                    }
                    buf.append("    }\n");
                }
            }

            return buf.append("}\n").toString();
        }

        private static String generateJavadocBody(String raw, String prefix) {
            var escaped = "<pre>\n" + raw.replace("@", "{@literal @}")
                    .replace("/*", "{@literal /}*")
                    .replace("*/", "*{@literal /}")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;") + "</pre>";
            var body = new StringBuilder();
            for (var line : escaped.split("\n")) {
                body.append(prefix).append(line).append("\n");
            }
            return body.toString();
        }
    }
}
