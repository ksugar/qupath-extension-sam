package org.elephant.sam;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.controlsfx.control.action.Action;

import com.google.gson.Gson;

import javafx.application.Platform;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.ActionTools.ActionDescription;
import qupath.lib.gui.ActionTools.ActionMenu;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent.HierarchyEventType;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.interfaces.ROI;

public class SAMExtension implements QuPathExtension {
	
	public static final String SAM_TYPE_VIT_H = "vit_h";
	public static final String SAM_TYPE_VIT_L = "vit_l";
	public static final String SAM_TYPE_VIT_B = "vit_b";

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
	public class SAMCommands implements PathObjectHierarchyListener {
		
		private final QuPathGUI qupath;
		
		private String samType = SAM_TYPE_VIT_H;
		
		private String serverURL = "http://localhost:8000/sam/";
		
		@ActionMenu("Enable SAM>ViT-H")
		@ActionDescription("Enable SegmentAnything Model (ViT-H).")
		public final Action actionEnableSAMViTH;
		
		@ActionMenu("Enable SAM>ViT-L")
		@ActionDescription("Enable SegmentAnything Model (ViT-L).")
		public final Action actionEnableSAMViTL;
		
		@ActionMenu("Enable SAM>ViT-B")
		@ActionDescription("Enable SegmentAnything Model (ViT-B).")
		public final Action actionEnableSAMViTB;
		
		@ActionMenu("Disable SAM")
		@ActionDescription("Disable SegmentAnything Model.")
		public final Action actionDisableSAM;
		
		@ActionMenu("Server URL")
		@ActionDescription("Set API server URL.")
		public final Action actionSetServerURL;
		
		private SAMCommands(QuPathGUI qupath) {
			actionEnableSAMViTH = qupath.createImageDataAction(imageData -> {
				Dialogs.showMessageDialog("SAM", "SAM (ViT-H) enabled");
				this.samType = SAM_TYPE_VIT_H;
				qupath.getImageData().getHierarchy().removeListener(this);
				qupath.getImageData().getHierarchy().addListener(this);
			});
			actionEnableSAMViTL = qupath.createImageDataAction(imageData -> {
				Dialogs.showMessageDialog("SAM", "SAM (ViT-L) enabled");
				this.samType = SAM_TYPE_VIT_L;
				qupath.getImageData().getHierarchy().removeListener(this);
				qupath.getImageData().getHierarchy().addListener(this);
			});
			actionEnableSAMViTB = qupath.createImageDataAction(imageData -> {
				Dialogs.showMessageDialog("SAM", "SAM (ViT-B) enabled");
				this.samType = SAM_TYPE_VIT_B;
				qupath.getImageData().getHierarchy().removeListener(this);
				qupath.getImageData().getHierarchy().addListener(this);
			});
			actionDisableSAM = qupath.createImageDataAction(imageData -> {
				Dialogs.showMessageDialog("SAM", "SAM disabled");
				qupath.getImageData().getHierarchy().removeListener(this);
			});
			actionSetServerURL = new Action(event -> {
				String newURL = Dialogs.showInputDialog("Server URL", "Set API server URL", serverURL);
				if (newURL != null) {
					serverURL = newURL;
				}
			});
			this.qupath = qupath;
		}
		
		private Type type = new com.google.gson.reflect.TypeToken<List<PathObject>>(){}.getType();
		
		private String base64Encode(final BufferedImage bufferedImage) {
			String strImage = null;
			try {
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageIO.write(bufferedImage, "png", baos);
				final byte[] bytes = baos.toByteArray();
				strImage = Base64.getEncoder().encodeToString(bytes);
			} catch (IOException e) {
				e.printStackTrace();
				Dialogs.showErrorMessage(getName(), e);
			}
			return strImage;
		}

		@Override
		public void hierarchyChanged(PathObjectHierarchyEvent event) {
			if (event.getEventType() == HierarchyEventType.ADDED) {
				final PathObject pathObject = event.getChangedObjects().get(0);
				final ROI roi = pathObject.getROI();
				if (roi instanceof RectangleROI) {
					final double downsample = qupath.getViewer().getDownsampleFactor();
					
					// Crop coordinate in the original scale
					int x1 = Math.max(0, (int)(roi.getBoundsX() - roi.getBoundsWidth() * 2));
					int y1 = Math.max(0, (int)(roi.getBoundsY() - roi.getBoundsHeight() * 2));
					int x2 = Math.min(qupath.getImageData().getServer().getWidth() - 1, x1 + (int) roi.getBoundsWidth() * 5);
					int y2 = Math.min(qupath.getImageData().getServer().getHeight() - 1, y1 + (int) roi.getBoundsHeight() * 5);
					
					BufferedImage bufferedImage = null;
					try {
						final ImageServer<BufferedImage> renderedServer = new RenderedImageServer
								.Builder(qupath.getViewer().getImageData())
								.store(qupath.getViewer().getImageRegionStore())
								.renderer(qupath.getViewer().getImageDisplay())
								.build();
						bufferedImage = renderedServer.readRegion(downsample, x1, y1, x2 - x1, y2 - y1);
					} catch (IOException e) {
						e.printStackTrace();
						Dialogs.showErrorMessage(getName(), e);
					}
					final SAMPrompt prompt = SAMPrompt.newBuilder(this.samType)
							.offset(x1, y1)
							.bbox(
									(int)((roi.getBoundsX() - x1) / downsample),
									(int)((roi.getBoundsY() - y1) / downsample),
									(int)((roi.getBoundsX() + roi.getBoundsWidth() - x1) / downsample),
									(int)((roi.getBoundsY() + roi.getBoundsHeight() - y1) / downsample)
							)
							.downsample(downsample)
							.b64img(base64Encode(bufferedImage))
							.build();

					final Gson gson = GsonTools.getInstance();
					final String body = gson.toJson(prompt);
					
					final HttpRequest request = HttpRequest.newBuilder()
					        .version(HttpClient.Version.HTTP_1_1)
					        .uri(URI.create(serverURL))
					        .header("accept", "application/json")
					        .header("Content-Type", "application/json; charset=utf-8")
					        .POST(HttpRequest.BodyPublishers.ofString(body))
					        .build();
					HttpClient client = HttpClient.newHttpClient();
					try {
						HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
						if (response.statusCode() == HttpURLConnection.HTTP_OK) {
							List<PathObject> samObjects = gson.fromJson(response.body(), type);
				            for (PathObject samObject : samObjects) {
				            	final PathObject scaledSamObject = PathObjects.createAnnotationObject(
				            		samObject.getROI().scale(downsample, downsample).translate(x1, y1),
				            		PathPrefs.autoSetAnnotationClassProperty().get()
				            	);
				                qupath.getImageData().getHierarchy().addObject(scaledSamObject);
				            }
				        }
						else {
							Dialogs.showErrorMessage("Http error: " + response.statusCode(), response.body());
						}
					} catch (IOException | InterruptedException e) {
						e.printStackTrace();
						Dialogs.showErrorMessage(getName(), e);
					}
					qupath.getImageData().getHierarchy().removeObject(pathObject, false);
					Platform.runLater(() -> qupath.getImageData().getHierarchy().getSelectionModel().clearSelection());
				}
			}
			
		}
	}

}