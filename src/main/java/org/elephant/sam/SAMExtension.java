package org.elephant.sam;

import org.controlsfx.control.action.Action;
import org.elephant.sam.commands.SAMMainCommand;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * QuPath extension for SegmentAnything Model (SAM).
 */
public class SAMExtension implements QuPathExtension {

	/**
	 * Get the description of the extension.
	 * 
	 * @return The description of the extension.
	 */
	public String getDescription() {
		return "Run SegmentAnything Model (SAM).";
	}

	/**
	 * Get the name of the extension.
	 * 
	 * @return The name of the extension.
	 */
	public String getName() {
		return "SegmentAnything";
	}

	public void installExtension(QuPathGUI qupath) {
		qupath.installActions(ActionTools.getAnnotatedActions(new SAMCommands(qupath)));
	}

	@ActionMenu("Extensions")
	public class SAMCommands {

		@ActionMenu("SAM")
		public final Action actionSAMCommand;

		/**
		 * Constructor.
		 * 
		 * @param qupath
		 *            The QuPath GUI.
		 */
		private SAMCommands(QuPathGUI qupath) {
			SAMMainCommand samCommand = new SAMMainCommand(qupath);
			actionSAMCommand = new Action("Launch SAM dialog.", event -> samCommand.run());
		}

	}

}