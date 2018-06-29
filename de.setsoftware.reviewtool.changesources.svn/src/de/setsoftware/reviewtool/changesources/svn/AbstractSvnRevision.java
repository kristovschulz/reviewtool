package de.setsoftware.reviewtool.changesources.svn;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import de.setsoftware.reviewtool.base.ComparableWrapper;
import de.setsoftware.reviewtool.base.Pair;
import de.setsoftware.reviewtool.base.tree.DepthFirstTraversalTreeNodeIterator;
import de.setsoftware.reviewtool.base.tree.OrderPreservingTreeNode;
import de.setsoftware.reviewtool.base.tree.TreeLeftToRightChildOrderStrategy;
import de.setsoftware.reviewtool.base.tree.TreeNodeIterator;
import de.setsoftware.reviewtool.base.tree.TreePreOrderRootNodeStrategy;
import de.setsoftware.reviewtool.model.api.IMutableFileHistoryGraph;
import de.setsoftware.reviewtool.model.api.IRepoRevision;
import de.setsoftware.reviewtool.model.api.IRepository;
import de.setsoftware.reviewtool.model.api.IRevision;
import de.setsoftware.reviewtool.model.changestructure.ChangestructureFactory;

/**
 * Common behaviour for {@link SvnRevision} implementations.
 */
abstract class AbstractSvnRevision implements SvnRevision {

    /**
     * Processes a single SVN repository revision by translating it into a file history graph operation.
     *
     * @param revision The revision to process,
     */
    void integrateInto(final IMutableFileHistoryGraph graph) {
        this.integrateInto(graph, new ArrayList<>(this.getChangedPaths().entrySet()), false);
    }

    /**
     * Processes a single SVN repository revision by translating it into a file history graph operation.
     *
     * @param revision The revision to process,
     * @param deferredBatches Copy operations to be processed later.
     * @param skipFirstCopy If {@code true}, the first copy operation is processed "as is", else it is deferred.
     */
    private void integrateInto(
            final IMutableFileHistoryGraph graph,
            final List<Map.Entry<String, CachedLogEntryPath>> entries,
            final boolean skipFirstCopy) {

        final OrderPreservingTreeNode<String, Boolean> addedPaths = OrderPreservingTreeNode.createRoot(null);
        final OrderPreservingTreeNode<String, Boolean> addedLeafs = OrderPreservingTreeNode.createRoot(null);

        final List<List<Map.Entry<String, CachedLogEntryPath>>> deferredCopies = new ArrayList<>();
        final ListIterator<Map.Entry<String, CachedLogEntryPath>> it = entries.listIterator();
        boolean firstEntryProcessed = false;

        while (it.hasNext()) {
            final Map.Entry<String, CachedLogEntryPath> e = it.next();
            final String path = e.getKey();
            final CachedLogEntryPath pathInfo = e.getValue();

            final String copyPath = pathInfo.getCopyPath();
            if (copyPath != null) {
                if (!skipFirstCopy || firstEntryProcessed) {
                    deferredCopies.add(this.deferCopy(e, it));
                    continue;
                } else {
                    firstEntryProcessed = true;
                }
            }

            final IRevision revision = this.toRevision();
            if (pathInfo.isDeleted() || pathInfo.isReplaced()) {
                final List<String> pathKey = makePathKey(path);
                if (Optional.ofNullable(addedPaths.getNearestValue(pathKey)).orElse(false)) {
                    // this path or some of its parents has been added as a copy in this commit:
                    // retrieve node of deleted or replaced path in order to act upon its file leafs
                    final OrderPreservingTreeNode<String, Boolean> pathNode = addedLeafs.getNode(pathKey);
                    // pathNode is null if a directory containing no file leafs is deleted.
                    // In this case, however, we need not do anything wrt. the file history graph.
                    if (pathNode != null) {
                        // iterate over the previously added leafs and remove them again from the file history graph
                        final TreeNodeIterator<
                                String,
                                Boolean,
                                OrderPreservingTreeNode<String, Boolean>> itChild =
                                        new DepthFirstTraversalTreeNodeIterator<>(
                                                pathNode,
                                                new TreePreOrderRootNodeStrategy<>(),
                                                new TreeLeftToRightChildOrderStrategy<>());

                        while (itChild.hasNext()) {
                            final Pair<List<String>, OrderPreservingTreeNode<String, Boolean>> entry = itChild.next();
                            final OrderPreservingTreeNode<String, Boolean> node = entry.getSecond();
                            // check if we found a leaf
                            if (Optional.ofNullable(node.getValue()).orElse(false)) {
                                final String deletedPath = String.join("/", entry.getFirst());
                                graph.addDeletion(deletedPath.isEmpty() ? path : path + "/" + deletedPath, revision);
                            }
                        }

                        // remove all the leafs at one go
                        pathNode.getParent().removeNode(pathNode);
                    }
                } else {
                    // no parent path has been added as a copy in this commit:
                    // check ancestor revision for contents of this path and remove all leafs
                    for (final ISvnRepo.File deletedFile :
                            this.getRelevantFilePaths(pathInfo, path, pathInfo.getAncestorRevision())) {
                        graph.addDeletion(deletedFile.getName(), revision);
                    }
                }
            }

            if (pathInfo.isNew() || pathInfo.isReplaced()) {
                if (copyPath != null) {
                    final long copyRevisionNumber = pathInfo.getCopyRevision();
                    final IRepoRevision<ComparableWrapper<Long>> copyRevision =
                            ChangestructureFactory.createRepoRevision(
                                    ComparableWrapper.wrap(copyRevisionNumber),
                                    this.getRepository());

                    if (pathInfo.isDir()) {
                        final int copyPathLen = copyPath.length();

                        for (final ISvnRepo.File copySource :
                                this.getRelevantFilePaths(pathInfo, copyPath, copyRevisionNumber)) {
                            final String copySourcePath = copySource.getName();
                            final String copyTargetPath = path + copySourcePath.substring(copyPathLen);
                            graph.addCopy(
                                    copySourcePath,
                                    copyRevision,
                                    copyTargetPath,
                                    revision);
                            addedLeafs.putValue(makePathKey(copyTargetPath), true);
                        }

                        addedPaths.putValue(makePathKey(path), true);
                    } else if (pathInfo.isFile()) {
                        graph.addCopy(
                                copyPath,
                                copyRevision,
                                path,
                                revision);
                    }
                } else if (pathInfo.isFile()) {
                    graph.addAddition(path, revision);
                }
            }

            if (!pathInfo.isDeleted() && !pathInfo.isNew()  && !pathInfo.isReplaced() && pathInfo.isFile()) {
                graph.addChange(
                        path,
                        revision,
                        Collections.singleton(ChangestructureFactory.createRepoRevision(
                                ComparableWrapper.wrap(e.getValue().getAncestorRevision()),
                                this.getRepository())));
            }
        }

        for (final List<Map.Entry<String, CachedLogEntryPath>> deferredCopy : deferredCopies) {
            this.integrateInto(graph, deferredCopy, true);
        }
    }

    /**
     * Defers a copy target and all entries that refer to a subpath of the copy target.
     * @param firstEntry The first entry denoting the copy operation.
     * @param it The entry iterator.
     * @return The batch containing all deferred entries.
     */
    private List<Map.Entry<String, CachedLogEntryPath>> deferCopy(
            final Map.Entry<String, CachedLogEntryPath> firstEntry,
            final ListIterator<Map.Entry<String, CachedLogEntryPath>> it) {

        final List<Map.Entry<String, CachedLogEntryPath>> batch = new ArrayList<>();
        batch.add(firstEntry);

        while (it.hasNext()) {
            final Map.Entry<String, CachedLogEntryPath> entry = it.next();
            if (Paths.get(entry.getKey()).startsWith(firstEntry.getKey())) {
                batch.add(entry);
            } else {
                it.previous();
                return batch;
            }
        }

        return batch;
    }

    /**
     * Returns all relevant file paths for the given {@link CachedLogEntryPath} object.
     * If the given {@link CachedLogEntryPath} object describes a file, only this file is returned.
     * If the given {@link CachedLogEntryPath} object describes a directory,
     * {@link IRepository#getFiles(String, IRepoRevision)} is called on the underlying repository and its result
     * is returned.
     *
     * @param pathInfo The {@link CachedLogEntryPath} object.
     * @param revisionNumber The revision number where to perform path lookup if necessary.
     * @return A set of relevant file paths.
     */
    private Set<? extends ISvnRepo.File> getRelevantFilePaths(
            final CachedLogEntryPath pathInfo,
            final String path,
            final long revisionNumber) {

        if (pathInfo.isFile()) {
            return new LinkedHashSet<>(Arrays.asList(new ISvnRepo.File() {
                @Override
                public String getName() {
                    return path;
                }
            }));
        } else if (pathInfo.isDir()) {
            final IRepoRevision<?> revision = ChangestructureFactory.createRepoRevision(
                    ComparableWrapper.wrap(revisionNumber),
                    this.getRepository());
            return this.getRepository().getFiles(path, revision);
        } else {
            return Collections.emptySet();
        }
    }

    /**
     * Converts a path into a list of keys suitable for the path trie.
     * @param path The path.
     * @return The list of keys.
     */
    private static List<String> makePathKey(final String path) {
        if (path.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> pathComponents = Arrays.asList(path.split("/"));
        return pathComponents.subList(1, pathComponents.size());
    }
}
