package com.kira.followlens.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** The delta between a stored list and a freshly fetched one. */
public final class GraphDiff {

    private final Set<String> added;
    private final Set<String> removed;

    private GraphDiff(Set<String> added, Set<String> removed) {
        this.added = Collections.unmodifiableSet(added);
        this.removed = Collections.unmodifiableSet(removed);
    }

    public static GraphDiff of(Set<String> previousIds, Set<String> currentIds) {
        Set<String> added = new HashSet<>(currentIds);
        added.removeAll(previousIds);
        Set<String> removed = new HashSet<>(previousIds);
        removed.removeAll(currentIds);
        return new GraphDiff(added, removed);
    }

    public Set<String> added() {
        return added;
    }

    public Set<String> removed() {
        return removed;
    }
}
