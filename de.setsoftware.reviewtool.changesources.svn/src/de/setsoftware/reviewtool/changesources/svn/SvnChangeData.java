package de.setsoftware.reviewtool.changesources.svn;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import de.setsoftware.reviewtool.model.api.IChangeData;
import de.setsoftware.reviewtool.model.api.IChangeSource;
import de.setsoftware.reviewtool.model.api.ICommit;
import de.setsoftware.reviewtool.model.api.IFileHistoryGraph;
import de.setsoftware.reviewtool.model.api.IRevisionedFile;

/**
 * Implementation of {@link IChangeData} that caches data needed for tracing in addition to the matched commits.
 */
public class SvnChangeData implements IChangeData {

    private final IChangeSource source;
    private final List<? extends ICommit> commits;
    private final Map<File, IRevisionedFile> localPathMap;
    private final IFileHistoryGraph fileHistoryGraph;

    public SvnChangeData(
            final IChangeSource source,
            final List<? extends ICommit> commits,
            final Map<File, IRevisionedFile> localPathMap,
            final IFileHistoryGraph fileHistoryGraph) {
        this.source = source;
        this.commits = commits;
        this.localPathMap = localPathMap;
        this.fileHistoryGraph = fileHistoryGraph;
    }

    @Override
    public IChangeSource getSource() {
        return this.source;
    }

    @Override
    public List<? extends ICommit> getMatchedCommits() {
        return Collections.unmodifiableList(this.commits);
    }

    @Override
    public Map<File, IRevisionedFile> getLocalPathMap() {
        return this.localPathMap;
    }

    @Override
    public IFileHistoryGraph getHistoryGraph() {
        return this.fileHistoryGraph;
    }

}
