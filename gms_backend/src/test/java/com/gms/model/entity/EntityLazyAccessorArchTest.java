package com.gms.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A derived accessor may not read an attribute of a lazy association without asking whether it loaded.
 *
 * <p>{@code open-in-view=false} ({@code application.properties:36}) closes the session when the
 * transaction ends, and the message converter runs after that. So an entity getter written as
 * {@code createdBy.getFullName()} reads through a proxy nobody is holding a session for, and Jackson
 * turns that into {@code LazyInitializationException: could not initialize proxy
 * [com.gms.model.entity.User#2] - no Session}, which the client sees as HTTP 500. This is not a
 * hypothetical: 16 of the 21 by-id read endpoints returned 500 on the last release for exactly this
 * reason, and every one of them was a {@code @JsonProperty} accessor doing what this test forbids.</p>
 *
 * <p>The identifier getters are allowed and must stay allowed. Hibernate answers {@code getId()} from
 * the proxy without loading it, so {@code @JsonProperty("school_id")} was never broken; flagging it
 * would teach the team to ignore the test. Only a read of a NON-identifier attribute of a lazy
 * association is a defect, and the fix is the one the guarded accessors already use:
 * {@code Hibernate.isInitialized(school)} before the dot.</p>
 *
 * <p>Three limits, stated because a guard that hides them reads as coverage:</p>
 * <ul>
 *   <li>It reads source text, not bytecode. An unguarded read reached through a helper method
 *       ({@code BaseAuditEntity}'s {@code fullNameIfLoaded(createdBy)}) or through a local variable is
 *       invisible: the accessor body never names {@code createdBy.getFullName()}. {@code C-4}'s live
 *       suite closes that, because the helper still throws at serialization time and a 500 is a 500.</li>
 *   <li>It covers {@code @JsonProperty} accessors on entities only. A bare lazy field that Jackson
 *       traverses on its own is the same defect wearing a different hat, and it is not hypothetical
 *       either: {@code Parent}'s {@code user} (Parent.java:37-39) is a {@code @OneToOne} of
 *       {@code FetchType.LAZY} with no {@code @JsonIgnore} on it, so serializing a Parent walks into
 *       User's accessors — which this scan cannot see from the Parent side.</li>
 *   <li>It does not police {@code @OneToMany} or {@code @ManyToMany} collections. A lazy collection
 *       throws the same exception on {@code size()} or a stream, and needs a different shape of check
 *       (a collection has no identifier-only escape hatch).</li>
 * </ul>
 *
 * <p>Who closes them: {@code scripts/verify-c4-owned-reads.sh} reads every by-id endpoint as its owner
 * and fails on a 500, so it catches the helper-reached and collection forms this text scan cannot;
 * Stage 4's DTO work removes the whole class of defect by taking entities out of responses, at which
 * point this test has nothing left to guard.</p>
 */
class EntityLazyAccessorArchTest {

    /** A field declaration, with the field name as group 1 — the tail of an annotation block. */
    private static final Pattern FIELD_DECLARATION = Pattern.compile(
            "^\\s*(?:private|protected|public)\\s+(?:static\\s+)?(?:final\\s+)?"
                    + "[\\w.$]+(?:\\s*<[^<>]*>)?(?:\\s*\\[\\s*\\])?\\s+(\\w+)\\s*(?:=[^;]*)?;\\s*$");

    /** The name of the method a signature line declares: the identifier that sits before the first '('. */
    private static final Pattern METHOD_NAME = Pattern.compile("\\b([A-Za-z_$][\\w$]*)\\s*\\(");

    /** A field the file's own author already treats as loadable, which the counter has to count. */
    private static final Pattern GUARDED_REFERENCE =
            Pattern.compile("Hibernate\\.isInitialized\\s*\\(\\s*(\\w+)\\s*\\)");

    @Test
    void noDerivedAccessorOnAnEntityReadsALazyAssociationWithoutCheckingItLoaded() throws Exception {
        Path sources = Path.of("src/main/java/com/gms/model/entity");
        assertThat(Files.isDirectory(sources))
                .as("run from the gms_backend module directory so the source scan has something to read")
                .isTrue();

        List<Path> entitySources;
        try (var walk = Files.walk(sources)) {
            entitySources = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        assertThat(entitySources)
                .as("no entity sources where the scan expects them")
                .hasSizeGreaterThanOrEqualTo(30);

        int lazyFields = 0;
        int parsedAccessors = 0;
        int lazyReaders = 0;
        int guardedReaders = 0;
        List<String> violations = new ArrayList<>();

        for (Path file : entitySources) {
            EntitySource entity = EntitySource.read(file);
            lazyFields += entity.lazyFields.size();
            parsedAccessors += entity.jsonPropertyMethods;
            lazyReaders += entity.lazyReaders;
            guardedReaders += entity.guardedReaders;
            violations.addAll(entity.violations);
        }

        // The two figures the C-4 closure quotes, recomputed on every build instead of transcribed from
        // a card. The floors guard the other failure mode: a pattern that stopped matching would report a
        // tidy zero offences and a zero count, turning a red test green without a line of code changing.
        System.out.printf("C-4: %d derived accessors read a lazy association, %d of them ask whether it is "
                        + "loaded first (%d lazy to-one fields and %d @JsonProperty accessors across %d "
                        + "entity sources)%n",
                lazyReaders, guardedReaders, lazyFields, parsedAccessors, entitySources.size());
        assertThat(lazyFields)
                .as("too few lazy to-one associations were resolved -- check the pattern")
                .isGreaterThanOrEqualTo(20);
        assertThat(guardedReaders)
                .as("too few guarded accessors were found -- check the pattern")
                .isGreaterThanOrEqualTo(11);
        assertThat(parsedAccessors)
                .as("no @JsonProperty accessor was parsed in any entity source, so the rule measured nothing "
                        + "-- check the pattern")
                .isGreaterThan(0);

        assertThat(violations)
                .as("a derived accessor reads a lazy association with no session left to read it from, "
                        + "which arrives at the client as HTTP 500")
                .isEmpty();
    }

    /**
     * One entity source: its lazy to-one fields, and the {@code @JsonProperty} accessors that read them.
     */
    private static final class EntitySource {

        private final String className;
        private final Set<String> lazyFields = new LinkedHashSet<>();
        private final Set<String> guardedFields = new LinkedHashSet<>();
        private int jsonPropertyMethods;
        private int lazyReaders;
        private int guardedReaders;
        private final List<String> violations = new ArrayList<>();

        private EntitySource(String className) {
            this.className = className;
        }

        private static EntitySource read(Path file) throws Exception {
            EntitySource entity = new EntitySource(file.getFileName().toString().replace(".java", ""));
            // Comments and string literals are blanked first: a javadoc that quotes
            // school.getSchoolName() is not a call, and "school_name" is not an identifier.
            List<String> lines = withoutCommentsOrLiterals(
                    Files.readString(file, StandardCharsets.UTF_8)).lines().toList();

            Matcher guarded = GUARDED_REFERENCE.matcher(String.join("\n", lines));
            while (guarded.find()) {
                entity.guardedFields.add(guarded.group(1));
            }

            for (int index = 0; index < lines.size();) {
                if (!lines.get(index).trim().startsWith("@")) {
                    index++;
                    continue;
                }
                int member = annotatedMember(lines, index);
                if (member >= lines.size()) {
                    break;
                }
                String block = String.join("\n", lines.subList(index, member + 1));
                Matcher field = FIELD_DECLARATION.matcher(lines.get(member));
                if (field.matches()) {
                    boolean lazyToOne = (block.contains("@ManyToOne") || block.contains("@OneToOne"))
                            && block.contains("FetchType.LAZY");
                    if (lazyToOne) {
                        entity.lazyFields.add(field.group(1));
                    }
                }
                index = member > index ? member + 1 : index + 1;
            }

            for (int index = 0; index < lines.size();) {
                if (!lines.get(index).trim().startsWith("@")) {
                    index++;
                    continue;
                }
                int member = annotatedMember(lines, index);
                if (member >= lines.size()) {
                    break;
                }
                String block = String.join("\n", lines.subList(index, member + 1));
                String signature = lines.get(member);
                boolean accessor = block.contains("@JsonProperty") && signature.contains("(")
                        && !FIELD_DECLARATION.matcher(signature).matches();
                if (accessor) {
                    Matcher name = METHOD_NAME.matcher(signature);
                    String method = name.find() ? name.group(1) : "?";
                    String body = bodyOf(lines, member);
                    entity.jsonPropertyMethods++;
                    List<String> reads = reads(entity, body);
                    boolean asks = body.contains("Hibernate.isInitialized(");
                    if (!reads.isEmpty()) {
                        entity.lazyReaders++;
                        if (asks) {
                            entity.guardedReaders++;
                        }
                    }
                    for (String read : strictReads(entity, body)) {
                        if (!read.endsWith(".getId()") && !asks) {
                            entity.violations.add(entity.className + "." + method + " -> " + read);
                        }
                    }
                    index = bodyEnd(lines, member) + 1;
                    continue;
                }
                index = member > index ? member + 1 : index + 1;
            }
            return entity;
        }

        /**
         * The reads this accessor performs, counted over the lazy fields plus the fields the file itself
         * passes to {@code Hibernate.isInitialized} — Designation's department is an enum, so its guard
         * is redundant, but the accessor is still one of the ones that asks before it reads.
         */
        private static List<String> reads(EntitySource entity, String body) {
            Set<String> fields = new LinkedHashSet<>(entity.guardedFields);
            fields.addAll(entity.lazyFields);
            return reads(fields, body);
        }

        /** The reads that this test's rule is about: a lazy to-one association, and nothing else. */
        private static List<String> strictReads(EntitySource entity, String body) {
            return reads(entity.lazyFields, body);
        }

        private static List<String> reads(Set<String> fields, String body) {
            List<String> found = new ArrayList<>();
            for (String field : fields) {
                Matcher read = Pattern.compile("\\b" + field + "\\s*\\.\\s*(get\\w+)\\s*\\(").matcher(body);
                while (read.find()) {
                    found.add(field + "." + read.group(1) + "()");
                }
            }
            return found;
        }

        /** The line index of the member the annotation block starting at {@code from} decorates. */
        private static int annotatedMember(List<String> lines, int from) {
            int depth = 0;
            int index = from;
            while (index < lines.size()) {
                String line = lines.get(index);
                if (depth == 0 && !line.trim().startsWith("@")) {
                    return index;
                }
                depth += parenBalance(line);
                index++;
                if (depth <= 0) {
                    depth = 0;
                    if (index < lines.size() && lines.get(index).trim().startsWith("@")) {
                        continue;
                    }
                    return index;
                }
            }
            return index;
        }

        private static int parenBalance(String line) {
            int balance = 0;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '(') {
                    balance++;
                } else if (c == ')') {
                    balance--;
                }
            }
            return balance;
        }

        private static String bodyOf(List<String> lines, int member) {
            StringBuilder body = new StringBuilder();
            for (int i = member; i <= bodyEnd(lines, member) && i < lines.size(); i++) {
                body.append(lines.get(i)).append('\n');
            }
            return body.toString();
        }

        /** The closing line of the method whose signature is at {@code member}, or that line if it has no body. */
        private static int bodyEnd(List<String> lines, int member) {
            int depth = 0;
            boolean opened = false;
            for (int i = member; i < lines.size(); i++) {
                String line = lines.get(i);
                for (int c = 0; c < line.length(); c++) {
                    if (line.charAt(c) == '{') {
                        depth++;
                        opened = true;
                    } else if (line.charAt(c) == '}') {
                        depth--;
                    }
                }
                if (opened && depth <= 0) {
                    return i;
                }
            }
            return member;
        }

        /** The same text with comments and string literals blanked out, keeping every line where it was. */
        private static String withoutCommentsOrLiterals(String text) {
            StringBuilder code = new StringBuilder(text.length());
            int index = 0;
            while (index < text.length()) {
                char current = text.charAt(index);
                char next = index + 1 < text.length() ? text.charAt(index + 1) : ' ';
                if (current == '/' && next == '/') {
                    while (index < text.length() && text.charAt(index) != '\n') {
                        code.append(' ');
                        index++;
                    }
                } else if (current == '/' && next == '*') {
                    index += 2;
                    code.append("  ");
                    while (index < text.length()
                            && !(text.charAt(index) == '*' && text.charAt(index + 1) == '/')) {
                        code.append(text.charAt(index) == '\n' ? '\n' : ' ');
                        index++;
                    }
                    index += 2;
                    code.append("  ");
                } else if (current == '"' || current == '\'') {
                    code.append(current);
                    index++;
                    while (index < text.length() && text.charAt(index) != current) {
                        if (text.charAt(index) == '\\') {
                            index++;
                        }
                        index++;
                    }
                    code.append(current);
                    index++;
                } else {
                    code.append(current);
                    index++;
                }
            }
            return code.toString();
        }
    }
}
