package dorkbox.systemTray.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import dorkbox.util.FileUtil;

/**
 * A simple, basic CSS parser
 */
public
class CssParser {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_NODES = false;
    private static final boolean DEBUG_GETTING_ATTRIBUTE_FROM_NODES = false;

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
    class CssNode {
        public final String label;
        public final List<CssAttribute> attributes;

        CssNode(final String label, final List<CssAttribute> attributes) {
            this.label = trim(label);
            this.attributes = attributes;
        }

        @Override
        public
        String toString() {
            return label + "\n\t" + Arrays.toString(attributes.toArray());
        }
    }


    public static
    class CssAttribute {
        public final String key;
        public final String value;

        CssAttribute(final String key, final String value) {
            this.key = trim(key);
            this.value = trim(value);
        }

        @Override
        public
        String toString() {
            return key + ':' + value;
        }

        @Override
        public
        boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            final CssAttribute attribute = (CssAttribute) o;

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
     * @param css the css text, in it's raw form
     * @param nodes the section nodes we are interested in (ie: .menuitem, *)
     * @param states the section state we are interested in (ie: focus, hover, active). Null (or empty list) means no state.
     */
    public static
    List<CssNode> getSections(String css, String[] nodes, String[] states) {
        if (states == null) {
            states = new String[0];
        }

        // collect a list of all of the sections that have what we are interested in
        List<CssNode> sections = new ArrayList<CssNode>();

        // now check the css nodes to see if they contain a combination of what we are looking for.
        for (String node : nodes) {
            int i = 0;
            while (i != -1) {
                i = css.indexOf(node, i);
                if (i > -1) {
                    int endOfNodeLabels = css.indexOf("{", i);
                    int endOfSection = css.indexOf("}", endOfNodeLabels + 1) + 1;
                    int endOfSectionTest = css.indexOf("}", i) + 1;

                    // this makes sure that weird parsing errors don't happen as a result of node keywords appearing in node sections
                    if (endOfSection != endOfSectionTest) {
                        // advance the index
                        i = endOfSection;
                        continue;
                    }

                    String nodeLabel = css.substring(i, endOfNodeLabels);

                    List<CssAttribute> attributes = new ArrayList<CssAttribute>();

                    // split the section into an arrayList, one per item. Split by attribute element
                    String nodeSection = css.substring(endOfNodeLabels, endOfSection);
                    int start = nodeSection.indexOf('{') + 1;
                    while (start != -1) {
                        int end = nodeSection.indexOf(';', start);
                        if (end != -1) {
                            int separator = nodeSection.indexOf(':', start);

                            if (separator < end) {
                                String key = nodeSection.substring(start, separator);
                                String value = nodeSection.substring(separator + 1, end);
                                attributes.add(new CssAttribute(key, value));
                            }
                            start = end + 1;
                        }
                        else {
                            break;
                        }
                    }

                    // if the label contains ',' this means that MORE that one CssNode has the same attributes. We want to split that up.
                    int multiIndex = nodeLabel.indexOf(',');
                    if (multiIndex != -1) {
                        multiIndex = 0;
                        while (multiIndex != -1) {
                            int multiEndIndex = nodeLabel.indexOf(',', multiIndex);
                            if (multiEndIndex != -1) {
                                String newLabel = nodeLabel.substring(multiIndex, multiEndIndex);

                                sections.add(new CssNode(newLabel, attributes));
                                multiIndex = multiEndIndex+1;
                            } else {
                                // now add the last part of the label.
                                String newLabel = nodeLabel.substring(multiIndex);
                                sections.add(new CssNode(newLabel, attributes));
                                multiIndex = -1;
                            }
                        }

                    } else {
                        // we are the only one with these attributes
                        sections.add(new CssNode(nodeLabel, attributes));
                    }

                    // advance the index
                    i = endOfSection;
                }
            }
        }

        // our sections can ONLY contain what we are looking for, as a word.
        for (Iterator<CssNode> iterator = sections.iterator(); iterator.hasNext(); ) {
            final CssNode section = iterator.next();
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
                        String stateValue = label.substring(stateIndex+1);
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

            if (!canSave) {
                iterator.remove();
            }
        }


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
                        for (CssAttribute attribute : section.attributes) {
                            for (Iterator<CssAttribute> iterator2 = section2.attributes.iterator(); iterator2.hasNext(); ) {
                                final CssAttribute attribute2 = iterator2.next();

                                if (attribute.equals(attribute2)) {
                                    iterator2.remove();
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

        // final cleanup loop
        for (Iterator<CssNode> iterator = sections.iterator(); iterator.hasNext(); ) {
            final CssNode section = iterator.next();

            if (section.attributes.isEmpty()) {
                iterator.remove();
            } else {
                for (Iterator<CssAttribute> iterator1 = section.attributes.iterator(); iterator1.hasNext(); ) {
                    final CssAttribute attribute = iterator1.next();

                    if (attribute == null) {
                        iterator1.remove();
                    }
                }
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
     * @param nodes the nodes (array) of strings (in descending importance) of the section titles we are looking for
     * @param attributeName the name of the attribute we are looking for.
     * @param equalsOrContained true if we want to EXACT match, false if the attribute key can contain what we are looking for.
     *
     * @return the attribute value, if found
     */
    @SuppressWarnings("Duplicates")
    public static
    String getAttributeFromSections(final List<CssNode> sections,
                                    final String[] nodes,
                                    final String attributeName,
                                    boolean equalsOrContained) {

        // a list of sections that contains the exact attribute we are looking for
        List<CssNode> sectionsWithAttribute = new ArrayList<CssNode>();
        for (CssNode cssNode : sections) {
            for (CssAttribute attribute : cssNode.attributes) {
                if (equalsOrContained) {
                    if (attribute.key.equals(attributeName)) {
                        sectionsWithAttribute.add(cssNode);
                    }
                }
                else {
                    if (attribute.key.contains(attributeName)) {
                        sectionsWithAttribute.add(cssNode);
                    }
                }
            }
        }

        if (DEBUG_GETTING_ATTRIBUTE_FROM_NODES) {
            System.err.println("--------------");
            System.err.println("Cleaned Sections");
            System.err.println("--------------");
            for (CssNode section : sectionsWithAttribute) {
                System.err.println("--------------");
                System.err.println(section);
                System.err.println("--------------");
            }
        }

        // if we only have 1, then return that one
        if (sectionsWithAttribute.size() == 1) {
            CssNode cssNode = sectionsWithAttribute.get(0);
            for (CssAttribute attribute : cssNode.attributes) {
                if (equalsOrContained) {
                    if (attribute.key.equals(attributeName)) {
                        return attribute.value;
                    }
                }
                else {
                    if (attribute.key.contains(attributeName)) {
                        return attribute.value;
                    }
                }
            }

            return null;
        }

        // now we need to narrow down which sections have the attribute.
        // This is because a section can override another, and we want to reflect that info.
        // If a section has more than one node as it's label, then it has a higher priority, and overrides ones that only have a single label.
        // IE:  "GtkPopover .entry"  overrides   ".entry"


        int maxValue = -1;
        CssNode maxNode = null; // guaranteed to be non-null

        // not the CLEANEST way to do this, but it works by only choosing css nodes that are important
        for (CssNode cssNode : sectionsWithAttribute) {
            int count = 0;
            for (String node : nodes) {
                String label = cssNode.label;
                boolean startsWith = label.startsWith(node);
                // make sure the . version (if we don't have it specified) isn't counted twice.
                boolean contains = label.contains(node) && !label.contains('.' + node);

                if (startsWith) {
                    count++;
                }
                else if (contains) {
                    // if it has MORE than just one node label (that we care about), it's more important that one that is by itself.
                    count++;
                }
            }

            if (count > maxValue) {
                maxValue = count;
                maxNode = cssNode;
            }
        }


        // get the attribute from the highest scoring node
        //noinspection ConstantConditions
        for (CssAttribute attribute : maxNode.attributes) {
            if (equalsOrContained) {
                if (attribute.key.equals(attributeName)) {
                    return attribute.value;
                }
            }
            else {
                if (attribute.key.contains(attributeName)) {
                    return attribute.value;
                }
            }
        }

        return null;
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
}
