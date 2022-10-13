package player;

import javafx.application.Application;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
	private static Stage stage;

    @Override
    public void start(Stage primaryStage) throws Exception {
    	stage = primaryStage;
        Parent root = FXMLLoader.load(getClass().getResource("ControllerCode.fxml"));
        stage.setTitle("KP Media Player");
        stage.setScene(new Scene(root, 900, 500));        
        stage.show();     
    }


    public static void main(String[] args) {
    	System.out.println("Launching client");
        launch(args);
    }
    
    public static Stage getPrimaryStage() {
    	return stage;
    }
    
}
