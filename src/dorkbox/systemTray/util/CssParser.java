package dorkbox.systemTray.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dorkbox.util.FileUtil;
import dorkbox.util.OS;

/**
 * A simple, basic CSS parser
 */
public
class CssParser {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_NODES = false;
    private static final boolean DEBUG_GETTING_ATTRIBUTE_FROM_NODES = false;
    private static final boolean DEBUG_VERBOSE = false;

    private static
    String trim(String s) {
        s = s.replaceAll("\n", "");
        s = s.replaceAll("\t", "");
        // shrink all whitespace more than 1 space wide.
        while (s.contains("  ")) {
            s = s.replaceAll("  ", " ");
        }
        return s.trim();
    }

    public static
    class Css {
        List<Entry> colorDefinitions;
        List<CssNode> cssNodes;

        Css(final List<Entry> colorDefinitions, final List<CssNode> cssNodes) {
            this.colorDefinitions = colorDefinitions;
            this.cssNodes = cssNodes;
        }

        @Override
        public
        String toString() {
            StringBuilder def = new StringBuilder();
            for (Entry attribute : colorDefinitions) {
                def.append(attribute.key)
                   .append(" : ")
                   .append(attribute.value)
                   .append(OS.LINE_SEPARATOR);
            }


            StringBuilder nodes = new StringBuilder();
            for (CssNode node : cssNodes) {
                nodes.append(OS.LINE_SEPARATOR)
                     .append(node.toString())
                     .append(OS.LINE_SEPARATOR);
            }

            return nodes.toString() + "\n\n" + nodes.toString();
        }

        public
        String getColorDefinition(final String colorString) {
            for (Entry definition : colorDefinitions) {
                if (definition.key.equals(colorString)) {
                    return definition.value;
                }
            }

            return null;
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static
    class CssNode {
        public String label;
        public List<Entry> attributes;

        CssNode(final String label, final List<Entry> attributes) {
            this.label = trim(label);
            this.attributes = attributes;
        }

        @Override
        public
        String toString() {
            StringBuilder builder = new StringBuilder();
            for (Entry attribute : attributes) {
                builder.append("\t")
                       .append(attribute.key)
                       .append(" : ")
                       .append(attribute.value)
                       .append(OS.LINE_SEPARATOR);
            }

            return label + "\n" + builder.toString();
        }
    }


    @SuppressWarnings("WeakerAccess")
    public static
    class Entry {
        public String key;
        public String value;

        Entry(final String key, final String value) {
            this.key = trim(key);
            this.value = trim(value);
        }

        @Override
        public
        String toString() {
            return key + " : " + value;
        }

        @SuppressWarnings("SimplifiableIfStatement")
        @Override
        public
        boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final Entry attribute = (Entry) o;

            if (key != null ? !key.equals(attribute.key) : attribute.key != null) {
                return false;
            }
            return value != null ? value.equals(attribute.value) : attribute.value == null;
        }

        @Override
        public
        int hashCode() {
            int result = key != null ? key.hashCode() : 0;
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }
    }

    /**
     * Gets the sections of text, of the specified CSS nodes.
     *
     * @param css the css
     * @param nodes the section nodes we are interested in (ie: .menuitem, *)
     * @param states the section state we are interested in (ie: focus, hover, active). Null (or empty list) means no state.
     */
    public static
    List<CssNode> getSections(Css css, String[] nodes, String[] states) {
        if (states == null) {
            states = new String[0];
        }

        List<CssNode> sections = new ArrayList<CssNode>(css.cssNodes.size());

        // our sections can ONLY contain what we are looking for, as a word.
        for (final CssNode section : css.cssNodes) {
            String label = section.label;
            boolean canSave = false;

            if (!section.attributes.isEmpty()) {
                main:
                for (String node : nodes) {
                    if (label.equals(node)) {
                        // exactly what our node is
                        canSave = true;
                        break;
                    }
                    if (label.length() > node.length() && label.startsWith(node)) {
                        // a combination of our node + MAYBE some other node
                        int index = node.length();
                        label = trim(label.substring(index));

                        if (label.charAt(0) == '>') {
                            // if it's an override, we have to check what it overrides.
                            label = label.substring(1);
                        }

                        // then, this MUST be one of our other nodes (that we are looking for, otherwise remove this section)
                        for (String n : nodes) {
                            //noinspection StringEquality
                            if (n != node && label.startsWith(n)) {
                                canSave = true;
                                break main;
                            }
                        }
                    }
                }

                if (canSave) {
                    // if this section is for a state we DO NOT care about, remove it
                    int stateIndex = label.lastIndexOf(':');
                    if (stateIndex != -1) {
                        String stateValue = label.substring(stateIndex + 1);
                        boolean saveState = false;
                        for (String state : states) {
                            if (stateValue.equals(state)) {
                                // this is a state we care about
                                saveState = true;
                                break;
                            }
                        }

                        if (!saveState) {
                            canSave = false;
                        }
                    }
                }
            }

            if (canSave) {
                sections.add(section);
            }
        }


        if (DEBUG_NODES) {
            for (CssNode section : sections) {
                System.err.println("--------------");
                System.err.println(section);
                System.err.println("--------------");
            }
        }

        return sections;
    }

    /**
     * find an attribute name from the list of sections. The incoming sections will all be related to one of the nodes, we prioritize
     * them on WHO has the attribute we are looking for.
     *
     * @param sections the css sections
     * @param attributeName the name of the attribute we are looking for.
     * @param equalsOrContained true if we want to EXACT match, false if the attribute key can contain what we are looking for.
     *
     * @return the attribute value, if found
     */
    @SuppressWarnings("Duplicates")
    public static
    List<Entry> getAttributeFromSections(final List<CssNode> sections, final String attributeName, boolean equalsOrContained) {

        // a list of sections that contains the exact attribute we are looking for
        List<Entry> sectionsWithAttribute = new ArrayList<Entry>();
        for (CssNode cssNode : sections) {
            for (Entry attribute : cssNode.attributes) {
                if (equalsOrContained) {
                    if (attribute.key.equals(attributeName)) {
                        sectionsWithAttribute.add(new Entry(cssNode.label, attribute.value));
                    }
                }
                else {
                    if (attribute.key.contains(attributeName)) {
                        sectionsWithAttribute.add(new Entry(cssNode.label, attribute.value));
                    }
                }
            }
        }

        if (DEBUG_GETTING_ATTRIBUTE_FROM_NODES) {
            System.err.println("--------------");
            System.err.println("Cleaned Sections");
            System.err.println("--------------");
            for (Entry section : sectionsWithAttribute) {
                System.err.println("--------------");
                System.err.println(section);
                System.err.println("--------------");
            }
        }

        return sectionsWithAttribute;
    }

    public static
    void injectAdditionalCss(final File parent, final StringBuilder stringBuilder) {
        // not the BEST way to do this because duplicates are not merged at all.

        int start = 0;
        while (start != -1) {
            // now check if it says: @import url("gtk-main.css")
            start = stringBuilder.indexOf("@import url(", start);

            if (start != -1) {
                int end = stringBuilder.indexOf("\")", start);
                if (end != -1) {
                    String url = stringBuilder.substring(start + 13, end);
                    stringBuilder.delete(start, end + 2); // 2 is the size of ")

                    if (DEBUG) {
                        System.err.println("import url: " + url);
                    }
                    try {
                        // now inject the new file where the import command was.
                        File file = new File(parent, url);
                        StringBuilder stringBuilder2 = new StringBuilder((int) (file.length()));
                        FileUtil.read(file, stringBuilder2);

                        removeComments(stringBuilder2);

                        stringBuilder.insert(start, stringBuilder2);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @SuppressWarnings("Duplicates")
    public static
    void removeComments(final StringBuilder stringBuilder) {
        // remove block comments, /* .... */  This can span multiple lines
        int start = 0;
        while (start != -1) {
            // get the start of a comment
            start = stringBuilder.indexOf("/*", start);

            if (start != -1) {
                // get the end of a comment
                int end = stringBuilder.indexOf("*/", start);
                if (end != -1) {
                    stringBuilder.delete(start, end + 2); // 2 is the size of */

                    // sometimes when the comments are removed, there is a trailing newline. remove that too. Works for windows too
                    if (stringBuilder.charAt(start) == '\n') {
                        stringBuilder.delete(start, start + 1);
                    }
                    else {
                        start++;
                    }
                }
            }
        }

        // now remove comments that start with // (line MUST start with //)
        start = 0;
        while (start != -1) {
            // get the start of a comment
            start = stringBuilder.indexOf("//", start);

            if (start != -1) {
                // the comment is at the start of a line
                if (start == 0 || stringBuilder.charAt(start - 1) == '\n') {
                    // get the end of the comment (the end of the line)
                    int end = stringBuilder.indexOf("\n", start);
                    if (end != -1) {
                        stringBuilder.delete(start, end + 1); // 1 is the size of \n
                    }
                }

                // sometimes when the comments are removed, there is a trailing newline. remove that too. Works for windows too
                if (stringBuilder.charAt(start) == '\n') {
                    stringBuilder.delete(start, start + 1);
                }
                else if (start > 0) {
                    start++;
                }
            }
        }

        // now remove comments that start with # (line MUST start with #)
        start = 0;
        while (start != -1) {
            // get the start of a comment
            start = stringBuilder.indexOf("#", start);

            if (start != -1) {
                // the comment is at the start of a line
                if (start == 0 || stringBuilder.charAt(start - 1) == '\n') {
                    // get the end of the comment (the end of the line)
                    int end = stringBuilder.indexOf("\n", start);
                    if (end != -1) {
                        stringBuilder.delete(start, end + 1); // 1 is the size of \n
                    }
                }

                // sometimes when the comments are removed, there is a trailing newline. remove that too. Works for windows too
                if (stringBuilder.charAt(start) == '\n') {
                    stringBuilder.delete(start, start + 1);
                }
                else if (start > 0) {
                    start++;
                }
            }
        }
    }

    /**
     * @return the parsed out CSS, or NULL
     */
    public static
    Css parse(final String css) {
        if (css == null) {
            return null;
        }

        // extract the color definitions
        List<Entry> colorDefinitions = getColorDefinition(css);


        int endOfColorDefinitions = css.indexOf("{");
        // find the start of the line.
        for (int lineStart = endOfColorDefinitions; lineStart > 0; lineStart--) {
            if (css.charAt(lineStart) == '\n') {
                endOfColorDefinitions = lineStart + 1;
                break;
            }
        }


        // collect a list of all of the sections that have what we are interested in.
        List<CssNode> sections = new ArrayList<CssNode>();

        int index = endOfColorDefinitions;
        int length = css.length();

        // now create a list of the CSS nodes
        do {
            int endOfNodeLabels = css.indexOf("{", index);
            if (endOfNodeLabels == -1) {
                break;
            }

            int endOfSection = css.indexOf("}", endOfNodeLabels + 1) + 1;
            int endOfSectionTest = css.indexOf("}", index) + 1;


            // this makes sure that weird parsing errors don't happen as a result of node keywords appearing in node sections
            if (endOfSection != endOfSectionTest) {
                // advance the index
                index = endOfSection;
                continue;
            }

            // find the start of the line.
            for (int lineStart = index; lineStart > 0; lineStart--) {
                if (css.charAt(lineStart) == '\n') {
                    index = lineStart + 1;
                    break;
                }
            }

            String nodeLabel = css.substring(index, endOfNodeLabels)
                                  .trim();

            List<Entry> attributes = new ArrayList<Entry>();

            // split the section into an arrayList, one per item. Split by attribute element
            String nodeSection = css.substring(endOfNodeLabels, endOfSection);
            int sectionStart = nodeSection.indexOf('{') + 1;
            int sectionEnd = nodeSection.indexOf('}');

            while (sectionStart != -1) {
                int end = nodeSection.indexOf(';', sectionStart);
                if (end != -1) {
                    int separator = nodeSection.indexOf(':', sectionStart);

                    // String.charAt() is a bit slow because of all the extra range checking it performs
                    if (separator < end) {
                        // the parenthesis must be balanced for the value
                        short parenCount = 0;

                        int j = separator;
                        while (j < end) {
                            j++;

                            char c = nodeSection.charAt(j);
                            if (c == '(') {
                                parenCount++;
                            }
                            else if (c == ')') {
                                parenCount--;
                            }
                        }

                        j--;
                        if (parenCount > 0) {
                            do {
                                // find the extended balancing paren
                                if (nodeSection.charAt(j) == ')') {
                                    parenCount--;
                                }

                                j++;
                            } while (parenCount > 0 && j < sectionEnd);
                        }

                        end = j + 1;

                        String key = nodeSection.substring(sectionStart, separator);
                        String value = nodeSection.substring(separator + 1, end);
                        attributes.add(new Entry(key, value));
                    }
                    sectionStart = end + 1;
                }
                else {
                    break;
                }
            }

            // if the label contains ',' this means that MORE than that one CssNode has the same attributes. We want to split it up and duplicate it.
            int multiIndex = nodeLabel.indexOf(',');
            if (multiIndex != -1) {
                multiIndex = 0;
                while (multiIndex != -1) {
                    int multiEndIndex = nodeLabel.indexOf(',', multiIndex);
                    if (multiEndIndex != -1) {
                        String newLabel = nodeLabel.substring(multiIndex, multiEndIndex);

                        sections.add(new CssNode(newLabel, attributes));
                        multiIndex = multiEndIndex + 1;
                    }
                    else {
                        // now add the last part of the label.
                        String newLabel = nodeLabel.substring(multiIndex);
                        sections.add(new CssNode(newLabel, attributes));
                        multiIndex = -1;
                    }
                }

            }
            else {
                // we are the only one with these attributes
                sections.add(new CssNode(nodeLabel, attributes));
            }

            // advance the index
            index = endOfSection;
        } while (index < length);

        // now merge all nodes that have the same labels.
        for (Iterator<CssNode> iterator = sections.iterator(); iterator.hasNext(); ) {
            final CssNode section = iterator.next();

            if (section != null) {
                String label = section.label;

                for (int i = 0; i < sections.size(); i++) {
                    final CssNode section2 = sections.get(i);
                    if (section != section2 && section2 != null && label.equals(section2.label)) {
                        sections.set(i, null);

                        // now merge both lists.
                        for (Entry attribute : section.attributes) {
                            for (Iterator<Entry> iterator2 = section2.attributes.iterator(); iterator2.hasNext(); ) {
                                final Entry attribute2 = iterator2.next();

                                if (attribute.equals(attribute2)) {
                                    iterator2.remove();
                                    break;
                                }
                            }
                        }

                        // now both lists are unique.
                        section.attributes.addAll(section2.attributes);
                    }
                }
            } else {
                // clean up the (possible) null entries.
                iterator.remove();
            }
        }

        // final cleanup loop for empty CSS sections
        for (Iterator<CssNode> iterator = sections.iterator(); iterator.hasNext(); ) {
            final CssNode section = iterator.next();

            for (Iterator<Entry> iterator1 = section.attributes.iterator(); iterator1.hasNext(); ) {
                final Entry attribute = iterator1.next();

                if (attribute == null) {
                    iterator1.remove();
                }
            }

            if (section.attributes.isEmpty()) {
                iterator.remove();
            }
        }

        return new Css(colorDefinitions, sections);
    }


    /**
     * Gets the color definitions (which exists at the beginnning of the CSS/gtkrc files) as a list of key/value attributes. The values
     * are also recursively resolved.
     */
    private static
    List<Entry> getColorDefinition(final String css) {
        // have to setup the "define color" section
        String colorDefine = "@define-color";
        int start = css.indexOf(colorDefine);
        int end = css.lastIndexOf(colorDefine);
        end = css.lastIndexOf(";", end) + 1; // include the ;
        String colorDefines = css.substring(start, end);

        if (DEBUG_VERBOSE) {
            System.err.println("+++++++++++++++++++++++");
            System.err.println(colorDefines);
            System.err.println("+++++++++++++++++++++++");
        }


        // since it's a color definition, it will start a very specific way. This will recursively get the defined colors.
        String[] split = colorDefines.split(colorDefine);
        List<Entry> defines = new ArrayList<Entry>(split.length);

        for (String s : split) {
            s = s.trim();
            int endDefine = s.indexOf(" ");
            if (endDefine > -1) {
                String label = s.substring(0, endDefine);
                String value = s.substring(endDefine + 1);

                // remove the trailing ;
                int endOfValue = value.length() - 1;
                if (value.charAt(endOfValue) == ';') {
                    value = value.substring(0, endOfValue);
                }

                Entry attribute = new Entry(label, value);
                defines.add(attribute);
            }
        }


        // now to recursively figure out the color definitions
        boolean allClean = false;
        while (!allClean) {
            allClean = true;

            for (Entry d : defines) {
                String value = d.value;
                int i = value.indexOf('@');
                if (i > -1) {
                    // where is the last letter?
                    int lastLetter;
                    for (lastLetter = i+1; lastLetter < value.length(); lastLetter++) {
                        char c = value.charAt(lastLetter);
                        if (!Character.isLetter(c) && c != '_') {
                            allClean = false;
                            break;
                        }
                    }

                    String replacement = d.value.substring(i+1, lastLetter);

                    // the target value for replacement will ALWAYS be earlier in the list.
                    for (Entry d2 : defines) {
                        if (d2.key.equals(replacement)) {
                            d.value = value.replace("@" + replacement, d2.value);
                            break;
                        }
                    }
                }
            }
        }

        return defines;
    }

    /**
     * Select the most relevant CSS attribute based on the input cssNodes
     *
     * @param cssNodes the list of in-order cssNodes we care about
     * @param entries a list of key/value pairs, where the key is the CSS Node label, and the value is the attribute value
     *
     * @return the most relevant attribute or NULL
     */
    public static
    String selectMostRelevantAttribute(final String[] cssNodes, final List<Entry> entries) {
        // we care about 'cssNodes' IN ORDER, so if the first one has what we are looking for, that is what we choose.
        for (String node : cssNodes) {
            for (Entry s : entries) {
                if (s.key.equals(node)) {
                    return s.value;
                }
            }

            // check now if one of the children has it
            for (Entry s : entries) {
                if (s.key.contains(node)) {
                    return s.value;
                }
            }
        }

        return null;
    }
}
