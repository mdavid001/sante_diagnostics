package santediagnostics;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SanteDiagnostics extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(
                getClass().getResource("resources/views/login.fxml")
        );
        Scene scene = new Scene(root);

        scene.getStylesheets().add(
                getClass().getResource("resources/css/styles.css").toExternalForm()
        );

        SceneManager.setStage(primaryStage);
        primaryStage.setResizable(false);
        primaryStage.setTitle("Sante Diagnostics LIMS");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}