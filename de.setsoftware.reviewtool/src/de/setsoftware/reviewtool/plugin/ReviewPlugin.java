package de.setsoftware.reviewtool.plugin;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.preference.IPreferenceStore;

import de.setsoftware.reviewtool.connectors.file.FilePersistence;
import de.setsoftware.reviewtool.dialogs.CorrectSyntaxDialog;
import de.setsoftware.reviewtool.dialogs.SelectTicketDialog;
import de.setsoftware.reviewtool.model.Constants;
import de.setsoftware.reviewtool.model.DummyMarker;
import de.setsoftware.reviewtool.model.IReviewPersistence;
import de.setsoftware.reviewtool.model.ITicketChooser;
import de.setsoftware.reviewtool.model.ReviewData;
import de.setsoftware.reviewtool.model.ReviewStateManager;

public class ReviewPlugin {

	public static enum Mode {
		IDLE,
		REVIEWING,
		FIXING
	}

	private static final ReviewPlugin INSTANCE = new ReviewPlugin();
	private final ReviewStateManager persistence;
	private Mode mode = Mode.IDLE;
	private final List<WeakReference<ReviewPluginModeService>> modeListeners = new ArrayList<>();

	private ReviewPlugin() {
		final IPreferenceStore pref = Activator.getDefault().getPreferenceStore();
		pref.setDefault("user", System.getProperty("user.name"));
		final String user = pref.getString("user");
		final IReviewPersistence persistence = new FilePersistence(new File("C:\\Temp\\reviewData"), user);
		//		this.persistence = new ReviewStateManager(user, new JiraPersistence(
		//				pref.getString("url"),
		//				pref.getString("reviewRemarkField"),
		//				pref.getString("reviewState"),
		//				pref.getString("user"),
		//				pref.getString("password")));
		final ITicketChooser ticketChooser = new ITicketChooser() {
			@Override
			public String choose(
					IReviewPersistence persistence, String ticketKeyDefault, boolean forReview) {
				return SelectTicketDialog.get(persistence, ticketKeyDefault, forReview);
			}
		};
		this.persistence = new ReviewStateManager(persistence, ticketChooser);
	}

	public static ReviewStateManager getPersistence() {
		return INSTANCE.persistence;
	}

	public static ReviewPlugin getInstance() {
		return INSTANCE;
	}

	public Mode getMode() {
		return this.mode;
	}

	public void startReview() throws CoreException {
		this.loadReviewData(Mode.REVIEWING);
	}

	public void startFixing() throws CoreException {
		this.loadReviewData(Mode.FIXING);
	}

	private void loadReviewData(Mode targetMode) throws CoreException {
		this.persistence.resetKey();
		final boolean ok = this.persistence.selectTicket(targetMode == Mode.REVIEWING);
		if (!ok) {
			this.setMode(Mode.IDLE);
			return;
		}
		this.clearMarkers();
		final ReviewData currentReviewData = CorrectSyntaxDialog.getCurrentReviewDataParsed(
				this.persistence, new RealMarkerFactory());
		if (currentReviewData == null) {
			this.setMode(Mode.IDLE);
			return;
		}
		this.setMode(targetMode);
	}

	private void clearMarkers() throws CoreException {
		ResourcesPlugin.getWorkspace().getRoot().deleteMarkers(
				Constants.REVIEWMARKER_ID, true, IResource.DEPTH_INFINITE);
	}

	private void setMode(Mode mode) {
		if (mode != this.mode) {
			this.mode = mode;
			final Iterator<WeakReference<ReviewPluginModeService>> iter = this.modeListeners.iterator();
			while (iter.hasNext()) {
				final ReviewPluginModeService s = iter.next().get();
				if (s != null) {
					s.notifyModeChanged();
				} else {
					iter.remove();
				}
			}
		}
	}

	public void registerModeListener(ReviewPluginModeService reviewPluginModeService) {
		this.modeListeners.add(new WeakReference<>(reviewPluginModeService));
	}

	public void endReview() throws CoreException {
		this.clearMarkers();
		this.setMode(Mode.IDLE);
	}

	public void endFixing() throws CoreException {
		this.clearMarkers();
		this.setMode(Mode.IDLE);
	}

	public boolean hasUnresolvedRemarks() {
		final ReviewData d = CorrectSyntaxDialog.getCurrentReviewDataParsed(this.persistence, DummyMarker.FACTORY);
		return d.hasUnresolvedRemarks();
	}

	public boolean hasTemporaryMarkers() {
		final ReviewData d = CorrectSyntaxDialog.getCurrentReviewDataParsed(this.persistence, DummyMarker.FACTORY);
		return d.hasTemporaryMarkers();
	}

	public void refreshMarkers() throws CoreException {
		this.clearMarkers();
		CorrectSyntaxDialog.getCurrentReviewDataParsed(this.persistence, new RealMarkerFactory());
	}

}
