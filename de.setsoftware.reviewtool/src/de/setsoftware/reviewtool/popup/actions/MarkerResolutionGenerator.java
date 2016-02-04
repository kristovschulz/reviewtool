package de.setsoftware.reviewtool.popup.actions;

import org.eclipse.core.resources.IMarker;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator2;

import de.setsoftware.reviewtool.plugin.ReviewPlugin;

public class MarkerResolutionGenerator implements IMarkerResolutionGenerator2 {

	@Override
	public boolean hasResolutions(IMarker marker) {
		return ReviewPlugin.getInstance().getMode() != ReviewPlugin.Mode.IDLE;
	}

	@Override
	public IMarkerResolution[] getResolutions(IMarker marker) {
		switch (ReviewPlugin.getInstance().getMode()) {
		case FIXING:
			return new IMarkerResolution[] {
					FixedResolution.INSTANCE,
					WontFixResolution.INSTANCE,
					QuestionResolution.INSTANCE
			};
		case REVIEWING:
			//TODO �ndern
			return new IMarkerResolution[] {
					DeleteResolution.INSTANCE,
					CommentResolution.INSTANCE
			};
		case IDLE:
		default:
			return new IMarkerResolution[0];
		}
	}

}
