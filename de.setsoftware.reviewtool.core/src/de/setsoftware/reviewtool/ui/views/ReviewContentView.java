package de.setsoftware.reviewtool.ui.views;

import java.io.File;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

import de.setsoftware.reviewtool.base.ReviewtoolException;
import de.setsoftware.reviewtool.model.PositionTransformer;
import de.setsoftware.reviewtool.model.ReviewStateManager;
import de.setsoftware.reviewtool.model.changestructure.Fragment;
import de.setsoftware.reviewtool.model.changestructure.Stop;
import de.setsoftware.reviewtool.model.changestructure.Tour;
import de.setsoftware.reviewtool.model.changestructure.ToursInReview;
import de.setsoftware.reviewtool.model.changestructure.ToursInReview.IToursInReviewChangeListener;
import de.setsoftware.reviewtool.viewtracking.CodeViewTracker;
import de.setsoftware.reviewtool.viewtracking.ITrackerCreationListener;
import de.setsoftware.reviewtool.viewtracking.IViewStatisticsListener;
import de.setsoftware.reviewtool.viewtracking.TrackerManager;
import de.setsoftware.reviewtool.viewtracking.ViewStatDataForStop;

/**
 * A review to show the content (tours and stops) belonging to a review.
 */
public class ReviewContentView extends ViewPart implements ReviewModeListener {

    private Composite comp;
    private Composite currentContent;

    @Override
    public void createPartControl(Composite comp) {
        this.comp = comp;

        ViewDataSource.get().registerListener(this);
    }

    @Override
    public void setFocus() {
    }

    @Override
    public void notifyReview(ReviewStateManager mgr, ToursInReview tours) {
        this.disposeOldContent();
        this.currentContent = this.createReviewContent(tours);
        this.comp.layout();
    }

    private Composite createReviewContent(final ToursInReview tours) {
        final Composite panel = new Composite(this.comp, SWT.NULL);
        panel.setLayout(new FillLayout());

        final TreeViewer tv = new TreeViewer(panel);
        tv.setContentProvider(new ViewContentProvider(tours));
        tv.setLabelProvider(new TourAndStopLabelProvider());
        tv.setInput(tours);

        final Tree tree = tv.getTree();
        tree.addListener(SWT.MouseDoubleClick, new Listener() {
            @Override
            public void handleEvent(Event event) {
                final Point point = new Point(event.x, event.y);
                final TreeItem item = tree.getItem(point);
                if (item != null) {
                    if (item.getData() instanceof Stop) {
                        final Stop stop = (Stop) item.getData();
                        final Tour tour = (Tour) item.getParentItem().getData();
                        jumpTo(tours, tour, stop);
                    }
                }
            }
        });

        ViewHelper.createContextMenu(this, tv.getControl(), tv);
        ensureActiveTourExpanded(tv, tours);

        return panel;
    }

    /**
     * Jumps to the given fragment. Ensures that the corresponding tour is active.
     */
    public static void jumpTo(ToursInReview tours, Tour tour, Stop fragment) {
        CurrentStop.setCurrentStop(fragment);
        try {
            tours.ensureTourActive(tour, new RealMarkerFactory());
            openEditorFor(fragment);
        } catch (final CoreException e) {
            throw new ReviewtoolException(e);
        }
    }

    private static void ensureActiveTourExpanded(TreeViewer tv, ToursInReview tours) {
        final Tour activeTour = tours.getActiveTour();
        tv.expandToLevel(activeTour, TreeViewer.ALL_LEVELS);
    }

    private static void openEditorFor(Stop stop) throws CoreException {
        final IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();

        //when jumping to a marker, Eclipse selects all the contained text. We don't want that, so
        //  we create a copy of the fragment without size
        Stop jumpTarget;
        if (stop.isDetailedFragmentKnown()) {
            final Fragment fragment = new Fragment(
                    stop.getMostRecentFile(),
                    stop.getMostRecentFragment().getFrom(),
                    stop.getMostRecentFragment().getFrom(),
                    "");
            jumpTarget = new Stop(fragment, fragment, fragment);
        } else {
            jumpTarget = stop;
        }

        final IMarker marker = ToursInReview.createMarkerFor(new RealMarkerFactory(), jumpTarget);
        if (marker != null) {
            IDE.openEditor(page, marker);
            marker.delete();
        } else {
            final IFileStore fileStore =
                    EFS.getLocalFileSystem().getStore(stop.getMostRecentFile().toLocalPath());
            IDE.openEditorOnFileStore(page, fileStore);
        }
    }

    @Override
    public void notifyFixing(ReviewStateManager mgr) {
        this.disposeOldContent();
        this.currentContent = this.createNoReviewContent();
        this.comp.layout();
    }

    @Override
    public void notifyIdle() {
        this.disposeOldContent();
        this.currentContent = this.createNoReviewContent();
        this.comp.layout();
    }

    private Composite createNoReviewContent() {
        final Composite panel = new Composite(this.comp, SWT.NULL);
        panel.setLayout(new FillLayout());
        final Label label = new Label(panel, SWT.NULL);
        label.setText("Nicht im Review-Modus");
        ViewHelper.createContextMenuWithoutSelectionProvider(this, label);
        return panel;
    }

    private void disposeOldContent() {
        if (this.currentContent != null) {
            this.currentContent.dispose();
        }
    }

    /**
     * Provides the tree consisting of tours and stops.
     */
    private static class ViewContentProvider implements ITreeContentProvider, IToursInReviewChangeListener,
            ITrackerCreationListener, IViewStatisticsListener {

        private final ToursInReview tours;
        private TreeViewer viewer;

        public ViewContentProvider(ToursInReview tours) {
            this.tours = tours;
            this.tours.registerListener(this);
            TrackerManager.get().registerListener(this);
        }

        @Override
        public Object[] getElements(Object inputElement) {
            assert inputElement == this.tours;
            return this.getChildren(null);
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            if (parentElement == null) {
                return this.tours.getTours().toArray();
            } else if (parentElement instanceof Tour) {
                final Tour s = (Tour) parentElement;
                return s.getStops().toArray();
            } else {
                return new Object[0];
            }
        }

        @Override
        public Object getParent(Object element) {
            if (element instanceof Stop) {
                final Stop f = (Stop) element;
                for (final Tour s : this.tours.getTours()) {
                    if (s.getStops().contains(f)) {
                        return s;
                    }
                }
                return null;
            } else {
                return null;
            }
        }

        @Override
        public boolean hasChildren(Object element) {
            return !(element instanceof Stop);
        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            this.viewer = (TreeViewer) viewer;
        }

        @Override
        public void dispose() {
            this.viewer = null;
        }

        @Override
        public void toursChanged() {
            if (this.viewer != null) {
                this.viewer.refresh();
                ensureActiveTourExpanded(this.viewer, this.tours);
            }
        }

        @Override
        public void activeTourChanged(Tour oldActive, Tour newActive) {
            this.viewer.update(oldActive, null);
            this.viewer.update(newActive, null);
            ensureActiveTourExpanded(this.viewer, this.tours);
        }

        @Override
        public void trackerStarts(CodeViewTracker tracker) {
            tracker.getStatistics().addListener(this);
        }

        @Override
        public void statisticsChanged(File absolutePath) {
            for (final Stop stop : this.tours.getStopsFor(absolutePath)) {
                this.viewer.update(stop, null);
            }
        }

    }

    /**
     * Label provider for the tree with tours and stops.
     */
    private static final class TourAndStopLabelProvider extends LabelProvider {
        private static final RGB[] VIEW_COLORS = new RGB[] {
            new RGB(255, 235, 0),
            new RGB(223, 235, 0),
            new RGB(191, 235, 0),
            new RGB(159, 235, 0),
            new RGB(127, 235, 0),
            new RGB(95, 235, 0),
            new RGB(63, 235, 0),
            new RGB(32, 235, 0),
            new RGB(0, 235, 0)
        };

        @Override
        public String getText(Object element) {
            if (element instanceof Tour) {
                return ((Tour) element).getDescription();
            } else if (element instanceof Stop) {
                final Stop f = (Stop) element;
                if (f.isDetailedFragmentKnown()) {
                    return this.determineFilename(f) + ", "
                            + f.getMostRecentFragment().getFrom() + " - "
                            + f.getMostRecentFragment().getTo();
                } else {
                    return this.determineFilename(f);
                }
            } else {
                return element.toString();
            }
        }

        @Override
        public Image getImage(Object element) {
            if (element instanceof Stop) {
                final Stop f = (Stop) element;
                final ViewStatDataForStop viewRatio = this.determineViewRatio(f);
                if (viewRatio.isNotViewedAtAll()) {
                    return null;
                } else {
                    return ImageCache.getColoredRectangle(
                            VIEW_COLORS[toColorIndex(viewRatio.getMaxRatio())],
                            VIEW_COLORS[toColorIndex(viewRatio.getAverageRatio())]);
                }
            } else if (element instanceof Tour) {
                final ToursInReview tours = ViewDataSource.get().getToursInReview();
                if (tours != null && tours.getActiveTour() == element) {
                    return ImageCache.getColoredDot(new RGB(255, 0, 0));
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }

        private static int toColorIndex(double ratio) {
            return (int) (ratio * (VIEW_COLORS.length - 1));
        }

        private ViewStatDataForStop determineViewRatio(Stop f) {
            return TrackerManager.get().determineViewRatio(f);
        }

        private String determineFilename(final Stop f) {
            final IResource resource = f.getMostRecentFile().determineResource();
            if (resource != null) {
                return PositionTransformer.toPosition(resource, -1).getShortFileName();
            } else {
                return new File(f.getMostRecentFile().getPath()).getName();
            }
        }
    }

}
