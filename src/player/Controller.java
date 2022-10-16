package player;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

/**
 * Class to manipulate and utilize the FXML GUI.
 */
public class Controller implements Initializable {

	// The main window
	@FXML
	BorderPane window;

	// The main parent container
	@FXML
	private VBox vboxParent;

	// Media objects used to display and work with the video.
	@FXML
	private MediaView mediaView;
	private MediaPlayer mediaPlayer;
	private Media mediaFile;

	// The Primary HBox with control elements for media player.
	@FXML
	private HBox hBoxControls;
	// HBox for volume label and volume slider.
	@FXML
	private HBox hboxVolume;
	// Volume Label.
	@FXML
	private Label labelVolume;
	// Volume slider.
	@FXML
	private Slider sliderVolume;
	// Current then total time labels.
	@FXML
	private Label labelCurrentTime;
	@FXML
	private Label labelTotalTime;
	// Time slider control.
	@FXML
	private Slider sliderTime;
	// Full screen button label.
	@FXML
	private Label labelFullScreen;
	// Speed multiplier button.
	@FXML
	private Label labelSpeed;
	// Play-Pause-Restart Button.
	@FXML
	private Button buttonPPR;

	// SrollPane for media List
	@FXML
	private ListView<String> mediaList;
	// Download Button for server downloads.
	@FXML
	private Button playBtn;

	// ImageViews for the buttons and labels.
	private ImageView ivPlay;
	private ImageView ivPause;
	private ImageView ivRestart;
	private ImageView ivVolume;
	private ImageView ivFullScreen;
	private ImageView ivMute;
	private ImageView ivExit;

	// Private functional variables unrelated to FXML

	// Checks if the video is at the end.
	private boolean atEndOfVideo = false;
	// Video is not playing when GUI starts.
	private boolean isPlaying = true;
	// Checks if the video is muted or not.
	private boolean isMuted = true;
	private final String RES = "resources/";
	private final String CACHE = "cache/";
	private final String START_FILE = "resources/Welcome.mp4";

	// How often (in seconds) we poll the server
	private final int POLLING_RATE = 5;

	// SSL Connection integration
	private Client client;
	private SSLSocketFactory socketfact;

	/**
	 * Initializes all aspects of FXML GUI and manipulates their control.
	 */
	@Override
	public void initialize(URL url, ResourceBundle resourceBundle) {
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		
		File cacheDir = new File(CACHE);
		for (File file : Objects.requireNonNull(cacheDir.listFiles()))
			if (!file.isDirectory())
				file.delete();
		
		/**
		 * Media Player creation. Media wrapped in player, player wrapped in view.
		 */
		mediaFile = new Media(new File(START_FILE).toURI().toString());
		mediaPlayer = new MediaPlayer(mediaFile);
		mediaPlayer.setAutoPlay(true);
		mediaView.setMediaPlayer(mediaPlayer);
		mediaView.fitHeightProperty().bind(vboxParent.heightProperty());
		mediaView.fitWidthProperty().bind(vboxParent.widthProperty());
		mediaPlayer.play();

		// Setup initial button Images and defaults
		setImages();

		/**
		 * Initial server/client connection
		 */
		try {
			socketfact = (SSLSocketFactory) SSLSocketFactory.getDefault();
			client = new Client(socketfact);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Error creating initial client.");
		}

		// request list of available media
		// This will run the call every 30 seconds
		UpdateListService updateListService = new UpdateListService();
		updateListService.setPeriod(Duration.seconds(POLLING_RATE));
		updateListService.setMediaList(mediaList);
		updateListService.setClient(client);

		updateListService.setOnSucceeded(e -> {
			System.out.println("Setting media list...");
			mediaList.setItems(((ListView<String>) e.getSource().getValue()).getItems());
		});
		
		updateListService.setOnFailed(e -> {
			System.out.println("Update list service failed!");
		});

		/**
		 * Play button functionality
		 */
		playBtn.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent actionEvent) {

				// get clicked on list Item
				String fileName = mediaList.getSelectionModel().getSelectedItem();
				
				if(fileName != null) {
					System.out.println("Asking for " + fileName);//dp
					File file = new File(CACHE + fileName);

					// Only download if file not already in cache
					if (!file.exists()) {

						// Run the task to download the file in the background
						Runnable task = new Runnable() {
							@Override
							public void run() {
								CountDownLatch dummyLatch = new CountDownLatch(1);
								Media file = client.receiveMediaFromServer(fileName, dummyLatch);

								// When it's done running, setup the media player.
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										System.out.println("Playing.");//dp
										setupNewFile(file);
									}
								});
							}
						};

						task.run();

					} else {
						try {
							Media currMedia = new Media(file.toURI().toString());
							setupNewFile(currMedia);
						} catch (MediaException e) {
							System.out.println("Unsupported media");
						}
					}
				}
			}
		});

		/**
		 * Handle closing the window Close all items Close server connection. Clear
		 * Cache
		 */
		Stage closeStage = Main.getPrimaryStage();
		closeStage.setOnCloseRequest(e -> {
			e.consume();
			mediaPlayer.pause();

			Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to quit?", ButtonType.YES,
					ButtonType.NO);
			Optional<ButtonType> confirm = a.showAndWait();
			File directory = new File(CACHE);
			if (confirm.isPresent() && confirm.get() == ButtonType.YES) {
				// closes refresh thread
				updateListService.cancel();				
				// Release all active latches
				client.releaseAllLatches();
				// closes media player
				mediaPlayer.dispose();
				// close server connection
				client.breakupWithServer();				
				// closes window
				closeStage.close();
				for (File file : Objects.requireNonNull(directory.listFiles()))
					if (!file.isDirectory())
						file.delete();
			} else
				mediaPlayer.play();

		});

		/**
		 * Play/pause/restart Button functionality
		 */
		buttonPPR.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent actionEvent) {

				Button buttonPlay = (Button) actionEvent.getSource();
				bindCurrentTimeLabel();
				// Restart at end of video
				if (atEndOfVideo) {
					sliderTime.setValue(0);
					atEndOfVideo = false;
					isPlaying = false;
				}
				// Pause and change image to Play.
				if (isPlaying) {
					buttonPlay.setGraphic(ivPlay);
					mediaPlayer.pause();
					isPlaying = false;
				} else {
					// Play and change image to pause
					buttonPlay.setGraphic(ivPause);
					mediaPlayer.play();
					isPlaying = true;
				}
			}
		});

		// Connect volume of video to slider
		mediaPlayer.volumeProperty().bindBidirectional(sliderVolume.valueProperty());

		// Volume Slider Functionality
		sliderVolume.valueProperty().addListener(new InvalidationListener() {
			@Override
			public void invalidated(Observable observable) {
				// Set the volume of the video to the slider's value.
				mediaPlayer.setVolume(sliderVolume.getValue());
				// If the video's volume isn't 0 then it is not muted so set the
				// label to the un-muted speaker and set isMuted to false.
				if (mediaPlayer.getVolume() != 0.0) {
					labelVolume.setGraphic(ivVolume);
					isMuted = false;
				} else {
					// The video is currently muted so set it to the muted speaker
					// and set isMuted to true.
					labelVolume.setGraphic(ivMute);
					isMuted = true;
				}
			}
		});

		// When the speed label is clicked on adjust the speed of the video
		// and change the text appropriately.
		labelSpeed.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent mouseEvent) {
				if (labelSpeed.getText().equals("1X")) {
					labelSpeed.setText("2X");
					mediaPlayer.setRate(2.0);
				} else {
					labelSpeed.setText("1X");
					mediaPlayer.setRate(1.0);
				}
			}
		});

		// When the volume label is clicked check if it is already muted. If it
		// is then switch the graphic to the unmuted speaker and set the volume.
		// Note that volume for a media player only works with values between 0.0 and
		// 1.0.
		labelVolume.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent mouseEvent) {
				// If the video is muted and the volume button was clicked then it
				// should now be un-muted so set the image of the label to the un-muted
				// speaker and set the value of the slider.
				// Then set isMuted to false.
				if (isMuted) {
					labelVolume.setGraphic(ivVolume);
					sliderVolume.setValue(0.2);
					isMuted = false;
				} else {
					// The video is not muted so mute it and change the image.
					labelVolume.setGraphic(ivMute);
					sliderVolume.setValue(0);
					isMuted = true;
				}
			}
		});

		// When the user hovers over the volume label (speaker) find the slider
		// by its ID and if it is null then the slider must have been removed from the
		// scene.
		// In other words, it is null. So if it is null add it to the HBox and
		// set its value to the current volume of the media player (video).
		labelVolume.setOnMouseEntered(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent mouseEvent) {
				if (hboxVolume.lookup("#sliderVolume") == null) {
					hboxVolume.getChildren().add(sliderVolume);
					sliderVolume.setValue(mediaPlayer.getVolume());
				}
			}
		});

		hboxVolume.setOnMouseExited(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent mouseEvent) {
				hboxVolume.getChildren().remove(sliderVolume);
			}
		});

		// Bind the height of the video player to the height of the scene. The VBox
		// parent
		// is used because it is the parent container so you can get the scene property
		// from it.
		vboxParent.sceneProperty().addListener(new ChangeListener<Scene>() {
			@Override
			public void changed(ObservableValue<? extends Scene> observableValue, Scene scene, Scene newScene) {
				if (scene == null && newScene != null) {
					// Match the height of the video to the height of the scene minus the hbox
					// controls height.
					mediaView.fitHeightProperty()
							.bind(newScene.heightProperty().subtract(hBoxControls.heightProperty().add(20)));
				}
			}
		});

		// Work with the full screen label.
		labelFullScreen.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent mouseEvent) {
				Label label = (Label) mouseEvent.getSource();
				Stage stage = (Stage) label.getScene().getWindow();

				if (stage.isFullScreen()) {
					stage.setFullScreen(false);
					labelFullScreen.setGraphic(ivFullScreen);
				} else {
					stage.setFullScreen(true);
					labelFullScreen.setGraphic(ivExit);
					stage.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
						@Override
						public void handle(KeyEvent keyEvent) {
							if (keyEvent.getCode() == KeyCode.ESCAPE) {
								labelFullScreen.setGraphic(ivFullScreen);
							}
						}
					});
				}
			}
		});

		/**
		 * totalDurationProperty() - the total amount of play time if allowed to play
		 * until finished. This checks how long the the video attached to the media
		 * player is. If the media attached to the media player changes then the max of
		 * the slider will change as well.
		 */
		mediaPlayer.totalDurationProperty().addListener(new ChangeListener<Duration>() {
			@Override
			public void changed(ObservableValue<? extends Duration> observableValue, Duration oldDuration,
					Duration newDuration) {
				// Note that duration is originally in milliseconds.
				// newDuration is the time of the current video, oldDuration is the duration of
				// the previous video.
				sliderTime.setMax(newDuration.toSeconds());
				labelTotalTime.setText(getTime(newDuration));

			}
		});

		// The valueChanging property indicates if the slider is in the process of being
		// changed.
		// When true, indicates the current value of the slider is changing.
		sliderTime.valueChangingProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observableValue, Boolean wasChanging,
					Boolean isChanging) {
				bindCurrentTimeLabel();
				// Once the slider has stopped changing (the user lets go of the slider ball)
				// then set the video to this time.
				if (!isChanging) {
					// seek() seeks the player to a new time. Note that this has no effect while the
					// player's status is stopped or the duration is indefinite.
					mediaPlayer.seek(Duration.seconds(sliderTime.getValue()));
				}
			}
		});

		// valueChangingProperty() - when true, indicates the current value of the
		// slider is changing.
		// valueProperty() - the current value represented by the slider.
		// ValueProperty() is the current value represented by the slider. This value
		// must always be between min and max.
		sliderTime.valueProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observableValue, Number oldValue, Number newValue) {
				bindCurrentTimeLabel();
				// Get the current time of the video in seconds.
				double currentTime = mediaPlayer.getCurrentTime().toSeconds();
				if (Math.abs(currentTime - newValue.doubleValue()) > 0.5) {
					mediaPlayer.seek(Duration.seconds(newValue.doubleValue()));
				}
				labelsMatchEndVideo(labelCurrentTime.getText(), labelTotalTime.getText());
			}
		});

		mediaPlayer.currentTimeProperty().addListener(new ChangeListener<Duration>() {
			@Override
			public void changed(ObservableValue<? extends Duration> observableValue, Duration oldTime,
					Duration newTime) {
				bindCurrentTimeLabel();
				if (!sliderTime.isValueChanging()) {
					sliderTime.setValue(newTime.toSeconds());
				}
				labelsMatchEndVideo(labelCurrentTime.getText(), labelTotalTime.getText());
			}
		});

		// What happens at the end of the video.
		mediaPlayer.setOnEndOfMedia(new Runnable() {
			@Override
			public void run() {
				buttonPPR.setGraphic(ivRestart);
				atEndOfVideo = true;
				// Set the current time to the final time in case it doesn't get rounded up.
				// For example the video could end with 00:39 / 00:40.
				if (!labelCurrentTime.textProperty().equals(labelTotalTime.textProperty())) {
					labelCurrentTime.textProperty().unbind();
					labelCurrentTime.setText(getTime(mediaPlayer.getTotalDuration()) + " / ");
				}
			}
		});

		updateListService.start();
	}

	// Sets up a new piece of media to play on the mediaplayer
	public void setupNewFile(Media media) {

		mediaPlayer.stop();

		double currentVolume = mediaPlayer.getVolume();

		// Update mediaPlayer with new media item
		try {
			mediaFile = media;

			mediaPlayer = new MediaPlayer(mediaFile);

			mediaPlayer.setVolume(currentVolume);
			mediaView.setMediaPlayer(mediaPlayer);

			// We need to wait for the media player to be ready according to the doc
			// We use the "resetplayer" helper function to rebind time labels
			mediaPlayer.setOnReady(new Runnable() {
				@Override
				public void run() {
					resetPlayer(mediaFile);
					mediaPlayer.play();
				}
			});
		} catch (Exception e) {
			System.out.println("Failed to retrieve file.");
		}
	}

	/**
	 * Helper function to reset the player Primarily utilized when loading a new
	 * piece of media in
	 * 
	 * @param media The media to reset according to
	 */
	public void resetPlayer(Media media) {
		bindCurrentTimeLabel();
		mediaPlayer.currentTimeProperty().addListener(new ChangeListener<Duration>() {
			@Override
			public void changed(ObservableValue<? extends Duration> observableValue, Duration oldTime,
					Duration newTime) {
				bindCurrentTimeLabel();
				if (!sliderTime.isValueChanging()) {
					sliderTime.setValue(newTime.toSeconds());
				}
				labelsMatchEndVideo(labelCurrentTime.getText(), labelTotalTime.getText());
			}
		});
		sliderTime.setMax(media.getDuration().toSeconds());
		sliderTime.setValue(0);
		labelTotalTime.setText(getTime(media.getDuration()));
		// When started the button should have the pause sign because it is playing.
		buttonPPR.setGraphic(ivPause);

		// The video starts out muted so originally have the volume label be the muted
		// speaker.
		Node currGraphic = labelVolume.getGraphic();
		labelVolume.setGraphic(currGraphic.equals(ivMute) ? currGraphic : ivVolume);
		// The video is at normal speed at the beginning.
		labelSpeed.setText("1X");
	}

	/**
	 * Setup of button configuration on mediaPlayer. Sets up button Images and their
	 * initial presentation upon player launch.
	 */
	public void setImages() {
		/*
		 * BEGIN: Applying images to buttons Play button
		 */

		Image imagePlay = new Image(new File(RES + "play-btn.png").toURI().toString());
		ivPlay = new ImageView(imagePlay);
		ivPlay.setFitWidth(35);
		ivPlay.setFitHeight(35);

		// Button stop image.
		Image imageStop = new Image(new File(RES + "stop-btn.png").toURI().toString());
		ivPause = new ImageView(imageStop);
		ivPause.setFitHeight(35);
		ivPause.setFitWidth(35);

		// Restart button image.
		Image imageRestart = new Image(new File(RES + "restart-btn.png").toURI().toString());
		ivRestart = new ImageView(imageRestart);
		ivRestart.setFitWidth(35);
		ivRestart.setFitHeight(35);

		// Volume (speaker) image.
		Image imageVol = new Image(new File(RES + "volume.png").toURI().toString());
		ivVolume = new ImageView(imageVol);
		ivVolume.setFitWidth(35);
		ivVolume.setFitHeight(35);

		// Full screen image.
		Image imageFull = new Image(new File(RES + "fullscreen.png").toURI().toString());
		ivFullScreen = new ImageView(imageFull);
		ivFullScreen.setFitHeight(35);
		ivFullScreen.setFitWidth(35);

		// Muted speaker image.
		Image imageMute = new Image(new File(RES + "mute.png").toURI().toString());
		ivMute = new ImageView(imageMute);
		ivMute.setFitWidth(35);
		ivMute.setFitHeight(35);

		// Exit full screen image.
		Image imageExit = new Image(new File(RES + "exitscreen.png").toURI().toString());
		ivExit = new ImageView(imageExit);
		ivExit.setFitHeight(35);
		ivExit.setFitWidth(35);
		/*
		 * END apply images
		 */

		/*
		 * Set default images
		 */
		// When started the button should have the pause sign because it is playing.
		buttonPPR.setGraphic(ivPause);
		// The video starts out muted so originally have the volume label be the muted
		// speaker.
		labelVolume.setGraphic(ivMute);
		// The video is at normal speed at the beginning.
		labelSpeed.setText("1X");
		// The video starts out not in full screen so make the label image the get to
		// full screen one.
		labelFullScreen.setGraphic(ivFullScreen);
	}

	/**
	 * This function takes the time of the video and calculates the seconds,
	 * minutes, and hours.
	 * 
	 * @param time - The time of the video.
	 * @return Corrected seconds, minutes, and hours.
	 */
	public String getTime(Duration time) {

		int hours = (int) time.toHours();
		int minutes = (int) time.toMinutes();
		int seconds = (int) time.toSeconds();

		// Fix the issue with the timer going to 61 and above for seconds, minutes, and
		// hours.
		if (seconds > 59)
			seconds = seconds % 60;
		if (minutes > 59)
			minutes = minutes % 60;
		if (hours > 59)
			hours = hours % 60;

		// Don't show the hours unless the video has been playing for an hour or longer.
		if (hours > 0)
			return String.format("%d:%02d:%02d", hours, minutes, seconds);
		else
			return String.format("%02d:%02d", minutes, seconds);
	}

	// Check the that the text of the time labels match. If they do then we are at
	// the end of the video.
	public void labelsMatchEndVideo(String labelTime, String labelTotalTime) {
		for (int i = 0; i < labelTotalTime.length(); i++) {
			if (labelTime.charAt(i) != labelTotalTime.charAt(i)) {
				atEndOfVideo = false;
				if (isPlaying)
					buttonPPR.setGraphic(ivPause);
				else
					buttonPPR.setGraphic(ivPlay);
				break;
			} else {
				atEndOfVideo = true;
				buttonPPR.setGraphic(ivRestart);
			}
		}
	}

	/**
	 * Bind the text of the current time label to the current time of the video.
	 * This will allow the timer to update along with the video.
	 */
	public void bindCurrentTimeLabel() {
		// Bind the text of the current time label to the current time of the video.
		// This will allow the timer to update along with the video.
		labelCurrentTime.textProperty().bind(Bindings.createStringBinding(new Callable<String>() {
			@Override
			public String call() throws Exception {
				// Return the hours, minutes, and seconds of the video.
				// %d is an integer
				// Time is given in milliseconds. (For example 750.0 ms).
				return getTime(mediaPlayer.getCurrentTime()) + " / ";
			}
		}, mediaPlayer.currentTimeProperty()));
	}

	/**
	 * UpdateListService, handles the updating of the list for a set interval.
	 * Enables to run in the background so primary JavaFX thread is not interrupted.
	 * 
	 * @author Hassan Khan
	 *
	 */
	private static class UpdateListService extends ScheduledService<ListView<String>> {
		private Client client;
		private ListView<String> mediaList;

		private final ListView<String> getMediaList() {
			return this.mediaList;
		}

		private final void setMediaList(ListView<String> mediaList) {
			this.mediaList = mediaList;
		}

		public final void setClient(Client client) {
			this.client = client;
		}

		@Override
		protected Task<ListView<String>> createTask() {
			return new Task<>() {
				@Override
				protected ListView<String> call() throws Exception {
					System.out.println("Attempting to receive list...");
					System.out.println(client);
					ListView<String> mediaList = client.receiveListFromServer(getMediaList());
					return mediaList;
				} 
			};
		};
	}
}
