module KPMediaPlayerClient {
	requires javafx.controls;
	requires javafx.fxml;
	requires javafx.media;
	requires javafx.base;
	requires javafx.graphics;
	
	opens player to javafx.graphics, javafx.fxml;
}
