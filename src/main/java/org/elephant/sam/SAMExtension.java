package org.elephant.sam;

import org.controlsfx.control.action.Action;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionMenu;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * QuPath extension for SegmentAnything Model (SAM).
 */
public class SAMExtension implements QuPathExtension {
	
	public String getDescription() {
		return "Run SegmentAnything Model (SAM).";
	}

	public String getName() {
		return "SegmentAnything";
	}

	public void installExtension(QuPathGUI qupath) {
		qupath.installActions(ActionTools.getAnnotatedActions(new SAMCommands(qupath)));
	}
	
	@ActionMenu("Extensions>SAM")
	public class SAMCommands {
		
		private final QuPathGUI qupath;

		@ActionMenu("SAM prompt")
		@ActionDescription("Launch Segment Anything prompt.")
		public final Action actionSAMCommand;

		@ActionMenu("SAM auto mask")
		@ActionDescription("Launch Segment Anything auto mask generator.")
		public final Action actionSAMAutoMaskCommand;
		
		private SAMCommands(QuPathGUI qupath) {
			SAMCommand samCommand = new SAMCommand(qupath);
			actionSAMCommand = new Action(event -> samCommand.run());
			SAMAutoMaskCommand samAutoMaskCommand = new SAMAutoMaskCommand(qupath);
			actionSAMAutoMaskCommand = new Action(event -> samAutoMaskCommand.run());
			this.qupath = qupath;
		}

	}

}