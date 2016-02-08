package de.setsoftware.reviewtool.popup.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;

import de.setsoftware.reviewtool.model.Constants;
import de.setsoftware.reviewtool.model.ReviewRemark;
import de.setsoftware.reviewtool.plugin.ReviewPlugin;

public class DeleteResolution extends WorkbenchMarkerResolution {

	public static final DeleteResolution INSTANCE = new DeleteResolution();

	private DeleteResolution() {
	}

	@Override
	public String getLabel() {
		return "Review-Anmerkung löschen";
	}

	@Override
	public void run(IMarker marker) {
		final ReviewRemark review = ReviewRemark.getFor(ReviewPlugin.getPersistence(), marker);
		try {
			review.delete();
		} catch (final CoreException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof DeleteResolution;
	}

	@Override
	public int hashCode() {
		return 762347;
	}

	@Override
	public String getDescription() {
		return null;
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public IMarker[] findOtherMarkers(IMarker[] markers) {
		final List<IMarker> ofFittingType = new ArrayList<>();
		for (final IMarker m : markers) {
			try {
				if (m.isSubtypeOf(Constants.REVIEWMARKER_ID)) {
					ofFittingType.add(m);
				}
			} catch (final CoreException e) {
				throw new RuntimeException(e);
			}
		}
		return ofFittingType.toArray(new IMarker[ofFittingType.size()]);
	}

}

