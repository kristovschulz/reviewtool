package de.setsoftware.reviewtool.model.changestructure;

import java.util.LinkedHashSet;
import java.util.Set;

import de.setsoftware.reviewtool.model.api.IFileHistoryEdge;
import de.setsoftware.reviewtool.model.api.IRevisionedFile;

/**
 * Implements a proxy to a {@link FileHistoryNode}.
 */
final class FileHistoryNodeProxy extends ProxyableFileHistoryNode {

    private static final long serialVersionUID = 2034613078318463993L;

    private final FileHistoryGraph graph;
    private final IRevisionedFile file;
    private final Set<ProxyableFileHistoryEdge> ancestors;
    private final Set<ProxyableFileHistoryEdge> descendants;
    private final Set<IRevisionedFile> moveSourceFiles;
    private final Set<IRevisionedFile> moveTargetFiles;
    private final Type type;
    private transient FileHistoryNode target;

    /**
     * Creates a proxy to a {@link FileHistoryNode}.
     *
     * @param graph The {@link FileHistoryGraph}.
     * @param file The path and revision of the node.
     * @param ancestors The ancestor edges of the node.
     * @param descendants The descendant edges of the node.
     * @param moveSourceFiles The paths and revisions of the move source nodes.
     * @param moveTargetFiles The paths and revisions of the move target nodes.
     * @param type The type of the node.
     */
    FileHistoryNodeProxy(
            final FileHistoryGraph graph,
            final IRevisionedFile file,
            final Set<ProxyableFileHistoryEdge> ancestors,
            final Set<ProxyableFileHistoryEdge> descendants,
            final Set<IRevisionedFile> moveSourceFiles,
            final Set<IRevisionedFile> moveTargetFiles,
            final Type type) {

        this.graph = graph;
        this.file = file;
        this.ancestors = ancestors;
        this.descendants = descendants;
        this.moveSourceFiles = moveSourceFiles;
        this.moveTargetFiles = moveTargetFiles;
        this.type = type;
    }

    @Override
    public FileHistoryGraph getGraph() {
        return this.graph;
    }

    @Override
    public IRevisionedFile getFile() {
        return this.file;
    }

    @Override
    public boolean isRoot() {
        return this.getTarget().isRoot();
    }

    @Override
    public Type getType() {
        return this.type;
    }

    @Override
    public boolean isConfirmed() {
        return this.getTarget().isConfirmed();
    }

    @Override
    public boolean isCopyTarget() {
        return this.getTarget().isCopyTarget();
    }

    @Override
    public Set<? extends ProxyableFileHistoryEdge> getAncestors() {
        return this.getTarget().getAncestors();
    }

    @Override
    public Set<? extends ProxyableFileHistoryEdge> getDescendants() {
        return this.getTarget().getDescendants();
    }

    @Override
    void addAncestor(final ProxyableFileHistoryEdge ancestor) {
        this.getTarget().addAncestor(ancestor);
    }

    @Override
    void removeAncestor(final ProxyableFileHistoryEdge ancestor) {
        this.getTarget().removeAncestor(ancestor);
    }

    @Override
    void addDescendant(final ProxyableFileHistoryNode descendant, final IFileHistoryEdge.Type type) {
        this.getTarget().addDescendant(descendant, type);
    }

    @Override
    public Set<? extends ProxyableFileHistoryNode> getMoveSources() {
        return this.getTarget().getMoveSources();
    }

    @Override
    public Set<? extends ProxyableFileHistoryNode> getMoveTargets() {
        return this.getTarget().getMoveTargets();
    }

    @Override
    void addMoveSource(final ProxyableFileHistoryNode node) {
        this.getTarget().addMoveSource(node);
    }

    @Override
    void addMoveTarget(final ProxyableFileHistoryNode node) {
        this.getTarget().addMoveTarget(node);
    }

    @Override
    void makeDeleted() {
        this.getTarget().makeDeleted();
    }

    @Override
    void makeReplaced() {
        this.getTarget().makeReplaced();
    }

    @Override
    void makeConfirmed() {
        this.getTarget().makeConfirmed();
    }

    /**
     * Returns the proxy target. The first time this method is called, it is looked up in the graph.
     */
    private synchronized FileHistoryNode getTarget() {
        if (this.target == null) {
            this.target = new FileHistoryNode(
                    this.graph,
                    this.file,
                    this.ancestors,
                    this.descendants,
                    this.fromFiles(this.moveSourceFiles),
                    this.fromFiles(this.moveTargetFiles),
                    this.type);
        }

        return this.target;
    }

    private Set<ProxyableFileHistoryNode> fromFiles(final Set<IRevisionedFile> files) {
        final Set<ProxyableFileHistoryNode> nodes = new LinkedHashSet<>();
        for (final IRevisionedFile file : files) {
            final ProxyableFileHistoryNode node = this.graph.getNodeFor(file);
            assert node != null;
            nodes.add(node);
        }
        return nodes;
    }
}
