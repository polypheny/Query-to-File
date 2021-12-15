package org.polypheny.qtf;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
public class StartUpController implements Initializable {

    @FXML
    private TextField hostName;
    @FXML
    private TextField port;
    @FXML
    private TextField libraryPath;
    @FXML
    private TextField libfuse;

    private static String host;
    private static String portNbr;

    public StartUpController(){
    }

    @FXML
    public void submit() {
        host = hostName.getText();
        portNbr = port.getText();

        libfuse.getScene().getWindow().hide();
        loadLibFuse();

        Parent root = null;
        FXMLLoader loader = null;
        //from https://stackoverflow.com/questions/24254000/how-to-force-anti-aliasing-in-javafx-fonts
        System.setProperty( "prism.lcdtext", "false" );
        try {
            loader = new FXMLLoader( getClass().getResource( "/fxml/sample.fxml" ) );
            root = loader.load();
        } catch ( IOException e ) {
            log.error( "Could not load fxml", e );
            System.exit( 1 );
        }
        Stage primaryStage = new Stage();
        //see https://stackoverflow.com/questions/36981599/fxml-minheight-minwidth-attributs-ignored
        primaryStage.setMinWidth( root.minWidth( -1 ) );
        primaryStage.setMinHeight( root.minHeight( -1 ) );
        primaryStage.setTitle( "Polypheny-DB Query-To-File" );
        primaryStage.setScene( new Scene( root ) );
        primaryStage.show();
        //see https://stackoverflow.com/questions/44439408/javafx-controller-detect-when-stage-is-closing
        Controller controller = loader.getController();
        primaryStage.setOnHidden( e -> controller.shutdown() );
    }

    public static String getHost() {
        return host;
    }

    public static String getPortNbr() {
        return portNbr;
    }


    public void loadLibFuse() {
        //see https://github.com/RaiMan/SikuliX1/issues/350
        // and https://stackoverflow.com/questions/2370545/how-do-i-make-a-target-library-available-to-my-java-app
        System.setProperty( "jna.library.path", libraryPath.getText() );
        System.load( libraryPath.getText() + libfuse.getText() );
    }

    @Override
    public void initialize( URL url, ResourceBundle resourceBundle ) {

    }

}
