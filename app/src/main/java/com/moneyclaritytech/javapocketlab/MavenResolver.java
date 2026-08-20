package com.moneyclaritytech.javapocketlab;

import android.content.Context;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Small Maven Central resolver for learning projects.
 * Supports group:artifact:version, common POM properties, and normal compile/runtime transitives.
 * It intentionally ignores repositories embedded in arbitrary POMs and always uses Maven Central.
 */
public final class MavenResolver {
    private static final String CENTRAL = "https://repo1.maven.org/maven2/";
    private static final long MAX_FILE_BYTES = 60L * 1024L * 1024L;
    private final File root;

    public MavenResolver(Context context) {
        root = new File(context.getFilesDir(), "maven");
        if (!root.exists()) root.mkdirs();
    }

    public List<File> installedJars() {
        List<File> out = new ArrayList<>();
        collectJars(root, out);
        return out;
    }

    public ResolveResult resolve(String coordinate) {
        LinkedHashSet<File> jars = new LinkedHashSet<>();
        List<String> messages = new ArrayList<>();
        try {
            Gav gav = Gav.parse(coordinate);
            resolveRecursive(gav, jars, messages, new HashSet<>(), 0);
            return new ResolveResult(true, new ArrayList<>(jars), messages);
        } catch (Throwable t) {
            messages.add("Error: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            return new ResolveResult(false, new ArrayList<>(jars), messages);
        }
    }

    public boolean clearAll() {
        return deleteTree(root) && root.mkdirs();
    }

    private void resolveRecursive(Gav gav, Set<File> jars, List<String> messages,
                                  Set<String> visited, int depth) throws Exception {
        if (depth > 6) return;
        if (!visited.add(gav.toString())) return;

        String base = gav.group.replace('.', '/') + "/" + gav.artifact + "/" + gav.version + "/";
        File dir = new File(root, base);
        if (!dir.exists()) dir.mkdirs();

        File jar = new File(dir, gav.artifact + "-" + gav.version + ".jar");
        File pom = new File(dir, gav.artifact + "-" + gav.version + ".pom");

        if (!pom.exists()) download(CENTRAL + base + pom.getName(), pom);
        if (!jar.exists()) {
            try {
                download(CENTRAL + base + jar.getName(), jar);
            } catch (IOException noJar) {
                messages.add("No JAR for " + gav + " (POM-only artifact)");
            }
        }
        if (jar.isFile() && jar.length() > 0) {
            jars.add(jar);
            messages.add("Added " + gav);
        }

        PomModel model = parsePom(pom, gav);
        for (Dependency dep : model.dependencies) {
            if (dep.optional) continue;
            if ("test".equals(dep.scope) || "provided".equals(dep.scope) || "system".equals(dep.scope)) continue;
            if (!"jar".equals(dep.type) && !dep.type.isEmpty()) continue;
            String version = substitute(dep.version, model.properties);
            String group = substitute(dep.group, model.properties);
            String artifact = substitute(dep.artifact, model.properties);
            if (version.isEmpty() || version.contains("${")) {
                messages.add("Skipped unresolved version: " + group + ":" + artifact + ":" + version);
                continue;
            }
            resolveRecursive(new Gav(group, artifact, version), jars, messages, visited, depth + 1);
        }
    }

    private static PomModel parsePom(File pom, Gav current) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        try { f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); } catch (IllegalArgumentException ignored) {}
        try { f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); } catch (IllegalArgumentException ignored) {}

        Document doc = f.newDocumentBuilder().parse(pom);
        Element project = doc.getDocumentElement();
        Map<String, String> props = new HashMap<>();
        props.put("project.groupId", current.group);
        props.put("project.artifactId", current.artifact);
        props.put("project.version", current.version);
        props.put("pom.groupId", current.group);
        props.put("pom.artifactId", current.artifact);
        props.put("pom.version", current.version);

        NodeList propNodes = project.getElementsByTagName("properties");
        if (propNodes.getLength() > 0) {
            Node propsNode = propNodes.item(0);
            NodeList children = propsNode.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE) props.put(n.getNodeName(), text(n));
            }
        }

        List<Dependency> deps = new ArrayList<>();
        NodeList depNodes = project.getElementsByTagName("dependency");
        for (int i = 0; i < depNodes.getLength(); i++) {
            Node d = depNodes.item(i);
            Node parent = d.getParentNode();
            if (parent != null && parent.getParentNode() != null
                    && "dependencyManagement".equals(parent.getParentNode().getNodeName())) {
                continue;
            }
            String group = childText(d, "groupId");
            String artifact = childText(d, "artifactId");
            String version = childText(d, "version");
            String scope = childText(d, "scope");
            String type = childText(d, "type");
            boolean optional = "true".equalsIgnoreCase(childText(d, "optional"));
            if (!group.isEmpty() && !artifact.isEmpty()) {
                deps.add(new Dependency(group, artifact, version, scope, type, optional));
            }
        }
        return new PomModel(props, deps);
    }

    private static String childText(Node parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && name.equals(n.getNodeName())) return text(n);
        }
        return "";
    }

    private static String text(Node n) {
        return n == null || n.getTextContent() == null ? "" : n.getTextContent().trim();
    }

    private static String substitute(String value, Map<String, String> props) {
        String out = value == null ? "" : value;
        for (int pass = 0; pass < 8 && out.contains("${"); pass++) {
            boolean changed = false;
            for (Map.Entry<String, String> e : props.entrySet()) {
                String token = "${" + e.getKey() + "}";
                if (out.contains(token)) {
                    out = out.replace(token, e.getValue());
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return out;
    }

    private static void download(String url, File dest) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "PocketJava/0.2");
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + url);
        long len = c.getContentLengthLong();
        if (len > MAX_FILE_BYTES) throw new IOException("Dependency too large (over 60 MB)");

        File tmp = new File(dest.getAbsolutePath() + ".part");
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        long total = 0;
        try (BufferedInputStream in = new BufferedInputStream(c.getInputStream());
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                total += n;
                if (total > MAX_FILE_BYTES) throw new IOException("Dependency too large (over 60 MB)");
                out.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
        if (!tmp.renameTo(dest)) {
            throw new IOException("Could not save " + dest.getName());
        }
    }

    private static void collectJars(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectJars(f, out);
            else if (f.getName().endsWith(".jar")) out.add(f);
        }
    }

    private static boolean deleteTree(File f) {
        if (f == null || !f.exists()) return true;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        return f.delete();
    }

    private static final class PomModel {
        final Map<String, String> properties;
        final List<Dependency> dependencies;
        PomModel(Map<String, String> properties, List<Dependency> dependencies) {
            this.properties = properties;
            this.dependencies = dependencies;
        }
    }

    private static final class Dependency {
        final String group, artifact, version, scope, type;
        final boolean optional;
        Dependency(String group, String artifact, String version, String scope, String type, boolean optional) {
            this.group = group; this.artifact = artifact; this.version = version;
            this.scope = scope == null ? "" : scope; this.type = type == null ? "" : type;
            this.optional = optional;
        }
    }

    private static final class Gav {
        final String group, artifact, version;
        Gav(String group, String artifact, String version) {
            if (group == null || group.trim().isEmpty() || artifact == null || artifact.trim().isEmpty() || version == null || version.trim().isEmpty()) {
                throw new IllegalArgumentException("Use group:artifact:version");
            }
            this.group = group.trim(); this.artifact = artifact.trim(); this.version = version.trim();
        }
        static Gav parse(String coordinate) {
            String[] p = coordinate == null ? new String[0] : coordinate.trim().split(":");
            if (p.length != 3) throw new IllegalArgumentException("Use group:artifact:version");
            return new Gav(p[0], p[1], p[2]);
        }
        @Override public String toString() { return group + ":" + artifact + ":" + version; }
    }

    public static final class ResolveResult {
        public final boolean success;
        public final List<File> jars;
        public final List<String> messages;
        ResolveResult(boolean success, List<File> jars, List<String> messages) {
            this.success = success; this.jars = jars; this.messages = messages;
        }
    }
}
